package red.man10.man10bank.db.tables

import org.ktorm.schema.*

object UserBank : Table<Nothing>("user_bank") {
    val id = int("id").primaryKey()
    val player = varchar("player")
    val uuid = varchar("uuid")
    val balance = decimal("balance")
}
