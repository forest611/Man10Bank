package red.man10.man10bank.loan.repository

import red.man10.man10bank.Bank
import red.man10.man10bank.Man10Bank.Companion.plugin
import red.man10.man10bank.MySQLManager
import java.util.UUID
import kotlin.math.floor

object ServerLoanRepository {

    fun fetchLoan(uuid: UUID): LoanRecord? {
        val mysql = MySQLManager(plugin, "Man10ServerLoan")
        val rs = mysql.query("select * from server_loan_tbl where uuid='${uuid}'") ?: return null
        val record = if (rs.next()) {
            LoanRecord(
                uuid,
                rs.getDouble("borrow_amount"),
                rs.getDouble("payment_amount"),
                rs.getTimestamp("last_pay_date"),
                rs.getInt("failed_payment")
            )
        } else null
        rs.close()
        mysql.close()
        return record
    }

    fun fetchActiveLoans(): List<LoanRecord> {
        val list = mutableListOf<LoanRecord>()
        val mysql = MySQLManager(plugin, "Man10ServerLoan")
        val rs = mysql.query("select * from server_loan_tbl where borrow_amount != 0") ?: return list
        while (rs.next()) {
            list += LoanRecord(
                UUID.fromString(rs.getString("uuid")),
                rs.getDouble("borrow_amount"),
                rs.getDouble("payment_amount"),
                rs.getTimestamp("last_pay_date"),
                rs.getInt("failed_payment")
            )
        }
        rs.close()
        mysql.close()
        return list
    }

    fun insertLoan(playerName: String, uuid: UUID, amount: Double, paymentAmount: Double): Boolean {
        val mysql = MySQLManager(plugin, "Man10ServerLoan")
        val query = "INSERT INTO server_loan_tbl (player, uuid, borrow_date, last_pay_date, borrow_amount, payment_amount) " +
            "VALUES ('$playerName', '$uuid', DEFAULT, DEFAULT, $amount, $paymentAmount)"
        val ret = mysql.execute(query)
        mysql.close()
        return ret
    }

    fun updateLoan(uuid: UUID, borrowAmount: Double, paymentAmount: Double): Boolean {
        val mysql = MySQLManager(plugin, "Man10ServerLoan")
        val query = "UPDATE server_loan_tbl SET borrow_amount=$borrowAmount, borrow_date=now(), payment_amount=$paymentAmount WHERE uuid = '$uuid'"
        val ret = mysql.execute(query)
        mysql.close()
        return ret
    }

    fun setPaymentAmount(uuid: UUID, amount: Double) {
        val mysql = MySQLManager(plugin, "Man10ServerLoan")
        mysql.execute("UPDATE server_loan_tbl SET payment_amount=$amount where uuid='$uuid'")
        mysql.close()
    }

    fun setBorrowAmountZero(uuid: UUID) {
        val mysql = MySQLManager(plugin, "Man10ServerLoan")
        mysql.execute("UPDATE server_loan_tbl set borrow_amount=0,last_pay_date=now() where uuid='$uuid'")
        mysql.close()
    }

    fun updateAfterSuccess(uuid: UUID, finalAmount: Double) {
        val mysql = MySQLManager(plugin, "Man10ServerLoan")
        mysql.execute("UPDATE server_loan_tbl set borrow_amount=${floor(finalAmount)},last_pay_date=now() where uuid='$uuid'")
        mysql.close()
    }

    fun updateAfterFailure(uuid: UUID, newAmount: Double) {
        val mysql = MySQLManager(plugin, "Man10ServerLoan")
        mysql.execute("UPDATE server_loan_tbl set borrow_amount=${floor(newAmount)},last_pay_date=now(),failed_payment=failed_payment+1 where uuid='$uuid'")
        mysql.close()
    }

    fun addLastPayTime(uuidOrAll: String, hour: Int): Int {
        val mysql = MySQLManager(plugin, "Man10ServerLoan")
        if (uuidOrAll == "all") {
            mysql.execute("update server_loan_tbl set last_pay_date=DATE_ADD(last_pay_date,INTERVAL $hour HOUR)")
            return 0
        }
        val uuid = Bank.getUUID(uuidOrAll) ?: return 1
        mysql.execute("update server_loan_tbl set last_pay_date=DATE_ADD(last_pay_date,INTERVAL $hour HOUR) Where uuid='${uuid}'")
        return 0
    }

    fun setLastPayTime(uuidOrAll: String, time: Long): Int {
        val mysql = MySQLManager(plugin, "Man10ServerLoan")
        if (uuidOrAll == "all") {
            mysql.execute("update server_loan_tbl set last_pay_date=FROM_UNIXTIME($time)")
            return 0
        }
        val uuid = Bank.getUUID(uuidOrAll) ?: return 1
        mysql.execute("update server_loan_tbl set last_pay_date=FROM_UNIXTIME($time) Where uuid='${uuid}'")
        return 0
    }

    fun fetchLoanTop(page: Int): MutableList<Pair<String, Double>> {
        val list = mutableListOf<Pair<String, Double>>()
        val mysql = MySQLManager(plugin, "Man10ServerLoan")
        val rs = mysql.query("SELECT player,borrow_amount FROM server_loan_tbl order by borrow_amount desc limit 10 offset ${(page * 10) - 10};") ?: return list
        while (rs.next()) {
            list.add(Pair(rs.getString("player"), rs.getDouble("borrow_amount")))
        }
        rs.close()
        mysql.close()
        return list
    }
}
