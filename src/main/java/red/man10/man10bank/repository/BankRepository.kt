package red.man10.man10bank.repository

import org.ktorm.database.Database
import org.ktorm.dsl.*
import red.man10.man10bank.db.tables.MoneyLog
import red.man10.man10bank.db.tables.UserBank

/**
 * MoneyLog の付帯情報をまとめるパラメータ。
 */
data class LogParams(
    val pluginName: String,
    val note: String,
    val displayNote: String,
    val server: String,
)

/**
 * 残高と入出金ログに関する最小限のリポジトリ。
 * - メインスレッドでは呼ばず、非同期で実行してください。
 */
class BankRepository(private val db: Database) {

    fun getBalanceByUuid(uuid: String): Double? {
        return db.from(UserBank)
            .select(UserBank.balance)
            .where { UserBank.uuid eq uuid }
            .map { row -> row[UserBank.balance] }
            .firstOrNull()
    }

    fun setBalance(uuid: String, player: String, amount: Double) {
        val updated = db.update(UserBank) {
            set(it.balance, amount)
            where { it.uuid eq uuid }
        }
        if (updated == 0) {
            db.insert(UserBank) {
                set(it.player, player)
                set(it.uuid, uuid)
                set(it.balance, amount)
            }
        }
    }

    fun adjustBalance(uuid: String, player: String, delta: Double, log: LogParams): Double {
        return db.useTransaction {
            val current = getBalanceByUuid(uuid) ?: 0.0
            val next = current + delta
            setBalance(uuid, player, next)
            // ログを必ず記録（delta の符号で増減を判定）
            val deposit = delta >= 0.0
            val amountForLog = delta
            logMoney(
                uuid = uuid,
                player = player,
                amount = amountForLog,
                deposit = deposit,
                pluginName = log.pluginName,
                note = log.note,
                displayNote = log.displayNote,
                server = log.server,
            )
            next
        }
    }

    fun increaseBalance(uuid: String, player: String, amount: Double, log: LogParams): Double {
        require(amount >= 0.0) { "amount は 0 以上である必要があります" }
        return adjustBalance(uuid, player, amount, log)
    }

    fun decreaseBalance(uuid: String, player: String, amount: Double, log: LogParams): Double {
        require(amount >= 0.0) { "amount は 0 以上である必要があります" }
        return adjustBalance(uuid, player, -amount, log)
    }

    private fun logMoney(
        uuid: String,
        player: String,
        amount: Double,
        deposit: Boolean,
        pluginName: String,
        note: String,
        displayNote: String,
        server: String,
    ) {
        db.insert(MoneyLog) {
            set(it.player, player)
            set(it.uuid, uuid)
            set(it.pluginName, pluginName)
            set(it.amount, amount)
            set(it.note, note)
            set(it.displayNote, displayNote)
            set(it.server, server)
            set(it.deposit, deposit)
            // date は DB 側の default now() を利用
        }
    }
}
