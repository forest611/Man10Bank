package red.man10.man10bank.db.tables

import org.ktorm.schema.Table
import org.ktorm.schema.double
import org.ktorm.schema.int
import org.ktorm.schema.varchar

/**
 * user_bank テーブル
 */
object UserBank : Table<Nothing>("user_bank") {
    val id = int("id").primaryKey()
    val player = varchar("player")
    val uuid = varchar("uuid")
    val balance = double("balance")
}
