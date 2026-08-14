package com.myapp.feature.settings.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * 本地备份归档往返（PRD 4.6）。
 *
 * 这套测试的重点是**图片**：结构化数据那半截云备份已经有 [BackupCodecTest] 守着，
 * 而图片只有本地这条路能迁移，一旦往返有问题，换机时笔记插图就永久没了。
 */
class LocalBackupArchiveTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val archive = LocalBackupArchive()
    private val passphrase = "correct horse battery"

    private fun imageFile(name: String, bytes: ByteArray): File =
        temp.newFile(name).apply { writeBytes(bytes) }

    @Test
    fun `images and json survive the round trip byte for byte`() {
        val photo = Random(7).nextBytes(300_000) // 明显跨过 512KB 帧边界的组合
        val small = "fake png".toByteArray()
        val images = listOf(
            ArchivedImage("notes/uuid-a/1.jpg", imageFile("a.jpg", photo)),
            ArchivedImage("notes/uuid-b/cover.png", imageFile("b.png", small)),
        )

        val bytes = ByteArrayOutputStream().also { out ->
            val result = archive.write(out, passphrase, """{"m":1}""", """{"s":2}""", images)
            assertEquals(2, result.imageCount)
            assertTrue(result.sizeBytes > 0)
        }.toByteArray()

        val staging = temp.newFolder("staging")
        val content = archive.read(ByteArrayInputStream(bytes), passphrase, staging)

        assertEquals("""{"m":1}""", content.manifestJson)
        assertEquals("""{"s":2}""", content.snapshotJson)
        assertEquals(2, content.imageCount)
        assertArrayEquals(photo, File(staging, "notes/uuid-a/1.jpg").readBytes())
        assertArrayEquals(small, File(staging, "notes/uuid-b/cover.png").readBytes())
    }

    @Test
    fun `archive with no images round trips`() {
        val bytes = ByteArrayOutputStream().also {
            archive.write(it, passphrase, "{}", """{"todos":[]}""", emptyList())
        }.toByteArray()

        val content = archive.read(ByteArrayInputStream(bytes), passphrase, temp.newFolder("empty"))
        assertEquals(0, content.imageCount)
        assertEquals("""{"todos":[]}""", content.snapshotJson)
    }

    @Test
    fun `missing image file is skipped instead of failing the whole export`() {
        // 某张图被外部清理掉了，不该连累其余数据导不出去
        val images = listOf(
            ArchivedImage("notes/uuid-a/1.jpg", File(temp.root, "does-not-exist.jpg")),
            ArchivedImage("notes/uuid-a/2.jpg", imageFile("real.jpg", "ok".toByteArray())),
        )
        val bytes = ByteArrayOutputStream().also {
            assertEquals(1, archive.write(it, passphrase, "{}", "{}", images).imageCount)
        }.toByteArray()

        val staging = temp.newFolder("partial")
        assertEquals(1, archive.read(ByteArrayInputStream(bytes), passphrase, staging).imageCount)
        assertFalse(File(staging, "notes/uuid-a/1.jpg").exists())
    }

    @Test
    fun `wrong passphrase cannot read the archive`() {
        val bytes = ByteArrayOutputStream().also {
            archive.write(it, passphrase, "{}", "{}", emptyList())
        }.toByteArray()

        assertThrows(BackupDecryptException::class.java) {
            archive.read(ByteArrayInputStream(bytes), "wrong", temp.newFolder("nope"))
        }
    }

    @Test
    fun `entry escaping the staging directory is rejected`() {
        // Zip Slip：备份文件是在设备之间传来传去的，条目名不能当成可信输入。
        // 构造一个把路径指到暂存目录外面的归档，读的时候必须拒绝而不是照写。
        val evil = ByteArrayOutputStream().also { out ->
            BackupCrypto.encryptingStream(out, passphrase).use { encrypting ->
                java.util.zip.ZipOutputStream(encrypting).use { zip ->
                    zip.putNextEntry(java.util.zip.ZipEntry(LocalBackupArchive.MANIFEST_ENTRY))
                    zip.write("{}".toByteArray())
                    zip.closeEntry()
                    zip.putNextEntry(
                        java.util.zip.ZipEntry(LocalBackupArchive.FILES_PREFIX + "../../pwned.txt"),
                    )
                    zip.write("evil".toByteArray())
                    zip.closeEntry()
                }
            }
        }.toByteArray()

        val staging = temp.newFolder("guarded")
        val e = assertThrows(BackupDecryptException::class.java) {
            archive.read(ByteArrayInputStream(evil), passphrase, staging)
        }
        assertTrue(e.message, e.message!!.contains("非法路径"))
        assertFalse(File(staging.parentFile.parentFile, "pwned.txt").exists())
    }

    @Test
    fun `archive without a snapshot entry is rejected`() {
        val onlyManifest = ByteArrayOutputStream().also { out ->
            BackupCrypto.encryptingStream(out, passphrase).use { encrypting ->
                java.util.zip.ZipOutputStream(encrypting).use { zip ->
                    zip.putNextEntry(java.util.zip.ZipEntry(LocalBackupArchive.MANIFEST_ENTRY))
                    zip.write("{}".toByteArray())
                    zip.closeEntry()
                }
            }
        }.toByteArray()

        val e = assertThrows(BackupDecryptException::class.java) {
            archive.read(ByteArrayInputStream(onlyManifest), passphrase, temp.newFolder("nodata"))
        }
        assertTrue(e.message, e.message!!.contains("没有数据"))
    }

    @Test
    fun `unknown entries from a newer format are ignored`() {
        // 将来版本往归档里加了新东西，旧 App 至少还能把认识的部分恢复回来
        val withExtra = ByteArrayOutputStream().also { out ->
            BackupCrypto.encryptingStream(out, passphrase).use { encrypting ->
                java.util.zip.ZipOutputStream(encrypting).use { zip ->
                    zip.putNextEntry(java.util.zip.ZipEntry(LocalBackupArchive.MANIFEST_ENTRY))
                    zip.write("{}".toByteArray())
                    zip.closeEntry()
                    zip.putNextEntry(java.util.zip.ZipEntry(LocalBackupArchive.SNAPSHOT_ENTRY))
                    zip.write("{}".toByteArray())
                    zip.closeEntry()
                    zip.putNextEntry(java.util.zip.ZipEntry("future/thing.bin"))
                    zip.write(byteArrayOf(1, 2, 3))
                    zip.closeEntry()
                }
            }
        }.toByteArray()

        val content = archive.read(ByteArrayInputStream(withExtra), passphrase, temp.newFolder("fwd"))
        assertEquals("{}", content.snapshotJson)
    }
}
