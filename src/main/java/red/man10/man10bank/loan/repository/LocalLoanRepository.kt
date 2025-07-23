package red.man10.man10bank.loan.repository

import red.man10.man10bank.Man10Bank.Companion.plugin
import red.man10.man10bank.MySQLManager
import java.text.SimpleDateFormat
import java.util.*

object LocalLoanRepository {

    fun insertLoan(
        lendName: String,
        lendUUID: UUID,
        borrowName: String,
        borrowUUID: UUID,
        paybackDate: Date,
        amount: Double,
        collateralItem: String? = null
    ): Int? {
        val mysql = MySQLManager(plugin, "Man10Loan")
        if (!mysql.lock("loan_table")) {
            return null
        }
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        val collateralValue = if (collateralItem != null) "'$collateralItem'" else "NULL"
        val query = "INSERT INTO loan_table " +
            "(lend_player, lend_uuid, borrow_player, borrow_uuid, borrow_date, payback_date, amount, collateral_item) " +
            "VALUES ('$lendName', '$lendUUID', '$borrowName', '$borrowUUID', now(), '${sdf.format(paybackDate.time)}', $amount, $collateralValue);"
        val result = mysql.execute(query)
        if (!result) {
            mysql.unlock()
            mysql.close()
            return null
        }
        val rs = mysql.query("SELECT id from loan_table order by id desc limit 1;")
        val id = if (rs != null && rs.next()) rs.getInt("id") else null
        rs?.close()
        mysql.unlock()
        mysql.close()
        return id
    }

    fun fetchLoan(id: Int): LocalLoanRecord? {
        val mysql = MySQLManager(plugin, "Man10Loan")
        val rs = mysql.query("select * from loan_table where id=$id;") ?: return null
        val record = if (rs.next()) {
            LocalLoanRecord(
                rs.getInt("id"),
                UUID.fromString(rs.getString("lend_uuid")),
                UUID.fromString(rs.getString("borrow_uuid")),
                rs.getTimestamp("payback_date"),
                rs.getDouble("amount"),
                rs.getString("collateral_item")
            )
        } else null
        rs.close()
        mysql.close()
        return record
    }

    fun updateAmount(id: Int, amount: Double): Boolean {
        val mysql = MySQLManager(plugin, "Man10Loan")
        val result = mysql.execute("UPDATE loan_table set amount=$amount where id=$id;")
        mysql.close()
        return result
    }

    fun fetchTotalLoan(uuid: UUID): Double {
        val mysql = MySQLManager(plugin, "Man10Bank")
        val rs = mysql.query("select SUM(amount) from loan_table where borrow_uuid='${uuid}';") ?: return 0.0
        rs.next()
        val amount = rs.getDouble(1)
        rs.close()
        mysql.close()
        return amount
    }

    fun fetchLoanData(uuid: UUID): Set<Pair<Int, Double>> {
        val set = mutableSetOf<Pair<Int, Double>>()
        val mysql = MySQLManager(plugin, "Man10Bank")
        val rs = mysql.query("select id,amount from loan_table where borrow_uuid='${uuid}';") ?: return set
        while (rs.next()) {
            set.add(Pair(rs.getInt("id"), rs.getDouble("amount")))
        }
        rs.close()
        mysql.close()
        return set
    }

    fun deleteCollateral(id: Int): Boolean {
        val mysql = MySQLManager(plugin, "Man10Loan")
        val result = mysql.execute("UPDATE loan_table SET collateral_item = NULL WHERE id = $id;")
        mysql.close()
        return result
    }
}
