package red.man10.man10bank.bank.service

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.bukkit.Bukkit
import org.ktorm.database.Database
import red.man10.man10bank.repository.BankRepository
import red.man10.man10bank.repository.LogParams
import java.util.UUID
import java.util.concurrent.Executors

/**
 * Bank のサービス層。
 * - 依頼（入金/出金/送金）を単一ワーカーで順次処理し、発生順を担保する。
 * - DB I/O はこのワーカー内でのみ実行され、メインスレッドをブロックしない。
 */
class BankService(private val db: Database) {

    data class BankResult(
        val ok: Boolean,
        val message: String,
        val balance: Double? = null,
    )

    private val repository = BankRepository(db)

    // 単一スレッドディスパッチャ（順序保証のため）
    private val dispatcher = Executors.newSingleThreadExecutor { r ->
        Thread(r, "bank-service-worker").apply { isDaemon = true }
    }.asCoroutineDispatcher()

    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    // リクエストキュー（無制限）。必要に応じて容量制限を検討。
    private val queue = Channel<Op>(Channel.UNLIMITED)

    private sealed class Op {
        data class Deposit(
            val uuid: UUID,
            val amount: Double,
            val pluginName: String,
            val note: String,
            val displayNote: String?,
            val result: CompletableDeferred<BankResult>
        ) : Op()

        data class Withdraw(
            val uuid: UUID,
            val amount: Double,
            val pluginName: String,
            val note: String,
            val displayNote: String?,
            val result: CompletableDeferred<BankResult>
        ) : Op()

        data class Transfer(
            val fromUuid: String,
            val fromPlayer: String,
            val toUuid: String,
            val toPlayer: String,
            val amount: Double,
            val result: CompletableDeferred<BankResult>
        ) : Op()
    }

    init {
        // ワーカー起動：常に1件ずつ順次処理
        scope.launch {
            for (op in queue) {
                when (op) {
                    is Op.Deposit -> handleDeposit(op)
                    is Op.Withdraw -> handleWithdraw(op)
                    is Op.Transfer -> handleTransfer(op)
                }
            }
        }
    }

    suspend fun getBalance(uuid: UUID): Double? = withContext(dispatcher) {
        repository.getBalanceByUuid(uuid.toString())
    }

    suspend fun deposit(
        uuid: UUID,
        amount: Double,
        pluginName: String,
        note: String,
        displayNote: String?,
    ): BankResult {
        val deferred = CompletableDeferred<BankResult>()
        queue.send(Op.Deposit(uuid, amount, pluginName, note, displayNote, deferred))
        return deferred.await()
    }

    suspend fun withdraw(
        uuid: UUID,
        amount: Double,
        pluginName: String,
        note: String,
        displayNote: String?,
    ): BankResult {
        val deferred = CompletableDeferred<BankResult>()
        queue.send(Op.Withdraw(uuid, amount, pluginName, note, displayNote, deferred))
        return deferred.await()
    }

    suspend fun transfer(
        fromUuid: String,
        fromPlayer: String,
        toUuid: String,
        toPlayer: String,
        amount: Double,
    ): BankResult {
        val deferred = CompletableDeferred<BankResult>()
        queue.send(Op.Transfer(fromUuid, fromPlayer, toUuid, toPlayer, amount, deferred))
        return deferred.await()
    }

    fun shutdown() {
        scope.cancel()
        dispatcher.close()
    }

    private suspend fun handleDeposit(op: Op.Deposit) {
        val (uuid, amount, pluginName, note, displayNote, result) = op
        try {
            if (amount <= 0.0) {
                result.complete(BankResult(false, "入金額は正の数である必要があります"))
                return
            }
            val uuidStr = uuid.toString()
            val playerName = Bukkit.getOfflinePlayer(uuid).name ?: ""
            val next = repository.increaseBalance(
                uuid = uuidStr,
                player = playerName,
                amount = amount,
                log = LogParams(
                    pluginName = pluginName,
                    note = note,
                    displayNote = displayNote ?: note,
                    server = "",
                )
            )
            result.complete(BankResult(true, "入金に成功しました", next))
        } catch (t: Throwable) {
            result.complete(BankResult(false, "入金に失敗しました: ${t.message}"))
        }
    }

    private suspend fun handleWithdraw(op: Op.Withdraw) {
        val (uuid, amount, pluginName, note, displayNote, result) = op
        try {
            if (amount <= 0.0) {
                result.complete(BankResult(false, "出金額は正の数である必要があります"))
                return
            }
            val uuidStr = uuid.toString()
            val playerName = Bukkit.getOfflinePlayer(uuid).name ?: ""
            val current = repository.getBalanceByUuid(uuidStr) ?: 0.0
            if (current < amount) {
                result.complete(BankResult(false, "残高が不足しています。現在残高: ${current}"))
                return
            }
            val next = repository.decreaseBalance(
                uuid = uuidStr,
                player = playerName,
                amount = amount,
                log = LogParams(
                    pluginName = pluginName,
                    note = note,
                    displayNote = displayNote ?: note,
                    server = "",
                )
            )
            result.complete(BankResult(true, "出金に成功しました", next))
        } catch (t: Throwable) {
            result.complete(BankResult(false, "出金に失敗しました: ${t.message}"))
        }
    }

    private suspend fun handleTransfer(op: Op.Transfer) {
        val (fromUuid, fromPlayer, toUuid, toPlayer, amount, result) = op
        try {
            if (amount <= 0.0) {
                result.complete(BankResult(false, "送金額は正の数である必要があります"))
                return
            }
            val fromBalance = repository.getBalanceByUuid(fromUuid) ?: 0.0
            if (fromBalance < amount) {
                result.complete(BankResult(false, "残高が不足しています。現在残高: ${fromBalance}"))
                return
            }
            // 同一ワーカー内で順次処理されるため、この範囲は擬似的にアトミック
            val afterFrom = repository.decreaseBalance(
                uuid = fromUuid,
                player = fromPlayer,
                amount = amount,
                log = LogParams(
                    pluginName = "Man10Bank",
                    note = "RemittanceTo${toPlayer}",
                    displayNote = "${toPlayer}への送金",
                    server = "",
                )
            )
            val afterTo = repository.increaseBalance(
                uuid = toUuid,
                player = toPlayer,
                amount = amount,
                log = LogParams(
                    pluginName = "Man10Bank",
                    note = "RemittanceFrom${fromPlayer}",
                    displayNote = "${fromPlayer}からの送金",
                    server = "",
                )
            )
            // 返却は送金者側の新残高を優先
            result.complete(BankResult(true, "送金に成功しました", afterFrom))
        } catch (t: Throwable) {
            result.complete(BankResult(false, "送金に失敗しました: ${t.message}"))
        }
    }
}
