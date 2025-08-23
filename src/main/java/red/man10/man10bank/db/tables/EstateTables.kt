package red.man10.man10bank.db.tables

import org.ktorm.schema.*

/**
 * estate_tbl / estate_history_tbl / server_estate_history テーブル
 */
object EstateTbl : Table<Nothing>("estate_tbl") {
    val id = int("id").primaryKey()
    val uuid = varchar("uuid")
    val date = datetime("date")
    val player = varchar("player")
    val vault = decimal("vault")
    val bank = decimal("bank")
    val cash = decimal("cash")
    val estate = decimal("estate")
    val loan = decimal("loan")
    val shop = decimal("shop")
    val crypto = decimal("crypto")
    val total = decimal("total")
}

object EstateHistoryTbl : Table<Nothing>("estate_history_tbl") {
    val id = int("id").primaryKey()
    val uuid = varchar("uuid")
    val date = datetime("date")
    val player = varchar("player")
    val vault = decimal("vault")
    val bank = decimal("bank")
    val cash = decimal("cash")
    val estate = decimal("estate")
    val loan = decimal("loan")
    val shop = decimal("shop")
    val crypto = decimal("crypto")
    val total = decimal("total")
}

object ServerEstateHistory : Table<Nothing>("server_estate_history") {
    val id = int("id").primaryKey()
    val vault = decimal("vault")
    val bank = decimal("bank")
    val cash = decimal("cash")
    val estate = decimal("estate")
    val loan = decimal("loan")
    val crypto = decimal("crypto")
    val shop = decimal("shop")
    val total = decimal("total")
    val year = int("year")
    val month = int("month")
    val day = int("day")
    val hour = int("hour")
    val date = datetime("date")
}
