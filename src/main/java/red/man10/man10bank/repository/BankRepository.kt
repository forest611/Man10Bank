package red.man10.man10bank.repository

import org.ktorm.database.Database
import org.ktorm.dsl.*
import red.man10.man10bank.db.tables.MoneyLog
import red.man10.man10bank.db.tables.UserBank

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

    fun addBalance(uuid: String, player: String, delta: Double): Double {
        return db.useTransaction { tx ->
            val current = getBalanceByUuid(uuid) ?: 0.0
            val next = current + delta
            setBalance(uuid, player, next)
            next
        }
    }

    fun logMoney(
        uuid: String,
        player: String,
        amount: Double,
        deposit: Boolean,
        pluginName: String? = null,
        note: String? = null,
        displayNote: String? = null,
        server: String? = null,
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
