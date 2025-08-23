package red.man10.man10bank.db.tables

import org.ktorm.schema.*

/**
 * atm_log テーブル
 */
object AtmLog : Table<Nothing>("atm_log") {
    val id = int("id").primaryKey()
    val player = varchar("player")
    val uuid = varchar("uuid")
    val amount = double("amount")
    val deposit = boolean("deposit")
    val date = datetime("date")
}
