package com.myapp.feature.settings.backup

import com.myapp.core.database.backup.DatabaseSnapshot
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

/** 编码后的备份，对应 `app_backup` 表的一行。 */
data class EncodedBackup(
    /** 密文信封的 Base64，写入 `payload` 列。 */
    val payloadBase64: String,
    /** 密文（Base64 解码后）的 SHA-256，写入 `checksum` 列。 */
    val checksum: String,
    /** 密文字节数，写入 `size_bytes` 列，用于备份列表展示大小。 */
    val sizeBytes: Long,
)

/**
 * 快照 ↔ 云端 payload 的编解码（PRD 3.13）。
 *
 * 管线：`DatabaseSnapshot → JSON → GZIP → AES-256-GCM → Base64`。
 *
 * 先压缩再加密，顺序不能反：密文是高熵数据，压不动；而 JSON 里大量重复的键名
 * 压缩率很高（实测量级上能到几分之一），直接决定了每天上传的流量。
 */
@Singleton
class BackupCodec @Inject constructor(
    private val json: Json,
) {

    fun encode(snapshot: DatabaseSnapshot, passphrase: String): EncodedBackup {
        val jsonBytes = json.encodeToString(DatabaseSnapshot.serializer(), snapshot)
            .toByteArray(Charsets.UTF_8)
        val envelope = BackupCrypto.encrypt(gzip(jsonBytes), passphrase)
        return EncodedBackup(
            payloadBase64 = Base64.getEncoder().encodeToString(envelope),
            checksum = BackupCrypto.sha256Hex(envelope),
            sizeBytes = envelope.size.toLong(),
        )
    }

    /**
     * @param expectedChecksum 若非空，先校验密文完整性再解密——传输截断的报错会比
     *   「密码不正确」准确得多（GCM 校验失败无法区分这两种情况）。
     */
    fun decode(
        payloadBase64: String,
        passphrase: String,
        expectedChecksum: String? = null,
    ): DatabaseSnapshot {
        val envelope = try {
            Base64.getDecoder().decode(payloadBase64)
        } catch (e: IllegalArgumentException) {
            throw BackupDecryptException("备份数据格式损坏（Base64 解码失败）", e)
        }
        if (expectedChecksum != null && BackupCrypto.sha256Hex(envelope) != expectedChecksum) {
            throw BackupDecryptException("备份数据校验失败，可能在传输中损坏，请重试")
        }
        val jsonBytes = ungzip(BackupCrypto.decrypt(envelope, passphrase))
        return json.decodeFromString(
            DatabaseSnapshot.serializer(),
            jsonBytes.toString(Charsets.UTF_8),
        )
    }

    private fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().also { out ->
        GZIPOutputStream(out).use { it.write(bytes) }
    }.toByteArray()

    private fun ungzip(bytes: ByteArray): ByteArray = try {
        GZIPInputStream(bytes.inputStream()).use { it.readBytes() }
    } catch (e: java.util.zip.ZipException) {
        throw BackupDecryptException("备份数据解压失败", e)
    }
}
