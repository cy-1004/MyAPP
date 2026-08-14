package com.myapp.feature.settings.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 分帧流式加解密（PRD 4.6 本地导出/导入）。
 *
 * 这条链路的风险和云备份那条一样：错了当场看不出来，等换机那天才发现解不开。
 * 额外还多两个只有流式才有的坑，各有一条用例守着：
 * - 密码错了必须**抛异常**，不能被当成「流结束」静默返回半截数据；
 * - 文件被截断（导出中途没写完）必须报错，不能因为「前几帧都能解开」就当成完整备份。
 */
class BackupCryptoStreamTest {

    private val passphrase = "correct horse battery"

    private fun encrypt(plain: ByteArray, passphrase: String = this.passphrase): ByteArray =
        ByteArrayOutputStream().also { out ->
            BackupCrypto.encryptingStream(out, passphrase).use { it.write(plain) }
        }.toByteArray()

    private fun decrypt(cipher: ByteArray, passphrase: String = this.passphrase): ByteArray =
        BackupCrypto.decryptingStream(ByteArrayInputStream(cipher), passphrase).use { it.readBytes() }

    @Test
    fun `small payload round trips`() {
        val plain = "笔记正文 🎉 with ascii".toByteArray()
        assertArrayEquals(plain, decrypt(encrypt(plain)))
    }

    @Test
    fun `empty payload round trips`() {
        // 空明文也必须写出末帧，否则解密侧会以为文件被截断
        assertArrayEquals(ByteArray(0), decrypt(encrypt(ByteArray(0))))
    }

    @Test
    fun `payload spanning many frames round trips`() {
        // 3MB 明显跨过 512KB 的帧边界，能覆盖帧序号递增与 AAD 绑定
        val plain = Random(42).nextBytes(3 * 1024 * 1024)
        assertArrayEquals(plain, decrypt(encrypt(plain)))
    }

    @Test
    fun `wrong passphrase throws instead of returning a partial result`() {
        val cipher = encrypt(Random(1).nextBytes(1024 * 1024))
        assertThrows(BackupDecryptException::class.java) { decrypt(cipher, "wrong passphrase") }
    }

    @Test
    fun `truncated file is rejected even though earlier frames decrypt fine`() {
        // 导出写到一半被打断：前面的帧都是完好的，唯独缺末帧。
        // 若不检查末帧标记，用户会拿到一份「能打开但少了一截」的备份。
        val cipher = encrypt(Random(2).nextBytes(2 * 1024 * 1024))
        val truncated = cipher.copyOfRange(0, cipher.size / 2)
        val e = assertThrows(BackupDecryptException::class.java) { decrypt(truncated) }
        assertTrue(e.message, e.message!!.contains("不完整"))
    }

    @Test
    fun `tampered ciphertext is rejected`() {
        val cipher = encrypt("敏感数据".toByteArray())
        // 跳过 8+16+12 的头，改密文里的一个字节
        cipher[40] = (cipher[40] + 1).toByte()
        assertThrows(BackupDecryptException::class.java) { decrypt(cipher) }
    }

    @Test
    fun `cloud backup envelope is not accepted as a local archive`() {
        // 两种格式的 magic 不同：拿云备份的 payload 来导入应当得到「认不出格式」，
        // 而不是走进解密流程报「密码不对」，那会让人一直去试密码
        val envelope = BackupCrypto.encrypt("x".toByteArray(), passphrase)
        val e = assertThrows(BackupDecryptException::class.java) { decrypt(envelope) }
        assertTrue(e.message, e.message!!.contains("无法识别"))
    }

    @Test
    fun `same data encrypts differently each time`() {
        val plain = "同样的内容".toByteArray()
        assertTrue(!encrypt(plain).contentEquals(encrypt(plain)))
    }

    @Test
    fun `single byte reads work`() {
        // ZipInputStream 会做单字节读，read() 的实现不能只在批量读时正确
        val plain = "abc".toByteArray()
        val input = BackupCrypto.decryptingStream(ByteArrayInputStream(encrypt(plain)), passphrase)
        val out = ByteArrayOutputStream()
        while (true) {
            val b = input.read()
            if (b == -1) break
            out.write(b)
        }
        assertEquals("abc", out.toString(Charsets.UTF_8.name()))
    }
}
