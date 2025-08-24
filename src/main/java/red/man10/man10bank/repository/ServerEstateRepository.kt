package red.man10.man10bank.repository

import org.ktorm.database.Database
import org.ktorm.dsl.*
import red.man10.man10bank.db.tables.ServerEstateHistory
import java.math.BigDecimal
import java.time.ZonedDateTime

class ServerEstateRepository(private val db: Database) {

    data class ServerEstateParams(
        val vault: BigDecimal,
        val bank: BigDecimal,
        val cash: BigDecimal,
        val estate: BigDecimal,
        val loan: BigDecimal,
        val shop: BigDecimal,
        val crypto: BigDecimal,
    ){
        fun total(): BigDecimal {
            return vault + bank + cash + estate + shop + crypto - loan
        }

        override fun equals(other: Any?): Boolean {
            if (other !is ServerEstateParams) return false
            return  vault.compareTo(other.vault) == 0 &&
                    bank.compareTo(other.bank) == 0 &&
                    cash.compareTo(other.cash) == 0 &&
                    estate.compareTo(other.estate) == 0 &&
                    loan.compareTo(other.loan) == 0 &&
                    shop.compareTo(other.shop) == 0 &&
                    crypto.compareTo(other.crypto) == 0
        }

        override fun hashCode(): Int =
            listOf(
                vault.stripTrailingZeros().toPlainString(),
                bank.stripTrailingZeros().toPlainString(),
                cash.stripTrailingZeros().toPlainString(),
                estate.stripTrailingZeros().toPlainString(),
                loan.stripTrailingZeros().toPlainString(),
                shop.stripTrailingZeros().toPlainString(),
                crypto.stripTrailingZeros().toPlainString()
            ).hashCode()
    }

    fun addEstateHistory(params: ServerEstateParams): Boolean {
        val now = ZonedDateTime.now()
        if (isRecorded(now)) {
            return true
        }
        val inserted = db.insert(ServerEstateHistory) {
            set(it.vault, params.vault)
            set(it.bank, params.bank)
            set(it.cash, params.cash)
            set(it.estate, params.estate)
            set(it.loan, params.loan)
            set(it.shop, params.shop)
            set(it.crypto, params.crypto)
            set(it.total, params.total())
            set(it.year, now.year)
            set(it.month, now.monthValue)
            set(it.day, now.dayOfMonth)
            set(it.hour, now.hour)
            set(it.date, now.toLocalDateTime())
        }
        return inserted == 1
    }

    private fun isRecorded(time: ZonedDateTime): Boolean {
        return db.from(ServerEstateHistory)
            .select()
            .where {
                (ServerEstateHistory.year eq time.year) and
                        (ServerEstateHistory.month eq time.monthValue) and
                        (ServerEstateHistory.day eq time.dayOfMonth) and
                        (ServerEstateHistory.hour eq time.hour)
            }.totalRecordsInAllPages >= 1
    }
}