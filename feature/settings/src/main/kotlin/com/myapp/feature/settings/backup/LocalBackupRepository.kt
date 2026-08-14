package com.myapp.feature.settings.backup

import android.content.Context
import android.net.Uri
import com.myapp.core.common.di.IoDispatcher
import com.myapp.core.database.DATABASE_SCHEMA_VERSION
import com.myapp.core.database.backup.BackupDataSource
import com.myapp.core.database.backup.DatabaseSnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/** 一次本地导出/导入的结果，用于给用户一句能看懂的反馈。 */
data class LocalBackupSummary(
    val rowCount: Int,
    val imageCount: Int,
    val sizeBytes: Long,
)

/** 本地备份失败，且原因是用户能看懂、能采取行动的（相对于裸的 IOException）。 */
class LocalBackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * 本地备份导出/导入（PRD 4.6）。
 *
 * 与云备份（3.13）的关系：**结构化数据用的是同一套 [DatabaseSnapshot] 与同一个
 * `BackupDataSource.restore` 覆盖恢复路径**，本地这条多做的事只有两件——
 * 落到用户自己选的文件（SAF）、以及**带上笔记图片**。图片是云备份有意不含的
 * （PRD 3.13 说明了原因），所以这条路径是换机时插图唯一的迁移方式。
 */
@Singleton
class LocalBackupRepository @Inject constructor(
    private val dataSource: BackupDataSource,
    private val archive: LocalBackupArchive,
    private val json: Json,
    @ApplicationContext private val context: Context,
    @IoDispatcher private val io: CoroutineDispatcher,
) {

    /** 默认文件名。带时间戳，用户连着导出几份也不会互相覆盖。 */
    fun suggestedFileName(now: Long = System.currentTimeMillis()): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date(now))
        return "myapp-backup-$stamp$FILE_EXTENSION"
    }

    suspend fun export(uri: Uri, passphrase: String): LocalBackupSummary = withContext(io) {
        val now = System.currentTimeMillis()
        val snapshot = dataSource.export(now)
        val images = collectImages()
        val manifest = LocalBackupManifest(
            appVersion = appVersionName(),
            schemaVersion = DATABASE_SCHEMA_VERSION,
            createdAt = now,
            rowCount = snapshot.rowCount,
            imageCount = images.size,
        )

        val result = context.contentResolver.openOutputStream(uri).use { out ->
            if (out == null) throw LocalBackupException("打不开你选的位置，请换一个目录再试")
            archive.write(
                sink = out,
                passphrase = passphrase,
                manifestJson = json.encodeToString(LocalBackupManifest.serializer(), manifest),
                snapshotJson = json.encodeToString(DatabaseSnapshot.serializer(), snapshot),
                images = images,
            )
        }
        LocalBackupSummary(
            rowCount = snapshot.rowCount,
            imageCount = result.imageCount,
            sizeBytes = result.sizeBytes,
        )
    }

    /**
     * 从备份文件**覆盖恢复**本机数据（数据库 + 笔记图片）。
     *
     * 顺序是刻意的：整份归档先读完并落进暂存目录 → 再恢复数据库 → 最后才换图片目录。
     * 中途任何一步失败（密码不对、文件截断、schema 太新），本机数据都还是原样。
     */
    suspend fun import(uri: Uri, passphrase: String): LocalBackupSummary = withContext(io) {
        // 暂存目录放在 filesDir 而不是 cacheDir：图片最终要 rename 进 filesDir/notes，
        // 同一目录树内的 rename 才能保留正确的文件属主/SELinux 标签
        //（实测从 cacheDir 搬过去，恢复出来的图片会带着 cache 的组标签）。
        val staging = File(context.filesDir, ".restore-${System.currentTimeMillis()}")
        try {
            // 上一次恢复要是被杀在半路，残留的暂存目录会一直占着空间，开始前先清掉
            context.filesDir.listFiles { f -> f.isDirectory && f.name.startsWith(".restore-") }
                ?.forEach { it.deleteRecursively() }
            staging.mkdirs()
            val content = context.contentResolver.openInputStream(uri).use { input ->
                if (input == null) throw LocalBackupException("打不开这个文件，请重新选一次")
                archive.read(input, passphrase, staging)
            }

            val manifest = runCatching {
                json.decodeFromString(LocalBackupManifest.serializer(), content.manifestJson)
            }.getOrElse { throw LocalBackupException("备份文件的清单无法解析，文件可能已损坏", it) }
            if (manifest.formatVersion > LocalBackupArchive.FORMAT_VERSION) {
                throw LocalBackupException(
                    "这份备份来自更新版本的 App（格式 v${manifest.formatVersion}），请先升级 App 再恢复",
                )
            }

            val snapshot = runCatching {
                json.decodeFromString(DatabaseSnapshot.serializer(), content.snapshotJson)
            }.getOrElse { throw LocalBackupException("备份数据无法解析，文件可能已损坏", it) }

            // schema 版本检查在 restore 里，整个恢复是一个事务，失败会整体回滚
            runCatching { dataSource.restore(snapshot) }
                .getOrElse { throw LocalBackupException(it.message ?: "恢复数据库失败", it) }

            swapImages(staging)

            LocalBackupSummary(
                rowCount = snapshot.rowCount,
                imageCount = content.imageCount,
                sizeBytes = 0,
            )
        } finally {
            staging.deleteRecursively()
        }
    }

    /** 收集 `filesDir/notes` 下的全部附件（含子目录）。目前只有笔记会往 filesDir 写图片。 */
    private fun collectImages(): List<ArchivedImage> {
        val root = File(context.filesDir, IMAGE_DIR)
        if (!root.isDirectory) return emptyList()
        return root.walkTopDown()
            .filter { it.isFile }
            .map { ArchivedImage(relativePath = "$IMAGE_DIR/${it.toRelativeString(root).replace(File.separatorChar, '/')}", file = it) }
            .toList()
    }

    /**
     * 用暂存目录里的图片替换现有的 `filesDir/notes`。
     *
     * 数据库这时已经被整表覆盖了，旧图片对应的笔记已经不存在，留着就是永远不会被引用的垃圾，
     * 所以是**替换**而不是合并。先删后搬之间有个极短的窗口，真在那一刻挂掉会丢图片；
     * 换成「搬到旁边再删」可以消掉这个窗口，但代价是峰值占用两倍磁盘——
     * 单人自用场景下选了前者，这里记一笔。
     */
    private fun swapImages(staging: File) {
        val restored = File(staging, IMAGE_DIR)
        val target = File(context.filesDir, IMAGE_DIR)
        target.deleteRecursively()
        if (!restored.isDirectory) return
        target.parentFile?.mkdirs()
        if (restored.renameTo(target)) return
        // renameTo 跨文件系统会失败（cacheDir 与 filesDir 通常同分区，但不保证），退回逐个拷贝
        restored.copyRecursively(target, overwrite = true) { _, e: IOException -> throw e }
    }

    private fun appVersionName(): String = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
    }.getOrDefault("unknown")

    private companion object {
        /** 相对 `filesDir` 的附件目录，与 `NoteRepository.importImages` 的约定一致。 */
        const val IMAGE_DIR = "notes"

        const val FILE_EXTENSION = ".myapbk"
    }
}
