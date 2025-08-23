package red.man10.man10bank.db.tables

import org.ktorm.schema.*

/**
 * loan_table テーブル
 */
object LoanTable : Table<Nothing>("loan_table") {
    val id = int("id").primaryKey()
    val lendPlayer = varchar("lend_player")
    val lendUuid = varchar("lend_uuid")
    val borrowPlayer = varchar("borrow_player")
    val borrowUuid = varchar("borrow_uuid")
    val borrowDate = datetime("borrow_date")
    val paybackDate = datetime("payback_date")
    val amount = double("amount")
    val collateralItem = text("collateral_item")
}
