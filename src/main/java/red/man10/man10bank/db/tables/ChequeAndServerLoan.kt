package red.man10.man10bank.db.tables

import org.ktorm.schema.*

/**
 * cheque_tbl / server_loan_tbl テーブル
 */
object ChequeTbl : Table<Nothing>("cheque_tbl") {
    val id = int("id").primaryKey()
    val player = varchar("player")
    val uuid = varchar("uuid")
    val amount = double("amount")
    val note = varchar("note")
    val date = datetime("date")
    val useDate = datetime("use_date")
    val usePlayer = varchar("use_player")
    val used = int("used") // TINYINT を int として扱い、必要に応じて 0/1 で管理
}

object ServerLoanTbl : Table<Nothing>("server_loan_tbl") {
    val id = int("id").primaryKey()
    val player = varchar("player")
    val uuid = varchar("uuid")
    val borrowDate = datetime("borrow_date")
    val lastPayDate = datetime("last_pay_date")
    val borrowAmount = double("borrow_amount")
    val paymentAmount = double("payment_amount")
    val failedPayment = int("failed_payment")
    val stopInterest = int("stop_interest") // TINYINT を int として扱う
}
