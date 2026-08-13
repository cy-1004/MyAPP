package com.myapp.feature.settings.backup

import com.myapp.core.database.backup.DatabaseSnapshot
import com.myapp.core.database.model.CategoryEntity
import com.myapp.core.database.model.NoteEntity
import com.myapp.core.database.model.TodoEntity
import com.myapp.core.database.model.TransactionEntity
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 备份编解码往返测试（PRD 3.13）。
 *
 * 这条链路的特点是「错了也不会当场报错」——加密后的密文谁也看不出内容对不对，
 * 等到真要恢复数据的那天才发现解不开就晚了。所以往返一致性必须有测试兜住。
 */
class BackupCodecTest {

    // 与 :core:network 提供的单例保持同样配置
    private val codec = BackupCodec(Json { ignoreUnknownKeys = true; coerceInputValues = true })

    private val passphrase = "correct horse battery"

    private fun sampleSnapshot() = DatabaseSnapshot(
        schemaVersion = 9,
        createdAt = 1_760_000_000_000L,
        todos = listOf(
            TodoEntity(
                id = 1, uuid = "todo-uuid-1", title = "买牛奶", note = "低脂",
                dueAt = 1_760_100_000_000L, priority = 2, tags = "生活,采购",
                done = false, createdAt = 1L, updatedAt = 2L,
            ),
            // 软删除墓碑：必须原样带过去，否则换机恢复后已删的数据会「诈尸」
            TodoEntity(
                id = 2, uuid = "todo-uuid-2", title = "已删除的事",
                createdAt = 1L, updatedAt = 2L, deletedAt = 3L,
            ),
        ),
        notes = listOf(
            NoteEntity(
                id = 7, uuid = "note-uuid", content = "# 标题\n正文包含 emoji 🎉 与中文",
                tags = "随手记", createdAt = 1L, updatedAt = 2L,
            ),
        ),
        categories = listOf(
            CategoryEntity(
                id = 3, uuid = "cat-uuid", name = "餐饮", icon = "restaurant",
                color = "#FF8800", sortOrder = 1, createdAt = 1L, updatedAt = 2L,
            ),
        ),
        transactions = listOf(
            TransactionEntity(
                id = 5, uuid = "tx-uuid", amount = 1234, direction = "EXPENSE",
                categoryId = 3, occurredAt = 1_760_050_000_000L, status = "CONFIRMED",
                source = "MANUAL", createdAt = 1L, updatedAt = 2L,
            ),
        ),
    )

    @Test
    fun `encode then decode restores the snapshot exactly`() {
        val original = sampleSnapshot()
        val encoded = codec.encode(original, passphrase)
        val decoded = codec.decode(encoded.payloadBase64, passphrase, encoded.checksum)

        assertEquals(original.schemaVersion, decoded.schemaVersion)
        assertEquals(original.createdAt, decoded.createdAt)
        assertEquals(original.todos, decoded.todos)
        assertEquals(original.notes, decoded.notes)
        assertEquals(original.categories, decoded.categories)
        assertEquals(original.transactions, decoded.transactions)
        assertEquals(original.rowCount, decoded.rowCount)
    }

    @Test
    fun `soft delete tombstones survive the round trip`() {
        val encoded = codec.encode(sampleSnapshot(), passphrase)
        val decoded = codec.decode(encoded.payloadBase64, passphrase)
        assertEquals(3L, decoded.todos.single { it.id == 2L }.deletedAt)
    }

    @Test
    fun `wrong passphrase is rejected rather than returning garbage`() {
        val encoded = codec.encode(sampleSnapshot(), passphrase)
        assertThrows(BackupDecryptException::class.java) {
            codec.decode(encoded.payloadBase64, "wrong passphrase")
        }
    }

    @Test
    fun `corrupted payload fails the checksum before decryption is attempted`() {
        val encoded = codec.encode(sampleSnapshot(), passphrase)
        val tampered = encoded.payloadBase64.let { it.dropLast(8) + "AAAAAAAA" }
        assertThrows(BackupDecryptException::class.java) {
            codec.decode(tampered, passphrase, encoded.checksum)
        }
    }

    @Test
    fun `same data encrypts differently each time`() {
        // salt 与 iv 每次都要重新随机：GCM 在相同 key 下重用 iv 会直接泄露明文
        val snapshot = sampleSnapshot()
        val first = codec.encode(snapshot, passphrase)
        val second = codec.encode(snapshot, passphrase)
        assertNotEquals(first.payloadBase64, second.payloadBase64)
        assertNotEquals(first.checksum, second.checksum)
    }

    @Test
    fun `empty snapshot round trips`() {
        val empty = DatabaseSnapshot(schemaVersion = 9, createdAt = 0L)
        val encoded = codec.encode(empty, passphrase)
        val decoded = codec.decode(encoded.payloadBase64, passphrase)
        assertEquals(0, decoded.rowCount)
    }

    @Test
    fun `gzip meaningfully shrinks repetitive json`() {
        // 先压缩再加密的意义：JSON 里大量重复键名压缩率很高，直接决定每天上传的流量。
        // 若哪天有人把顺序调反（先加密再压缩），密文压不动，这条会挂。
        val many = DatabaseSnapshot(
            schemaVersion = 9,
            createdAt = 0L,
            todos = (1..300).map {
                TodoEntity(
                    id = it.toLong(), uuid = "uuid-$it", title = "重复的待办标题",
                    tags = "标签A,标签B", createdAt = 1L, updatedAt = 2L,
                )
            },
        )
        val rawJsonSize = Json.encodeToString(DatabaseSnapshot.serializer(), many).toByteArray().size
        val encodedSize = codec.encode(many, passphrase).sizeBytes
        assertTrue(
            "压缩后 $encodedSize 应显著小于原始 JSON $rawJsonSize",
            encodedSize < rawJsonSize / 2,
        )
    }
}
