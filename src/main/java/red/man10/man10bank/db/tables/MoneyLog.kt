package red.man10.man10bank.db.tables

import org.ktorm.schema.*

object MoneyLog : Table<Nothing>("money_log") {
    val id = int("id").primaryKey()
    val player = varchar("player")
    val uuid = varchar("uuid")
    val pluginName = varchar("plugin_name")
    val amount = decimal("amount")
    val note = varchar("note")
    val displayNote = varchar("display_note")
    val server = varchar("server")
    val deposit = boolean("deposit")
    val date = datetime("date")
}
