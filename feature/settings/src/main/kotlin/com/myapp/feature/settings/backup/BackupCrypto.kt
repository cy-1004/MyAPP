package com.myapp.feature.settings.backup

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

    private fun deriveKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, ITERATIONS, KEY_BITS)
        val keyBytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(keyBytes, "AES")
    }
}
