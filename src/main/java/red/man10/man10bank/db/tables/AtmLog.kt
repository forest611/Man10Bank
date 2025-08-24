package red.man10.man10bank.db.tables

import org.ktorm.schema.*

object AtmLog : Table<Nothing>("atm_log") {
    val id = int("id").primaryKey()
    val player = varchar("player")
    val uuid = varchar("uuid")
    val amount = decimal("amount")
    val deposit = boolean("deposit")
    val date = datetime("date")
}
