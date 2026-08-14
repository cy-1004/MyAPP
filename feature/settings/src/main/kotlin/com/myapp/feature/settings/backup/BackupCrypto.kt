package com.myapp.feature.settings.backup

import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** 备份密码错误，或密文被损坏/截断（GCM 认证标签校验不通过）。 */
class BackupDecryptException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 备份密文的信封格式与加解密（PRD 3.13）。
 *
 * 云端只存密文，服务端拿不到明文——即使腾讯云账号泄露，备份内容仍受「备份密码」保护。
 *
 * 信封布局（加密后整体再 Base64 存进 `app_backup.payload`）：
 * ```
 * [ magic 8B ][ salt 16B ][ iv 12B ][ ciphertext + GCM tag 16B ]
 * ```
 * salt 与 iv 每次备份都重新随机生成，所以同样的数据两次备份的密文完全不同——
 * 这是必须的：GCM 在相同 key 下重用 iv 会直接泄露明文异或值。
 *
 * 用 `java.util.Base64` 而不是 `android.util.Base64`：后者在 JVM 单元测试里是空壳实现，
 * 会让加解密往返测试静默失效（minSdk 35，java.util 版本随便用）。
 */
object BackupCrypto {

    /** 信封头，用于快速识别格式并为将来换算法留版本位。 */
    private val MAGIC = "MYAPBK01".toByteArray(Charsets.US_ASCII)

    private const val SALT_BYTES = 16
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private const val KEY_BITS = 256

    /**
     * PBKDF2 迭代次数。取 OWASP 对 PBKDF2-HMAC-SHA256 的推荐值。
     * 在手机上约耗时百毫秒级，对「每天一次」的备份完全可接受，
     * 换来的是备份密码被暴力破解的成本高得多。
     */
    private const val ITERATIONS = 210_000

    private val random = SecureRandom()

