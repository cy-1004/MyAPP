package com.myapp.feature.settings.backup

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.Serializable

/**
 * 本地备份文件的清单（写在归档里的 `manifest.json`）。
 *
 * [formatVersion] 是归档**容器**的版本，与数据库 schema 版本各管各的：
 * 前者变了说明文件布局变了（旧 App 读不了），后者变了说明表结构变了
 * （由 `BackupDataSource.restore` 判断能不能恢复）。
 */
@Serializable
data class LocalBackupManifest(
    val formatVersion: Int = LocalBackupArchive.FORMAT_VERSION,
    val appVersion: String,
    val schemaVersion: Int,
    val createdAt: Long,
    val rowCount: Int,
    val imageCount: Int,
)

/** 归档里的一张图片：[relativePath] 是相对 `filesDir` 的路径，如 `notes/<uuid>/1.jpg`。 */
data class ArchivedImage(val relativePath: String, val file: File)

/** 读出来的归档内容。图片已经落到调用方给的暂存目录里，不在内存中。 */
data class LocalBackupContent(
    val manifestJson: String,
    val snapshotJson: String,
    val imageCount: Int,
)

/**
 * 本地备份归档的读写（PRD 4.6）：**加密的 zip**，里面同时装结构化数据和笔记图片。
 *
 * ```
 * [ BackupCrypto 分帧密文 ]  解开后是一个 zip：
 *   manifest.json                 清单（版本/时间/条数）
 *   snapshot.json                 全库快照，与云备份用的是同一个 DatabaseSnapshot
 *   files/notes/<uuid>/<name>     笔记图片原件（云备份有意不含图片，这里才是它们唯一的迁移路径）
 * ```
 *
 * 是「先 zip 再整体加密」而不是「zip 里放加密条目」：后者要么每个条目各推一次
 * PBKDF2（210k 次迭代 × N 张图，慢到不可用），要么共用一个 key 却还得自己管 iv；
 * 而且前者连**文件名**都一起加密了——「有几张图、笔记 uuid 是什么」本身也是隐私。
 *
 * 不碰 Android API（只认 [InputStream]/[OutputStream]/[File]），所以能在 JVM 单测里
 * 直接跑完整往返。SAF 的 Uri 由 [LocalBackupRepository] 负责。
 */
@Singleton
class LocalBackupArchive @Inject constructor() {

    /**
     * 写一份归档，返回写出的字节数。
     *
     * [images] 里读不到的文件会被跳过而不是让整次导出失败——某张图丢了不该
     * 连累其余全部数据导不出去，缺了几张会体现在返回的张数上。
     */
    fun write(
        sink: OutputStream,
        passphrase: String,
        manifestJson: String,
        snapshotJson: String,
        images: List<ArchivedImage>,
    ): WriteResult {
        val counting = CountingOutputStream(sink)
        var written = 0
        BackupCrypto.encryptingStream(counting, passphrase).use { encrypting ->
            ZipOutputStream(encrypting).use { zip ->
                zip.putTextEntry(MANIFEST_ENTRY, manifestJson)
                zip.putTextEntry(SNAPSHOT_ENTRY, snapshotJson)
                images.forEach { image ->
                    if (!image.file.isFile) return@forEach
                    zip.putNextEntry(ZipEntry(FILES_PREFIX + image.relativePath))
                    image.file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    written++
                }
            }
        }
        return WriteResult(sizeBytes = counting.count, imageCount = written)
    }

    /**
     * 读一份归档。图片写进 [stagingDir]（调用方给一个空目录），结构化数据以 JSON 文本返回。
     *
     * 先落暂存目录而不是直接盖掉 `filesDir/notes`：解密可能读到一半才失败（密码对但文件
     * 被截断），那时用户的现有图片必须还在。搬家由调用方在整份读完之后做。
     */
    fun read(source: InputStream, passphrase: String, stagingDir: File): LocalBackupContent {
        var manifestJson: String? = null
        var snapshotJson: String? = null
        var imageCount = 0

        BackupCrypto.decryptingStream(source, passphrase).use { decrypting ->
            ZipInputStream(decrypting).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    when {
                        entry.isDirectory -> Unit
                        entry.name == MANIFEST_ENTRY -> manifestJson = zip.readBytes().decodeToString()
                        entry.name == SNAPSHOT_ENTRY -> snapshotJson = zip.readBytes().decodeToString()
                        entry.name.startsWith(FILES_PREFIX) -> {
                            val target = resolveInside(stagingDir, entry.name.removePrefix(FILES_PREFIX))
                            target.parentFile?.mkdirs()
                            target.outputStream().use { zip.copyTo(it) }
                            imageCount++
                        }
                        // 其它条目直接忽略：将来版本加了新东西，旧 App 至少还能恢复它认识的部分
                    }
                    zip.closeEntry()
                }
            }
        }

        val manifest = manifestJson
            ?: throw BackupDecryptException("备份文件缺少清单，可能不是本 App 导出的")
        val snapshot = snapshotJson
            ?: throw BackupDecryptException("备份文件里没有数据，无法恢复")
        return LocalBackupContent(manifest, snapshot, imageCount)
    }

    /**
     * 把归档里的相对路径解析到 [root] 下，并确认没跑出去。
     *
     * zip 条目名是文件里写的、不是我们生成的：一个构造过的归档可以写
     * `../../databases/myapp.db` 这样的名字，直接盖掉数据库文件（Zip Slip）。
     * 备份文件会在设备间传来传去，这个检查不能省。
     */
    private fun resolveInside(root: File, relativePath: String): File {
        val target = File(root, relativePath)
        val rootPath = root.canonicalPath + File.separator
        if (!target.canonicalPath.startsWith(rootPath)) {
            throw BackupDecryptException("备份文件包含非法路径「$relativePath」，已中止恢复")
        }
        return target
    }

    private fun ZipOutputStream.putTextEntry(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray(Charsets.UTF_8))
        closeEntry()
    }

    data class WriteResult(val sizeBytes: Long, val imageCount: Int)

    companion object {
        /** 归档容器格式版本。布局变了才加，改 schema 不用动它。 */
        const val FORMAT_VERSION = 1

        const val MANIFEST_ENTRY = "manifest.json"
        const val SNAPSHOT_ENTRY = "snapshot.json"

        /** 附件条目前缀，后面接相对 `filesDir` 的路径。 */
        const val FILES_PREFIX = "files/"
    }
}

/** 只为了知道最终写了多少字节，好在结果里告诉用户备份文件多大。 */
private class CountingOutputStream(private val sink: OutputStream) : OutputStream() {
    var count = 0L
        private set

    override fun write(b: Int) {
        sink.write(b)
        count++
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        sink.write(b, off, len)
        count += len
    }

    override fun flush() = sink.flush()
    override fun close() = sink.close()
}
