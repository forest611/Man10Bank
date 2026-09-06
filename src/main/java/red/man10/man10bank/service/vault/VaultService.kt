package red.man10.man10bank.service.vault

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.milkbowl.vault.economy.EconomyResponse
import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10bank.api.VaultApiClient
import red.man10.man10bank.api.error.ApiHttpException
import red.man10.man10bank.api.model.request.VaultDepositRequest
import red.man10.man10bank.api.model.request.VaultMoveDirection
import red.man10.man10bank.api.model.request.VaultMoveRequest
import red.man10.man10bank.api.model.request.VaultSessionClaimRequest
import red.man10.man10bank.api.model.request.VaultSessionReleaseRequest
import red.man10.man10bank.api.model.request.VaultSetRequest
import red.man10.man10bank.api.model.request.VaultTransferRequest
import red.man10.man10bank.api.model.request.VaultWithdrawRequest
import red.man10.man10bank.api.model.response.VaultBalanceResponse
import red.man10.man10bank.api.model.response.VaultMoveResponse
import red.man10.man10bank.api.model.response.VaultTransferResponse
import java.io.IOException
import java.util.UUID
import kotlin.coroutines.coroutineContext

/** 管理用の電子マネー操作種別（/meco give|take|set）。 */
enum class VaultAdminOp { GIVE, TAKE, SET }

/** vault_log.source の値（VaultProvider 9.2）。 */
object VaultSources {
    const val PROVIDER = "PROVIDER"
    const val MAN10_API = "MAN10_API"
    const val ADMIN = "ADMIN"
}

/**
 * 電子マネー操作の単一窓口（VaultProvider 7）。
 *
 * 整合性モデル（確定設計）:
 * - すべての書き込みは「対象がこのサーバーに在席し session claim を保持している」場合だけ許可する
 *   （単一書き込み者。別 Paper 在席・オフラインへの操作は増額を含め拒否。VaultProvider 5.4）。
 * - 外部 Vault(Economy) 経路（[providerDeposit]/[providerWithdraw]）は同期でローカル台帳に成立させ、
 *   冪等キー付き送信キューが Man10BankService へ後送する。減算は必ず予約してから成功を返す。
 * - 内製経路（[depositConfirmed] ほか suspend 群）は Man10BankService の確定応答を待つ。
 *   減算は送信前にローカル台帳へ予約し、外部経路との二重引き落としを防ぐ（VaultProvider 5.3）。
 * - DB 未確定の入金はローカル台帳へ反映しない（確定応答後にのみ反映。不変条件12）。
 * - 残高収束は claim 時ロード・確定応答・定期再同期（自己修復）で行う。残高 push は無い。
 */
