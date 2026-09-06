package red.man10.man10bank.service.vault

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.logging.Logger

/**
 * Provider 送信待ちキューの退避・復元テスト（VaultProvider 6.4）。
 * - persistUnsent で未送信操作がディスクへ退避され、loadPersisted で作成時刻順に復元される。
 * - complete で退避ファイルが消え、markFailed で failed/ へ隔離される。
 */
@DisplayName("VaultWriteQueue（永続退避）のテスト")
class VaultWriteQueueTest {

    private val logger: Logger = Logger.getLogger("VaultWriteQueueTest")

    private fun op(id: String, createdAt: Long, amount: Long = 100L) = VaultWriteQueue.Op(
        operationId = id,
        uuid = "00000000-0000-0000-0000-000000000001",
        type = VaultWriteQueue.Type.WITHDRAW,
        amount = amount,
        note = "VaultWithdraw",
        displayNote = "電子マネー出金",
        createdAtMillis = createdAt,
    )

    @Test
    @DisplayName("persistUnsent → loadPersisted で作成時刻順に復元される（同一operationId再送の前提）")
    fun persistAndRestore(@TempDir dir: File) {
        val queue = VaultWriteQueue(dir, logger)
        queue.enqueue(op("op-b", createdAt = 2000L))
        queue.enqueue(op("op-a", createdAt = 1000L))
        queue.persistUnsent()

        val restoredQueue = VaultWriteQueue(dir, logger)
        assertEquals(2, restoredQueue.loadPersisted())
        assertEquals("op-a", restoredQueue.peek()?.operationId, "作成時刻の古い順に復元される")
        assertEquals(2, restoredQueue.size())
    }

    @Test
    @DisplayName("complete で退避ファイルが削除され、再起動後に再送されない")
    fun completeRemovesPersistedFile(@TempDir dir: File) {
        val queue = VaultWriteQueue(dir, logger)
        val o = op("op-1", 1000L)
        queue.enqueue(o)
        queue.persistUnsent()
        assertTrue(File(dir, "vault-queue/pending/1000-op-1.json").exists())

        queue.complete(o)
        assertFalse(File(dir, "vault-queue/pending/1000-op-1.json").exists())
        assertEquals(0, VaultWriteQueue(dir, logger).loadPersisted())
    }

    @Test
    @DisplayName("markFailed で failed/ へ隔離され、pending には残らない")
    fun markFailedQuarantines(@TempDir dir: File) {
        val queue = VaultWriteQueue(dir, logger)
        val o = op("op-1", 1000L)
        queue.enqueue(o)
        queue.persistUnsent()

        queue.markFailed(o, "テスト用の業務失敗")

        assertEquals(0, queue.size())
        assertFalse(File(dir, "vault-queue/pending/1000-op-1.json").exists())
        assertTrue(File(dir, "vault-queue/failed/1000-op-1.json").exists())
        assertEquals(0, VaultWriteQueue(dir, logger).loadPersisted(), "failed は再送対象にしない")
    }

    @Test
    @DisplayName("hasOpsFor は対象 UUID の未送信操作の有無を返す（quit ドレイン判定用）")
    fun hasOpsFor(@TempDir dir: File) {
        val queue = VaultWriteQueue(dir, logger)
        val o = op("op-1", 1000L)
        queue.enqueue(o)
        assertTrue(queue.hasOpsFor(o.playerUuid()))
        queue.complete(o)
        assertFalse(queue.hasOpsFor(o.playerUuid()))
    }
}
