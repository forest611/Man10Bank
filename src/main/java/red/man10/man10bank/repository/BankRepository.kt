package red.man10.man10bank.repository

import org.bukkit.Bukkit
import org.bukkit.plugin.java.JavaPlugin
import org.ktorm.database.Database
import org.ktorm.dsl.*
import red.man10.man10bank.db.tables.MoneyLog
import red.man10.man10bank.db.tables.UserBank
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 残高と入出金ログに関する最小限のリポジトリ。
 * - メインスレッドでは呼ばず、非同期で実行してください。
 */
class BankRepository(private val db: Database, private val serverName: String) {

    data class LogParams(
        val pluginName: String,
        val note: String,
        val displayNote: String,
    )

    fun getBalanceByUuid(uuid: String): BigDecimal? {
        return db.from(UserBank)
            .select(UserBank.balance)
            .where { UserBank.uuid eq uuid }
            .map { row -> row[UserBank.balance] }
            .firstOrNull()
    }

    fun setBalance(uuid: String, player: String, amount: BigDecimal) {
        val normalized = amount.setScale(0, RoundingMode.DOWN)
        val updated = db.update(UserBank) {
            set(it.balance, normalized)
            where { it.uuid eq uuid }
        }
        if (updated == 0) {
            db.insert(UserBank) {
                set(it.player, player)
                set(it.uuid, uuid)
                set(it.balance, normalized)
            }
        }
    }

    fun increaseBalance(uuid: String, player: String, amount: BigDecimal, log: LogParams): BigDecimal {
        require(amount > BigDecimal.ZERO) { "amount は 0 以上である必要があります" }
        return adjustBalance(uuid, player, amount, log)
    }

    fun decreaseBalance(uuid: String, player: String, amount: BigDecimal, log: LogParams): BigDecimal {
        require(amount > BigDecimal.ZERO) { "amount は 0 以上である必要があります" }
        return adjustBalance(uuid, player, amount.negate(), log)
    }

    private fun adjustBalance(uuid: String, player: String, delta: BigDecimal, log: LogParams): BigDecimal {
        return db.useTransaction {
            val current = getBalanceByUuid(uuid) ?: BigDecimal.ZERO
            val next = current.add(delta).setScale(0, RoundingMode.DOWN)
            setBalance(uuid, player, next)
            val deposit = delta.signum() >= 0
            logMoney(
                uuid = uuid,
                player = player,
                amount = delta.setScale(0, RoundingMode.DOWN),
                deposit = deposit,
                pluginName = log.pluginName,
                note = log.note,
                displayNote = log.displayNote,
                server = serverName,
            )
            next
        }
    }

    private fun logMoney(
        uuid: String,
        player: String,
        amount: BigDecimal,
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
