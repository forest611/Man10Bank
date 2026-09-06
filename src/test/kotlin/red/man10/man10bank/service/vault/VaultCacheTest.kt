package red.man10.man10bank.service.vault

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * ローカル Vault 台帳（予約台帳方式）のテスト（VaultProvider 5.3/6）。
 * - pendingDelta は減算予約のみ（未確定入金は残高に現れない）。
 * - available = confirmed - 予約合計。予約は operationId 単位で確定/取消できる。
 */
@DisplayName("VaultCache（予約台帳）のテスト")
class VaultCacheTest {

    private val uuid = UUID.fromString("00000000-0000-0000-0000-000000000001")

    private fun readyCache(balance: Long = 1000L, version: Long = 1L): VaultCache {
        val cache = VaultCache()
        cache.beginSession(uuid, "sess-1", balance, version)
        return cache
    }

    @Test
    @DisplayName("LOADING: markLoading 直後は available を返さず残高 0 扱い")
    fun loadingHasNoBalance() {
        val cache = VaultCache()
        cache.markLoading(uuid)
        assertTrue(cache.contains(uuid))
        assertEquals(VaultCache.Status.LOADING, cache.statusOf(uuid))
        assertNull(cache.availableBalance(uuid), "LOADING では予約可否判定に使えない")
        assertEquals(-1L, cache.snapshot(uuid)?.confirmedVersion)
    }

    @Test
    @DisplayName("beginSession で READY になり、sessionId と確定残高を保持する")
    fun beginSession() {
        val cache = readyCache(1500L, 3L)
        val snap = cache.snapshot(uuid)!!
        assertEquals(VaultCache.Status.READY, snap.status)
        assertEquals("sess-1", snap.sessionId)
        assertEquals(1500L, snap.confirmedBalance)
        assertEquals(1500L, snap.availableBalance)
        assertEquals(3L, snap.confirmedVersion)
    }

    @Test
    @DisplayName("予約: available が即座に減り、超過分の予約は拒否される")
    fun reserveReducesAvailable() {
        val cache = readyCache(1000L)
        assertTrue(cache.tryReserve(uuid, "op-1", 700L))
        assertEquals(300L, cache.availableBalance(uuid))
        assertFalse(cache.tryReserve(uuid, "op-2", 400L), "available(300) を超える予約は拒否")
        assertTrue(cache.tryReserve(uuid, "op-3", 300L))
        assertEquals(0L, cache.availableBalance(uuid))
    }

    @Test
    @DisplayName("予約の確定: 予約が外れ、応答の確定残高+version が反映される")
    fun confirmOperation() {
        val cache = readyCache(1000L, 1L)
        cache.tryReserve(uuid, "op-1", 700L)
        cache.confirmOperation(uuid, "op-1", 300L, 2L)
        val snap = cache.snapshot(uuid)!!
        assertEquals(300L, snap.confirmedBalance)
        assertEquals(2L, snap.confirmedVersion)
        assertEquals(0L, snap.pendingTotal)
        assertEquals(300L, snap.availableBalance)
    }

    @Test
    @DisplayName("予約の取消: available が予約前に戻る")
    fun rollbackOperation() {
        val cache = readyCache(1000L)
        cache.tryReserve(uuid, "op-1", 700L)
        cache.rollbackOperation(uuid, "op-1")
        assertEquals(1000L, cache.availableBalance(uuid))
    }

    @Test
    @DisplayName("reconcile: version が古い応答は捨て、新しい応答だけ反映する（予約は保持）")
    fun reconcileKeepsPending() {
        val cache = readyCache(1000L, 5L)
        cache.tryReserve(uuid, "op-1", 200L)

        cache.reconcile(uuid, 9999L, 4L) // 古い version は無視
        assertEquals(1000L, cache.snapshot(uuid)!!.confirmedBalance)

        cache.reconcile(uuid, 1500L, 6L)
        val snap = cache.snapshot(uuid)!!
        assertEquals(1500L, snap.confirmedBalance)
        assertEquals(200L, snap.pendingTotal, "確定反映後も未確定予約は残る")
        assertEquals(1300L, snap.availableBalance)
    }

    @Test
    @DisplayName("applyAuthoritative: version 前進のときだけ反映し true（乖離検知）を返す")
    fun applyAuthoritative() {
        val cache = readyCache(1000L, 5L)
        assertFalse(cache.applyAuthoritative(uuid, 500L, 5L), "同 version は捨てる")
        assertEquals(1000L, cache.snapshot(uuid)!!.confirmedBalance)
        assertTrue(cache.applyAuthoritative(uuid, 500L, 6L))
        assertEquals(500L, cache.snapshot(uuid)!!.confirmedBalance)
    }

    @Test
    @DisplayName("DRAINING / CONFLICT では新規予約を拒否する")
    fun reserveRejectedWhenNotReady() {
        val cache = readyCache(1000L)
        cache.markDraining(uuid)
        assertFalse(cache.tryReserve(uuid, "op-1", 100L))

        val cache2 = readyCache(1000L)
        cache2.markConflict(uuid)
        assertFalse(cache2.tryReserve(uuid, "op-1", 100L))
    }

    @Test
    @DisplayName("evict 後は台帳エントリが消え、未キャッシュへの操作は no-op")
    fun evict() {
        val cache = readyCache(1000L)
        cache.evict(uuid)
        assertFalse(cache.contains(uuid))
        assertFalse(cache.tryReserve(uuid, "op-1", 100L))
        cache.reconcile(uuid, 500L, 10L) // no-op（例外を投げない）
        assertNull(cache.snapshot(uuid))
    }
}