    fun encrypt(plaintext: ByteArray, passphrase: String): ByteArray {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val iv = ByteArray(IV_BYTES).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.ENCRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
        }
        val ciphertext = cipher.doFinal(plaintext)
        return MAGIC + salt + iv + ciphertext
    }

    fun decrypt(envelope: ByteArray, passphrase: String): ByteArray {
        val headerSize = MAGIC.size + SALT_BYTES + IV_BYTES
        if (envelope.size <= headerSize) {
            throw BackupDecryptException("备份数据不完整（长度 ${envelope.size} 字节）")
        }
        if (!envelope.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
            throw BackupDecryptException("无法识别的备份格式")
        }
        val salt = envelope.copyOfRange(MAGIC.size, MAGIC.size + SALT_BYTES)
        val iv = envelope.copyOfRange(MAGIC.size + SALT_BYTES, headerSize)
        val ciphertext = envelope.copyOfRange(headerSize, envelope.size)
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").run {
                init(Cipher.DECRYPT_MODE, deriveKey(passphrase, salt), GCMParameterSpec(TAG_BITS, iv))
                doFinal(ciphertext)
            }
        } catch (e: javax.crypto.AEADBadTagException) {
            // GCM 校验失败：绝大多数情况是密码输错，少数是密文损坏。两者无法区分，
            // 文案以「密码不对」为主——那是用户能采取行动的那一种。
            throw BackupDecryptException("备份密码不正确，或备份数据已损坏", e)
        }
    }

    /** 密文的 SHA-256，十六进制。存进 `app_backup.checksum`，下载后校验传输完整性。 */
    fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    // ---- 分帧流式加解密（PRD 4.6 本地导出/导入）----
    //
    // 上面那对 encrypt/decrypt 是「一口气全在内存里」的，云备份用它没问题（只有结构化
    // 数据，几十 KB）。本地备份要带上笔记图片，可能是几十上百 MB，必须能流式处理。
    //
    // 两个细节决定了这里为什么不直接用 CipherOutputStream/CipherInputStream：
    // 1. `CipherInputStream` 在部分实现上会把 GCM 的 AEADBadTagException 当成流结束
    //    静默吞掉——那意味着密码错了却返回一份「只解出前半截」的备份，这是最坏的失败方式。
    // 2. GCM 解密必须先验完整个 tag 才能吐出明文，单块加密时 JCE 会把整份密文缓存在内存里，
    //    等于没有流式可言。
    //
    // 所以按 [FRAME_PLAINTEXT_BYTES] 分帧，每帧独立 GCM 封装：内存占用恒定在一帧，
    // 且每帧解密失败会立刻抛异常。帧序号与「是否末帧」进 AAD，攻击者/损坏文件既不能
    // 重排帧，也不能把文件从中间截断后伪装成完整备份（缺末帧一定报错）。
    //
    // 帧布局：`[ magic 8B ][ salt 16B ][ baseIv 12B ]` 之后重复
    // `[ isFinal 1B ][ 密文长度 int32 BE ][ 密文 + tag ]`，末帧的 isFinal = 1。

    /** 分帧格式的信封头。与 [MAGIC] 不同，拿云备份的 payload 来导入会得到明确报错而不是乱解。 */
    private val FRAMED_MAGIC = "MYAPZP01".toByteArray(Charsets.US_ASCII)

    /** 每帧明文大小。512KB：内存占用可忽略，而每帧固定 16B 的 tag 开销也可忽略。 */
    private const val FRAME_PLAINTEXT_BYTES = 512 * 1024

    /** 读到比这更大的帧长度一定是文件坏了，直接报错，避免按坏长度去分配几百 MB。 */
    private const val MAX_FRAME_CIPHERTEXT_BYTES = FRAME_PLAINTEXT_BYTES + 1024

    /**
     * 返回一个「写进去明文、落到 [sink] 是密文」的流。
     *
     * **必须 close**：末帧（含 GCM tag）是在 close 时才写出去的，不 close 的文件一定读不回来。
     */
    fun encryptingStream(sink: OutputStream, passphrase: String): OutputStream {
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val baseIv = ByteArray(IV_BYTES).also(random::nextBytes)
        sink.write(FRAMED_MAGIC)
        sink.write(salt)
        sink.write(baseIv)
        return FramedEncryptingStream(sink, deriveKey(passphrase, salt), baseIv)
    }

    /** 返回一个「读出来就是明文」的流。密码错误/文件损坏会在读到对应帧时抛 [BackupDecryptException]。 */
    fun decryptingStream(source: InputStream, passphrase: String): InputStream {
        val magic = source.readExactly(FRAMED_MAGIC.size, "备份文件不完整")
        if (!magic.contentEquals(FRAMED_MAGIC)) {
            throw BackupDecryptException("无法识别的备份文件格式，请确认选的是本 App 导出的备份文件")
        }
        val salt = source.readExactly(SALT_BYTES, "备份文件不完整")
        val baseIv = source.readExactly(IV_BYTES, "备份文件不完整")
        return FramedDecryptingStream(source, deriveKey(passphrase, salt), baseIv)
    }

    /** 封一帧。[final] 进 AAD，所以末帧标记本身也受 tag 保护，改不了。 */
    internal fun sealFrame(
        key: SecretKeySpec,
        baseIv: ByteArray,
        index: Long,
        final: Boolean,
        plaintext: ByteArray,
        length: Int,
    ): ByteArray = Cipher.getInstance("AES/GCM/NoPadding").run {
        init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, frameIv(baseIv, index)))
        updateAAD(frameAad(index, final))
        doFinal(plaintext, 0, length)
    }

    internal fun openFrame(
        key: SecretKeySpec,
        baseIv: ByteArray,
        index: Long,
        final: Boolean,
        ciphertext: ByteArray,
    ): ByteArray = try {
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, frameIv(baseIv, index)))
            updateAAD(frameAad(index, final))
            doFinal(ciphertext)
        }
    } catch (e: javax.crypto.AEADBadTagException) {
        throw BackupDecryptException("备份密码不正确，或备份文件已损坏", e)
    }

    /**
     * 帧 iv = baseIv 的低 8 字节异或帧序号。
     * baseIv 每份备份重新随机，帧序号在文件内唯一，所以同一 key 下 iv 不会重复。
     */
    private fun frameIv(baseIv: ByteArray, index: Long): ByteArray {
        val iv = baseIv.copyOf()
        for (i in 0 until 8) {
            iv[iv.size - 1 - i] = (iv[iv.size - 1 - i].toInt() xor (index ushr (8 * i)).toInt()).toByte()
        }
        return iv
    }

    private fun frameAad(index: Long, final: Boolean): ByteArray =
        ByteArray(9).also { aad ->
            for (i in 0 until 8) aad[i] = (index ushr (8 * (7 - i))).toByte()
            aad[8] = if (final) 1 else 0
        }

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(keyBytes, "AES")
    }

    private class FramedEncryptingStream(
        private val sink: OutputStream,
        private val key: SecretKeySpec,
        private val baseIv: ByteArray,
    ) : OutputStream() {

        private val buffer = ByteArray(FRAME_PLAINTEXT_BYTES)
        private var filled = 0
        private var frameIndex = 0L
        private var closed = false

        override fun write(b: Int) = write(byteArrayOf(b.toByte()), 0, 1)

        override fun write(b: ByteArray, off: Int, len: Int) {
            var offset = off
            var remaining = len
            while (remaining > 0) {
                val n = minOf(remaining, buffer.size - filled)
                System.arraycopy(b, offset, buffer, filled, n)
                filled += n
                offset += n
                remaining -= n
                if (filled == buffer.size) writeFrame(final = false)
            }
        }

        override fun flush() = sink.flush()

        override fun close() {
            if (closed) return
            closed = true
            // 末帧即使是空的也要写：解密侧靠它确认文件没被截断
            writeFrame(final = true)
            sink.flush()
            sink.close()
        }

        private fun writeFrame(final: Boolean) {
            val ciphertext = sealFrame(key, baseIv, frameIndex, final, buffer, filled)
            sink.write(if (final) 1 else 0)
            sink.writeIntBigEndian(ciphertext.size)
            sink.write(ciphertext)
            frameIndex++
            filled = 0
        }
    }

    private class FramedDecryptingStream(
        private val source: InputStream,
        private val key: SecretKeySpec,
        private val baseIv: ByteArray,
    ) : InputStream() {

        private var plain = ByteArray(0)
        private var pos = 0
        private var frameIndex = 0L
        private var finished = false

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) == -1) -1 else one[0].toInt() and 0xff
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            while (pos >= plain.size) {
                if (finished) return -1
                readFrame()
            }
            val n = minOf(len, plain.size - pos)
            System.arraycopy(plain, pos, b, off, n)
            pos += n
            return n
        }

        override fun close() = source.close()

        private fun readFrame() {
            val flag = source.read()
            if (flag == -1) {
                throw BackupDecryptException("备份文件不完整（缺少结束标记），可能是导出中途被打断")
            }
            if (flag != 0 && flag != 1) throw BackupDecryptException("备份文件已损坏")
            val length = source.readIntBigEndian()
            if (length < 0 || length > MAX_FRAME_CIPHERTEXT_BYTES) {
                throw BackupDecryptException("备份文件已损坏")
            }
            val final = flag == 1
            plain = openFrame(key, baseIv, frameIndex, final, source.readExactly(length, "备份文件不完整"))
            pos = 0
            frameIndex++
            if (final) finished = true
        }
    }
}

private fun OutputStream.writeIntBigEndian(value: Int) {
    write(byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte(),
    ))
}

private fun InputStream.readIntBigEndian(): Int {
    val bytes = readExactly(4, "备份文件不完整")
    return (bytes[0].toInt() and 0xff shl 24) or
        (bytes[1].toInt() and 0xff shl 16) or
        (bytes[2].toInt() and 0xff shl 8) or
        (bytes[3].toInt() and 0xff)
}

/** 读满 [count] 字节，读不满就是文件被截断——不能当成正常的流结束。 */
private fun InputStream.readExactly(count: Int, message: String): ByteArray {
    val bytes = ByteArray(count)
    var read = 0
    while (read < count) {
        val n = read(bytes, read, count - read)
        if (n < 0) throw BackupDecryptException("$message（还差 ${count - read} 字节）")
        read += n
    }
    return bytes
}
