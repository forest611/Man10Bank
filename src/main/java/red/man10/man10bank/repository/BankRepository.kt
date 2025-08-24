package red.man10.man10bank.repository

import org.ktorm.database.Database
import org.ktorm.dsl.*
import red.man10.man10bank.db.tables.MoneyLog
import red.man10.man10bank.db.tables.UserBank
import java.math.BigDecimal
import java.math.RoundingMode

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

    fun increaseBalance(uuid: String, player: String, amount: BigDecimal, log: LogParams): BigDecimal {
        require(amount > BigDecimal.ZERO) { "amount は 0 以上である必要があります" }
        return adjustBalance(uuid, player, amount, log)
    }

    fun decreaseBalance(uuid: String, player: String, amount: BigDecimal, log: LogParams): BigDecimal {
        require(amount > BigDecimal.ZERO) { "amount は 0 以上である必要があります" }
        return adjustBalance(uuid, player, amount.negate(), log)
    }

    fun transfer(
        fromUuid: String,
        fromPlayer: String,
        toUuid: String,
        toPlayer: String,
        amount: BigDecimal,
    ): BigDecimal {
        require(amount.signum() > 0) { "amount は正の数である必要があります" }
        return db.useTransaction {
            val fromCurrent = getBalanceByUuid(fromUuid) ?: BigDecimal.ZERO
            require(fromCurrent >= BigDecimal.ZERO) { "送金元の残高が不正です: $fromCurrent" }
            val toCurrent = getBalanceByUuid(toUuid) ?: BigDecimal.ZERO
            val fromNext = fromCurrent.subtract(amount)
            val toNext = toCurrent.add(amount)

            setBalance(fromUuid, fromPlayer, fromNext)
            setBalance(toUuid, toPlayer, toNext)

            logMoney(
                uuid = fromUuid,
                player = fromPlayer,
                amount = amount.negate(),
                deposit = false,
                pluginName = "Man10Bank",
                note = "RemittanceTo'$toPlayer",
                displayNote = "${toPlayer}へ送金",
                server = serverName,
            )
            logMoney(
                uuid = toUuid,
                player = toPlayer,
                amount = amount,
                deposit = true,
                pluginName = "Man10Bank",
                note = "RemittanceFrom$$fromPlayer",
                displayNote = "${fromPlayer}からの送金",
                server = serverName,
            )
            fromNext
        }
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

    private fun setBalance(uuid: String, player: String, amount: BigDecimal) {
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
