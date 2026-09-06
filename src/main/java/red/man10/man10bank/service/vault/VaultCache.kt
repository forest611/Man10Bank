package red.man10.man10bank.service.vault

import java.util.UUID

/**
 * 在席プレイヤーのローカル Vault 台帳（VaultProvider 6）。
 *
 * - 残高は内部で Long（整数円）で保持し、Vault(Economy) 境界でだけ Double に変換する（VaultProvider 12）。
 * - `pendingDelta` は未確定の「減算予約」だけを operationId ごとに保持する（常に減算方向。VaultProvider 5.3）。
 *   DB 未確定の入金は台帳へ一切反映しない（visible/available に含めない。不変条件12）。
 * - `visibleBalance = availableBalance = confirmedBalance - 予約合計`。
 * - すべての複合操作（予約判定→予約、確定→消し込み）は単一ロックで直列化する。
 *   Economy 同期 API はメインスレッドから呼ばれ、確定応答・再同期は IO スレッドから
 *   このロックを介して適用される（VaultProvider 10.2 の直列化要件をモニタで満たす）。
 * - エントリは「このサーバーで session claim を保持しているプレイヤー」だけが持つ。
 *   claim 前は LOADING、claim 成功で READY、quit で DRAINING → 退避。
 */
class VaultCache {

    enum class Status { LOADING, READY, DRAINING, CONFLICT }

    /** 読み取り用スナップショット。 */
    data class Snapshot(
        val confirmedBalance: Long,
        val confirmedVersion: Long,
        val pendingTotal: Long,
        val status: Status,
        val sessionId: String?,
    ) {
        /** 表示・可否判定に使う残高（DB 未確定の入金は含まれない）。 */
        val availableBalance: Long get() = confirmedBalance - pendingTotal
    }

    private class Entry(
        var confirmedBalance: Long,
        var confirmedVersion: Long,
        var status: Status,
        var sessionId: String?,
    ) {
        /** operationId → 予約額（正の値）。未確定の減算予約だけを持つ。 */
        val pending = LinkedHashMap<String, Long>()
        fun pendingTotal(): Long = pending.values.sum()
    }

    private val lock = Any()
    private val map = HashMap<UUID, Entry>()

    /** join 直後（claim 完了前）のプレースホルダを作る。既存エントリは変更しない。 */
    fun markLoading(uuid: UUID) {
        synchronized(lock) {
            map.getOrPut(uuid) { Entry(0L, -1L, Status.LOADING, null) }
        }
    }

    /** claim 成功: 確定残高・version・sessionId をセットし READY にする。 */
    fun beginSession(uuid: UUID, sessionId: String, balance: Long, version: Long) {
        synchronized(lock) {
            val e = map.getOrPut(uuid) { Entry(0L, -1L, Status.LOADING, null) }
            e.confirmedBalance = balance
            e.confirmedVersion = version
            e.sessionId = sessionId
            e.status = Status.READY
        }
    }

    fun contains(uuid: UUID): Boolean = synchronized(lock) { map.containsKey(uuid) }

    fun snapshot(uuid: UUID): Snapshot? = synchronized(lock) {
        map[uuid]?.let { Snapshot(it.confirmedBalance, it.confirmedVersion, it.pendingTotal(), it.status, it.sessionId) }
    }

    fun statusOf(uuid: UUID): Status? = synchronized(lock) { map[uuid]?.status }

    fun sessionIdOf(uuid: UUID): String? = synchronized(lock) { map[uuid]?.sessionId }

    /** READY エントリの利用可能残高（それ以外は null）。 */
    fun availableBalance(uuid: UUID): Long? = synchronized(lock) {
        map[uuid]?.takeIf { it.status == Status.READY }?.let { it.confirmedBalance - it.pendingTotal() }
    }

    /**
     * 減算予約（VaultProvider 5.3）。READY かつ `availableBalance >= amount` の場合だけ
     * operationId をキーに予約し、利用可能残高を即座に減らす。予約できなければ false。
     */
    fun tryReserve(uuid: UUID, operationId: String, amount: Long): Boolean {
        if (amount <= 0L) return false
        return synchronized(lock) {
            val e = map[uuid] ?: return@synchronized false
            if (e.status != Status.READY) return@synchronized false
            if (e.confirmedBalance - e.pendingTotal() < amount) return@synchronized false
            e.pending[operationId] = amount
            true
        }
    }

    /**
     * 予約の確定消し込み: 予約を外し、応答の確定残高+version を `version >= 現在` で反映する。
     * （この操作を適用した応答の version は必ず現在以上のため、楽観ドリフトも同時に矯正される。）
     */
    fun confirmOperation(uuid: UUID, operationId: String, balance: Long, version: Long) {
        synchronized(lock) {
            val e = map[uuid] ?: return
            e.pending.remove(operationId)
            if (version >= e.confirmedVersion) {
                e.confirmedBalance = balance
                e.confirmedVersion = version
            }
        }
    }

    /** 予約の取り消し（送信失敗・業務失敗時）。 */
    fun rollbackOperation(uuid: UUID, operationId: String) {
        synchronized(lock) {
            map[uuid]?.pending?.remove(operationId)
        }
    }

    /**
     * 確定応答の反映（予約を伴わない操作用）。`version >= 現在` で確定残高を上書きする。
     * 未確定の減算予約はそのまま残る（再適用不要。available は計算値のため自動で追従する）。
     */
    fun reconcile(uuid: UUID, balance: Long, version: Long) {
        synchronized(lock) {
            val e = map[uuid] ?: return
            if (version >= e.confirmedVersion) {
                e.confirmedBalance = balance
                e.confirmedVersion = version
            }
        }
    }

    /**
     * 定期再同期の適用: `version > 現在` のときだけ反映する（自己修復。VaultProvider 6.5）。
     * 反映した場合 true を返す（単一書き込み者の下では通常 false。true は乖離検知を意味する）。
     */
    fun applyAuthoritative(uuid: UUID, balance: Long, version: Long): Boolean {
        return synchronized(lock) {
            val e = map[uuid] ?: return@synchronized false
            if (version > e.confirmedVersion) {
                e.confirmedBalance = balance
                e.confirmedVersion = version
                true
            } else {
                false
            }
        }
    }

    /** quit 開始: 新規予約を止める（送信キューの残操作は sessionId 維持のまま送られる）。 */
    fun markDraining(uuid: UUID) {
        synchronized(lock) { map[uuid]?.status = Status.DRAINING }
    }

    /** 重大不整合の検知: 対象の新規書き込みを止める（VaultProvider 5.7）。 */
    fun markConflict(uuid: UUID) {
        synchronized(lock) { map[uuid]?.status = Status.CONFLICT }
    }

    /** 退避（quit のドレイン完了後・session 失効時）。 */
    fun evict(uuid: UUID) {
        synchronized(lock) { map.remove(uuid) }
    }

    /** エントリを持つ UUID の集合スナップショット。 */
    fun onlineUuids(): Set<UUID> = synchronized(lock) { HashSet(map.keys) }
}
