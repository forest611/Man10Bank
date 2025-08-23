package red.man10.man10bank.bank.service

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.ktorm.database.Database
import red.man10.man10bank.repository.BankRepository
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
            val uuid: String,
            val player: String,
            val amount: Double,
            val result: CompletableDeferred<BankResult>
        ) : Op()

        data class Withdraw(
            val uuid: String,
            val player: String,
            val amount: Double,
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

    suspend fun getBalance(uuid: String): Double? = withContext(dispatcher) {
        repository.getBalanceByUuid(uuid)
    }

    suspend fun deposit(uuid: String, player: String, amount: Double): BankResult {
        val deferred = CompletableDeferred<BankResult>()
        queue.send(Op.Deposit(uuid, player, amount, deferred))
        return deferred.await()
    }

    suspend fun withdraw(uuid: String, player: String, amount: Double): BankResult {
        val deferred = CompletableDeferred<BankResult>()
        queue.send(Op.Withdraw(uuid, player, amount, deferred))
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
        val (uuid, player, amount, result) = op
        try {
            if (amount <= 0.0) {
                result.complete(BankResult(false, "入金額は正の数である必要があります"))
                return
            }
            val next = repository.addBalance(uuid, player, amount)
            repository.logMoney(
                uuid = uuid,
                player = player,
                amount = amount,
                deposit = true,
                pluginName = "Man10Bank",
                note = "deposit",
                displayNote = "deposit",
                server = ""
            )
            result.complete(BankResult(true, "入金に成功しました", next))
        } catch (t: Throwable) {
            result.complete(BankResult(false, "入金に失敗しました: ${t.message}"))
        }
    }

    private suspend fun handleWithdraw(op: Op.Withdraw) {
        val (uuid, player, amount, result) = op
        try {
            if (amount <= 0.0) {
                result.complete(BankResult(false, "出金額は正の数である必要があります"))
                return
            }
            val current = repository.getBalanceByUuid(uuid) ?: 0.0
            if (current < amount) {
                result.complete(BankResult(false, "残高が不足しています。現在残高: ${current}"))
                return
            }
            val next = repository.addBalance(uuid, player, -amount)
            repository.logMoney(
                uuid = uuid,
                player = player,
                amount = -amount,
                deposit = false,
                pluginName = "Man10Bank",
                note = "withdraw",
                displayNote = "withdraw",
                server = ""
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
            val afterFrom = repository.addBalance(fromUuid, fromPlayer, -amount)
            val afterTo = repository.addBalance(toUuid, toPlayer, amount)
            repository.logMoney(
                uuid = fromUuid,
                player = fromPlayer,
                amount = -amount,
                deposit = false,
                pluginName = "Man10Bank",
                note = "transfer to ${toPlayer}",
                displayNote = "transfer to ${toPlayer}",
                server = ""
            )
            repository.logMoney(
                uuid = toUuid,
                player = toPlayer,
                amount = amount,
                deposit = true,
                pluginName = "Man10Bank",
                note = "transfer from ${fromPlayer}",
                displayNote = "transfer from ${fromPlayer}",
                server = ""
            )
            // 返却は送金者側の新残高を優先
            result.complete(BankResult(true, "送金に成功しました", afterFrom))
        } catch (t: Throwable) {
            result.complete(BankResult(false, "送金に失敗しました: ${t.message}"))
        }
    }
}
