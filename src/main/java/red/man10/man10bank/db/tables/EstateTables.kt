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
    val vault = double("vault")
    val bank = double("bank")
    val cash = double("cash")
    val estate = double("estate")
    val loan = double("loan")
    val shop = double("shop")
    val crypto = double("crypto")
    val total = double("total")
}

object EstateHistoryTbl : Table<Nothing>("estate_history_tbl") {
    val id = int("id").primaryKey()
    val uuid = varchar("uuid")
    val date = datetime("date")
    val player = varchar("player")
    val vault = double("vault")
    val bank = double("bank")
    val cash = double("cash")
    val estate = double("estate")
    val loan = double("loan")
    val shop = double("shop")
    val crypto = double("crypto")
    val total = double("total")
}

object ServerEstateHistory : Table<Nothing>("server_estate_history") {
    val id = int("id").primaryKey()
    val vault = double("vault")
    val bank = double("bank")
    val cash = double("cash")
    val estate = double("estate")
    val loan = double("loan")
    val crypto = double("crypto")
    val shop = double("shop")
    val total = double("total")
    val year = int("year")
    val month = int("month")
    val day = int("day")
    val hour = int("hour")
    val date = datetime("date")
}