class VaultService(
    private val plugin: JavaPlugin,
    private val serverName: String,
    private val scope: CoroutineScope,
    private val api: VaultApiClient,
    val cache: VaultCache,
    private val queue: VaultWriteQueue,
) {
    companion object {
        /** IEEE 754 Double で整数を正確に表現できる上限（2^53 - 1）。 */
        const val MAX_SAFE_AMOUNT = 9_007_199_254_740_991L

        /** 送信キューの未処理がこの件数を超えたら DEGRADED として新規 Provider 書き込みを止める。 */
        const val MAX_PENDING_OPS = 100

        /** quit 時にキューのドレインを待つ上限。 */
        const val QUIT_DRAIN_TIMEOUT_MS = 10_000L
    }

    /** 書き込み健全性（VaultProvider 5.6）。 */
    enum class WriteHealth { WRITE_READY, DEGRADED, DOWN, DRAINING }

    @Volatile
    private var connected = false

    /** Provider として ServicesManager に登録済みかつ設定上有効か（Economy.isEnabled 用）。 */
    @Volatile
    private var providerActive = false

    /** session claim で配布される残高上限（権威値）。未取得の間は全書き込みを拒否する（VaultProvider 12）。 */
    @Volatile
    private var maxBalance: Long? = null

    /** shutdown 中フラグ。新規書き込みを止め、キューを退避する。 */
    @Volatile
    private var draining = false

    /** 接続レベル障害を検知したときに WebSocket の再接続を促すフック（[VaultSyncClient.requestReconnect]）。 */
    @Volatile
    private var reconnectRequester: (() -> Unit)? = null

    private var workerJob: Job? = null
    private var resyncJob: Job? = null

    // === 状態管理 ===

    /** 同期接続（WebSocket）の状態。[VaultSyncClient] から更新する。切断時は未送信キューを退避する。 */
    fun setConnected(value: Boolean) {
        connected = value
        if (!value) {
            queue.persistUnsent()
        }
    }

    /** Provider 登録状態（Man10Bank が登録/解除時に設定する）。isEnabled はこれだけを見る。 */
    fun setProviderActive(value: Boolean) {
        providerActive = value
    }

    fun isProviderActive(): Boolean = providerActive && plugin.isEnabled

    /** 接続レベル障害検知時に WebSocket 再接続を促すフックを登録する（[Man10Bank] が配線する）。 */
    fun setReconnectRequester(requester: () -> Unit) {
        reconnectRequester = requester
    }

    /** 確定応答経路（内製 API）の前提条件: 接続済みかつ残高上限を取得済み。 */
    fun isReady(): Boolean = plugin.isEnabled && !draining && connected && maxBalance != null

    /** Provider 書き込みの健全性判定（VaultProvider 5.6）。 */
    fun writeHealth(): WriteHealth = when {
        draining || !plugin.isEnabled -> WriteHealth.DRAINING
        !connected || maxBalance == null -> WriteHealth.DOWN
        queue.size() > MAX_PENDING_OPS -> WriteHealth.DEGRADED
        else -> WriteHealth.WRITE_READY
    }

    /** キュー処理・定期再同期を開始する（Provider 登録成功後に呼ぶ）。退避済み操作があれば復元する。 */
    fun start(resyncIntervalSeconds: Long) {
        val restored = queue.loadPersisted()
        if (restored > 0) {
            plugin.logger.warning("退避済みのProvider送信キュー操作 ${restored} 件を復元しました。接続回復後に同一operationIdで再送します")
        }
        // Paper 強制終了時にメモリキュー上の SUCCESS 返却済み操作が失われ得る許容リスクの告知（VaultProvider 15.1）。
        plugin.logger.warning(
            "Vault Provider 有効化: 外部Vault経路の成功は送信キュー経由でDBへ後送されます。" +
                "プロセス強制終了時、退避前のキュー操作は失われる可能性があります（既知の許容リスク）"
        )
        workerJob = scope.launch { queueWorkerLoop() }
        resyncJob = scope.launch { resyncLoop(resyncIntervalSeconds) }
    }

    /** shutdown: 新規書き込みを止め、未送信キューを退避する。 */
    fun shutdown() {
        draining = true
        queue.persistUnsent()
    }

    /**
     * 接続レベル（トランスポート）の失敗なら、WebSocket の切断検知を待たずに **即 fail-closed** にし、
     * WS 再接続を促す。`IOException`（Connection refused / timeout 等）が対象。
     * `ApiHttpException`（4xx/5xx＝サービスは応答している）は対象外。
     */
    private fun signalConnectionLossIfTransport(ex: Throwable?) {
        // 例外がラップされている場合に備え cause 連鎖も見る（深さは安全のため上限を設ける）。
        val isTransport = generateSequence(ex) { it.cause }.take(10).any { it is IOException }
        if (isTransport) {
            setConnected(false)
            reconnectRequester?.invoke()
        }
    }

    /** 4xx（サービスが業務的に拒否した＝DB未コミットが確定）かどうか。ATM の現金返却可否にも使う。 */
    fun isDefinitelyRejected(ex: Throwable?): Boolean =
        ex is ApiHttpException && ex.status.value in 400..499

    // === 同期 Vault(Economy) 契約（メインスレッド前提。Man10Economy から呼ばれる） ===

    /** 残高取得。claim 済み（READY 以降）はローカル台帳の値、未 claim・未在席は 0。 */
    fun getBalanceSync(uuid: UUID): Double {
        val snap = cache.snapshot(uuid) ?: return 0.0
        if (snap.confirmedVersion < 0) return 0.0 // LOADING（claim 前）
        return snap.availableBalance.toDouble()
    }

    /** `available >= amount` 判定。READY かつ WRITE_READY 以外は false（VaultProvider 6.2）。 */
    fun hasSync(uuid: UUID, amount: Double): Boolean {
        val amt = normalizeAmount(amount) ?: return false
        if (writeHealth() != WriteHealth.WRITE_READY) return false
        val available = cache.availableBalance(uuid) ?: return false
        return available >= amt
    }

    /** 台帳エントリの有無（hasAccount 用。LOADING でも true）。 */
    fun hasEntry(uuid: UUID): Boolean = cache.contains(uuid)

    /**
     * 入金（Vault 契約）。台帳残高は増やさず、冪等キー付きでキューへ登録して SUCCESS を返す。
     * DB 確定は送信キューが担い、確定応答で初めて残高へ反映される（VaultProvider 6.2）。
     */
    fun providerDeposit(uuid: UUID, amount: Double): EconomyResponse {
        val amt = normalizeAmount(amount) ?: return failure("金額が不正です。")
        val health = writeHealth()
        if (health != WriteHealth.WRITE_READY) return failure(healthMessage(health))
        val max = maxBalance ?: return failure("電子マネーに接続できないため操作できません。")
        if (amt > max) return failure("金額が上限を超えています。")
        val snap = cache.snapshot(uuid)
        if (snap == null || snap.status != VaultCache.Status.READY) {
            return failure("対象がこのサーバーに在席していないため電子マネーを操作できません。")
        }
        // 更新後残高の上限をローカルでも検査する（サービス側 409 → CONFLICT 化を避ける事前拒否。VaultProvider 12）。
        if (snap.confirmedBalance + amt > max) return failure("残高上限を超えるため入金できません。")
        val enqueued = queue.enqueue(newOp(uuid, VaultWriteQueue.Type.DEPOSIT, amt, "VaultDeposit", "電子マネー入金"))
        if (!enqueued) return failure("電子マネーの送信キューが混雑しています。")
        // 未確定の入金は残高へ加えない（確定応答後に reconcile で反映）。
        return success(amt, snap.availableBalance.toDouble())
    }

    /**
     * 出金（Vault 契約）。ローカル台帳へ減算予約できた場合だけキューへ登録し SUCCESS を返す。
     * 予約により available が即座に減り、内製経路との二重引き落としを防ぐ（VaultProvider 5.3）。
     */
    fun providerWithdraw(uuid: UUID, amount: Double): EconomyResponse {
        val amt = normalizeAmount(amount) ?: return failure("金額が不正です。")
        val health = writeHealth()
        if (health != WriteHealth.WRITE_READY) return failure(healthMessage(health))
        val max = maxBalance ?: return failure("電子マネーに接続できないため操作できません。")
        if (amt > max) return failure("金額が上限を超えています。")
        val op = newOp(uuid, VaultWriteQueue.Type.WITHDRAW, amt, "VaultWithdraw", "電子マネー出金")
        if (!cache.tryReserve(uuid, op.operationId, amt)) {
            return if (cache.statusOf(uuid) == VaultCache.Status.READY) {
                failure("残高が不足しています。")
            } else {
                failure("対象がこのサーバーに在席していないため電子マネーを操作できません。")
            }
        }
        queue.enqueue(op)
        val after = cache.snapshot(uuid)
        return success(amt, (after?.availableBalance ?: 0L).toDouble())
    }

    /** createPlayerAccount: 在席かつ台帳エントリが無ければ claim を要求する（DB コミットは待たない）。 */
    fun requestEnsure(uuid: UUID, name: String): Boolean {
        if (!isProviderActive()) return false
        if (cache.contains(uuid)) return true
        if (plugin.server.getPlayer(uuid) == null) return false
        cache.markLoading(uuid)
        scope.launch { claim(uuid, name) }
        return true
    }

    // === 確定応答が必要な操作（suspend。内製 API 経路） ===

    /**
     * 電子マネーの権威入金（確定応答待ち）。対象が自サーバー在席（claim 保持）の場合のみ。
     * 成功時に確定残高+version で台帳を補正する（未確定中は残高に現れない）。
     */
    suspend fun depositConfirmed(
        uuid: UUID,
        amount: Double,
        note: String,
        displayNote: String,
        source: String = VaultSources.MAN10_API,
    ): Result<VaultBalanceResponse> {
        val amt = normalizeAmount(amount) ?: return Result.failure(IllegalArgumentException("金額が不正です。"))
        val session = requireResidentSession(uuid).getOrElse { return Result.failure(it) }
        val result = api.deposit(
            VaultDepositRequest(uuid.toString(), amt.toDouble(), plugin.name, note, displayNote, serverName, session, null, source)
        )
        result.onSuccess { res -> cache.reconcile(uuid, res.balance.toLong(), res.version) }
            .onFailure { signalConnectionLossIfTransport(it) }
        return result
    }

    /**
     * 電子マネーの権威出金（確定応答待ち）。送信前にローカル台帳へ予約し、
     * 失敗時は予約を取り消す。サービス側も行ロック下で残高を再チェックする（不足は 409）。
     */
    suspend fun withdrawConfirmed(
        uuid: UUID,
        amount: Double,
        note: String,
        displayNote: String,
        source: String = VaultSources.MAN10_API,
    ): Result<VaultBalanceResponse> {
        val amt = normalizeAmount(amount) ?: return Result.failure(IllegalArgumentException("金額が不正です。"))
        val session = requireResidentSession(uuid).getOrElse { return Result.failure(it) }
        val opId = UUID.randomUUID().toString()
        if (!cache.tryReserve(uuid, opId, amt)) {
            return Result.failure(IllegalStateException("残高が不足しています。"))
        }
        val result = api.withdraw(
            VaultWithdrawRequest(uuid.toString(), amt.toDouble(), plugin.name, note, displayNote, serverName, session, null, source)
        )
        result.onSuccess { res -> cache.confirmOperation(uuid, opId, res.balance.toLong(), res.version) }
            .onFailure {
                cache.rollbackOperation(uuid, opId)
                signalConnectionLossIfTransport(it)
            }
        return result
    }

    /**
     * 電子マネー送金（/pay）。送金元・送金先の両方が自サーバー在席（claim 保持）の場合のみ。
     * 送金元は送信前に予約する。成功時は応答の両残高で両台帳を収束させる（残高 push は無い）。
     */
    suspend fun transfer(
        fromUuid: UUID,
        toUuid: UUID,
        amount: Double,
        note: String,
        displayNote: String,
    ): Result<VaultTransferResponse> {
        val amt = normalizeAmount(amount) ?: return Result.failure(IllegalArgumentException("金額が不正です。"))
        val fromSession = requireResidentSession(fromUuid).getOrElse { return Result.failure(it) }
        val toSession = requireResidentSession(toUuid).getOrElse {
            return Result.failure(IllegalStateException("送金先がこのサーバーに在席していないため送金できません。"))
        }
        val opId = UUID.randomUUID().toString()
        if (!cache.tryReserve(fromUuid, opId, amt)) {
            return Result.failure(IllegalStateException("残高が不足しています。"))
        }
        val result = api.transfer(
            VaultTransferRequest(
                fromUuid = fromUuid.toString(),
                toUuid = toUuid.toString(),
                amount = amt.toDouble(),
                pluginName = plugin.name,
                note = note,
                displayNote = displayNote,
                server = serverName,
                fromSessionId = fromSession,
                toSessionId = toSession,
            )
        )
        result.onSuccess { res ->
            cache.confirmOperation(fromUuid, opId, res.fromBalance.toLong(), res.fromVersion)
            cache.reconcile(toUuid, res.toBalance.toLong(), res.toVersion)
        }.onFailure {
            cache.rollbackOperation(fromUuid, opId)
            signalConnectionLossIfTransport(it)
        }
        return result
    }

    /**
     * 電子マネー ⇄ 銀行残高の移動（ATM/`/deposit`/`/withdraw` 用）。対象が自サーバー在席の場合のみ。
     * VaultToBank（電子マネー減）は送信前に予約する。成功時は電子マネー側台帳を確定値へ補正する。
     */
    suspend fun move(
        uuid: UUID,
        amount: Double,
        direction: VaultMoveDirection,
        note: String,
        displayNote: String,
    ): Result<VaultMoveResponse> {
        val amt = normalizeAmount(amount) ?: return Result.failure(IllegalArgumentException("金額が不正です。"))
        val session = requireResidentSession(uuid).getOrElse { return Result.failure(it) }
        val opId = if (direction == VaultMoveDirection.VaultToBank) {
            val id = UUID.randomUUID().toString()
            if (!cache.tryReserve(uuid, id, amt)) {
                return Result.failure(IllegalStateException("残高が不足しています。"))
            }
            id
        } else {
            null
        }
        val result = api.move(
            VaultMoveRequest(uuid.toString(), amt.toDouble(), direction, plugin.name, note, displayNote, serverName, session)
        )
        result.onSuccess { res ->
            if (opId != null) {
                cache.confirmOperation(uuid, opId, res.vaultBalance.toLong(), res.vaultVersion)
            } else {
                cache.reconcile(uuid, res.vaultBalance.toLong(), res.vaultVersion)
            }
        }.onFailure {
            if (opId != null) cache.rollbackOperation(uuid, opId)
            signalConnectionLossIfTransport(it)
        }
        return result
    }

    /**
     * 管理用: 電子マネー残高を操作し、確定結果を返す（/meco give|take|set）。
     * すべて対象が自サーバー在席（claim 保持）の場合のみ実行できる（VaultProvider 5.4）。
     * オフライン・別サーバー在席プレイヤーへの付与・回収・補償は既存 Bank 機能を使う。
     */
    suspend fun adminOperate(
        uuid: UUID,
        op: VaultAdminOp,
        amount: Double,
        note: String,
        displayNote: String,
    ): Result<VaultBalanceResponse> {
        return when (op) {
            VaultAdminOp.GIVE -> depositConfirmed(uuid, amount, note, displayNote, VaultSources.ADMIN)
            VaultAdminOp.TAKE -> withdrawConfirmed(uuid, amount, note, displayNote, VaultSources.ADMIN)
            VaultAdminOp.SET -> setConfirmed(uuid, amount, note, displayNote)
        }
    }

    private suspend fun setConfirmed(uuid: UUID, amount: Double, note: String, displayNote: String): Result<VaultBalanceResponse> {
        // SET は 0 円への設定を許容する（負数・非有限は拒否）。
        if (!amount.isFinite() || amount < 0.0) return Result.failure(IllegalArgumentException("金額が不正です。"))
        val amt = amount.toLong()
        if (amt > MAX_SAFE_AMOUNT) return Result.failure(IllegalArgumentException("金額が不正です。"))
        val session = requireResidentSession(uuid).getOrElse { return Result.failure(it) }
        val result = api.set(
            VaultSetRequest(uuid.toString(), amt.toDouble(), plugin.name, note, displayNote, serverName, session)
        )
        result.onSuccess { res -> cache.reconcile(uuid, res.balance.toLong(), res.version) }
            .onFailure { signalConnectionLossIfTransport(it) }
        return result
    }

    // === ライフサイクル（VaultLifecycleListener / VaultSyncClient から呼ばれる） ===

    /**
     * join / 再接続時の session claim。成功で台帳を READY にし、残高上限の権威値を保持する。
     * 失敗時は台帳が LOADING のまま残り、全書き込みが fail-closed で拒否される（再同期で再試行）。
     */
    suspend fun claim(uuid: UUID, playerName: String) {
        cache.markLoading(uuid)
        api.claimSession(VaultSessionClaimRequest(uuid.toString(), playerName, serverName)).onSuccess { res ->
            maxBalance = res.maxBalance.toLong()
            if (plugin.server.getPlayer(uuid) != null) {
                cache.beginSession(uuid, res.sessionId, res.balance.toLong(), res.version)
            } else {
                // claim 完了前に退出した: session を返して破棄する。
                cache.evict(uuid)
                api.releaseSession(VaultSessionReleaseRequest(uuid.toString(), res.sessionId, serverName))
            }
        }.onFailure {
            plugin.logger.warning(
                "session claim に失敗しました uuid=$uuid: ${it.message} " +
                    "詳細=対象の電子マネー操作は在席確認できるまで拒否される（定期再同期で再試行）"
            )
            signalConnectionLossIfTransport(it)
        }
    }

    /**
     * quit: 新規書き込みを止め、対象の送信キューをドレインしてから release・退避する（VaultProvider 5.8）。
     * ドレインできない場合は退避のみ行い、session は送信再開に備えて保持する。
     */
    fun beginQuit(uuid: UUID) {
        if (!cache.contains(uuid)) return
        cache.markDraining(uuid)
        scope.launch {
            val deadline = System.currentTimeMillis() + QUIT_DRAIN_TIMEOUT_MS
            while (queue.hasOpsFor(uuid) && System.currentTimeMillis() < deadline && coroutineContext.isActive) {
                delay(100)
            }
            if (queue.hasOpsFor(uuid)) {
                queue.persistUnsent()
                plugin.logger.warning(
                    "quit 時に未送信のキュー操作が残っています uuid=$uuid " +
                        "詳細=退避済み。session を保持したまま送信を継続する（別サーバー claim で失効した場合は隔離される）"
                )
                return@launch
            }
            val sessionId = cache.sessionIdOf(uuid)
            cache.evict(uuid)
            if (sessionId != null) {
                api.releaseSession(VaultSessionReleaseRequest(uuid.toString(), sessionId, serverName))
                    .onFailure { plugin.logger.fine("session release に失敗しました uuid=$uuid: ${it.message}") }
            }
        }
    }

    /** 別サーバーの後勝ち claim による session 失効通知（VaultProvider 7.4）。 */
    fun onSessionRevoked(uuid: UUID, sessionId: String?) {
        val current = cache.sessionIdOf(uuid) ?: return
        if (sessionId != null && current != sessionId) return
        cache.evict(uuid)
        if (queue.hasOpsFor(uuid)) {
            plugin.logger.severe(
                "session 失効時に未送信のキュー操作が残っています uuid=$uuid " +
                    "詳細=送信不能のため vault-queue/failed へ隔離される。手動確認が必要"
            )
        }
        plugin.logger.info("別サーバーの claim により session が失効しました uuid=$uuid")
    }

    // === 内部 ===

    /** 対象が自サーバー在席（READY + session 保持）であることを要求する共通ガード。 */
    private fun requireResidentSession(uuid: UUID): Result<String> {
        if (!isReady()) return Result.failure(IllegalStateException("電子マネーに接続できません。"))
        val snap = cache.snapshot(uuid)
        val session = snap?.sessionId
        if (snap == null || snap.status != VaultCache.Status.READY || session == null) {
            return Result.failure(IllegalStateException("対象がこのサーバーに在席していないため電子マネーを操作できません。"))
        }
        return Result.success(session)
    }

    private fun newOp(uuid: UUID, type: VaultWriteQueue.Type, amount: Long, note: String, displayNote: String) =
        VaultWriteQueue.Op(
            operationId = UUID.randomUUID().toString(),
            uuid = uuid.toString(),
            type = type,
            amount = amount,
            note = note,
            displayNote = displayNote,
            createdAtMillis = System.currentTimeMillis(),
        )

    /**
     * 送信キューのワーカー。登録順に 1 件ずつ送り、確定応答で台帳を収束させる。
     * - トランスポート失敗/5xx: 結果不明。fail-closed + 退避し、同一 operationId で再送する（冪等）。
     * - 4xx: サービスが拒否（DB 未コミット確定）。予約を取り消し、対象を CONFLICT にして隔離する（VaultProvider 5.7）。
     */
    private suspend fun queueWorkerLoop() {
        var backoffMs = 500L
        while (coroutineContext.isActive) {
            val op = queue.peek()
            if (op == null) {
                delay(200)
                continue
            }
            if (!isReady()) {
                delay(1_000)
                continue
            }
            val uuid = op.playerUuid()
            val sessionId = cache.sessionIdOf(uuid)
            if (sessionId == null) {
                // 対象の claim が無い（失効・退出済みで別サーバーへ移動等）。送信できないため隔離する。
                cache.rollbackOperation(uuid, op.operationId)
                queue.markFailed(op, "session が無く送信できない")
                continue
            }
            val result = when (op.type) {
                VaultWriteQueue.Type.DEPOSIT -> api.deposit(
                    VaultDepositRequest(
                        op.uuid, op.amount.toDouble(), plugin.name, op.note, op.displayNote,
                        serverName, sessionId, op.operationId, VaultSources.PROVIDER,
                    )
                )
                VaultWriteQueue.Type.WITHDRAW -> api.withdraw(
                    VaultWithdrawRequest(
                        op.uuid, op.amount.toDouble(), plugin.name, op.note, op.displayNote,
                        serverName, sessionId, op.operationId, VaultSources.PROVIDER,
                    )
                )
            }
            result.onSuccess { res ->
                when (op.type) {
                    VaultWriteQueue.Type.DEPOSIT -> cache.reconcile(uuid, res.balance.toLong(), res.version)
                    VaultWriteQueue.Type.WITHDRAW -> cache.confirmOperation(uuid, op.operationId, res.balance.toLong(), res.version)
                }
                queue.complete(op)
                backoffMs = 500L
            }.onFailure { ex ->
                if (isDefinitelyRejected(ex)) {
                    // Provider は SUCCESS 済みのため補償できない重大不整合。CONFLICT + 隔離 + 権威再同期。
                    if (op.type == VaultWriteQueue.Type.WITHDRAW) cache.rollbackOperation(uuid, op.operationId)
                    cache.markConflict(uuid)
                    queue.markFailed(op, ex.message ?: "業務失敗")
                    reconcileFromAuthority(uuid)
                } else {
                    signalConnectionLossIfTransport(ex)
                    queue.persistUnsent()
                    plugin.logger.warning(
                        "Provider送信キューの送信に失敗しました（同一operationIdで再送待ち） " +
                            "operationId=${op.operationId} uuid=${op.uuid}: ${ex.message}"
                    )
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(15_000L)
                }
            }
        }
    }

    /**
     * 定期再同期（自己修復。VaultProvider 7.4）。
     * - claim が無いまま在席しているプレイヤーへ claim を再試行する。
     * - READY エントリの権威残高を再取得し、乖離（version 前進）を検知したら補正して warning を残す。
     */
    private suspend fun resyncLoop(intervalSeconds: Long) {
        val intervalMs = (intervalSeconds.coerceAtLeast(10L)) * 1000L
        while (coroutineContext.isActive) {
            delay(intervalMs)
            if (!connected) continue
            val online = onlinePlayersOnMain()
            for ((uuid, name) in online) {
                when (cache.statusOf(uuid)) {
                    null, VaultCache.Status.LOADING -> claim(uuid, name)
                    VaultCache.Status.READY -> resyncBalance(uuid)
                    else -> {}
                }
            }
        }
    }

    private suspend fun resyncBalance(uuid: UUID) {
        api.getBalance(uuid).onSuccess { res ->
            if (cache.applyAuthoritative(uuid, res.balance.toLong(), res.version)) {
                plugin.logger.warning(
                    "定期再同期で残高乖離を検知し補正しました uuid=$uuid balance=${res.balance} version=${res.version} " +
                        "詳細=単一書き込み者の下では通常発生しない（手動DB変更・旧session競合等の可能性）"
                )
            }
        }.onFailure { signalConnectionLossIfTransport(it) }
    }

    /** 権威残高を再取得して台帳を補正する（CONFLICT 検知後の表示矯正）。 */
    private fun reconcileFromAuthority(uuid: UUID) {
        scope.launch {
            api.getBalance(uuid).onSuccess { res ->
                cache.reconcile(uuid, res.balance.toLong(), res.version)
            }.onFailure {
                plugin.logger.severe("権威残高の再取得に失敗しました uuid=$uuid: ${it.message}")
                signalConnectionLossIfTransport(it)
            }
        }
    }

    private suspend fun onlinePlayersOnMain(): List<Pair<UUID, String>> {
        if (plugin.server.isPrimaryThread) {
            return plugin.server.onlinePlayers.map { it.uniqueId to it.name }
        }
        val deferred = CompletableDeferred<List<Pair<UUID, String>>>()
        plugin.server.scheduler.runTask(plugin, Runnable {
            deferred.complete(plugin.server.onlinePlayers.map { it.uniqueId to it.name })
        })
        return deferred.await()
    }

    /**
     * 金額の正規化（VaultProvider 12）: 有限かつ正数を小数切り捨てで整数円にする。
     * NaN / Infinity / 0 以下 / 切り捨て後 0 / IEEE 安全上限超えは null（拒否）。
     */
    private fun normalizeAmount(amount: Double): Long? {
        if (!amount.isFinite()) return null
        if (amount <= 0.0) return null
        val truncated = amount.toLong()
        if (truncated <= 0L || truncated > MAX_SAFE_AMOUNT) return null
        return truncated
    }

    private fun healthMessage(health: WriteHealth): String = when (health) {
        WriteHealth.DOWN -> "電子マネーに接続できないため操作できません。後でもう一度お試しください。"
        WriteHealth.DEGRADED -> "電子マネーが混雑しているため操作できません。後でもう一度お試しください。"
        WriteHealth.DRAINING -> "電子マネーは停止処理中のため操作できません。"
        WriteHealth.WRITE_READY -> ""
    }

    private fun success(amount: Long, newBalance: Double): EconomyResponse =
        EconomyResponse(amount.toDouble(), newBalance, EconomyResponse.ResponseType.SUCCESS, "")

    private fun failure(message: String): EconomyResponse =
        EconomyResponse(0.0, 0.0, EconomyResponse.ResponseType.FAILURE, message)
}
