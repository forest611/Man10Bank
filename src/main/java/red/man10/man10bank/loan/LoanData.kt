package red.man10.man10bank.loan

import org.bukkit.entity.Player
import red.man10.man10bank.Bank
import red.man10.man10bank.Man10Bank
import red.man10.man10bank.Man10Bank.Companion.plugin
import red.man10.man10bank.MySQLManager
import java.util.*
import kotlin.math.floor

class LoanData {


    lateinit var borrow: UUID

    var nowAmount : Double = 0.0

    var id : Int = 0

    lateinit var borrowDate : Date
    lateinit var paybackDate : Date


    fun create(lend:Player, borrow: Player, borrowedAmount : Double, rate:Double, paybackDay:Int):Int{

        if (!Bank.withdraw(lend.uniqueId,(borrowedAmount* Man10Bank.loanFee), plugin,"LoanCreate"))return -1

        Bank.deposit(lend.uniqueId,borrowedAmount, plugin,"LoanCreate")

        nowAmount = floor(borrowedAmount * (1.0+rate))

        this.borrow = borrow.uniqueId

        borrowDate = Date()
        val cal = Calendar.getInstance()
        cal.time = borrowDate
        cal.add(Calendar.DAY_OF_YEAR,paybackDay)
        paybackDate = cal.time

        val mysql = MySQLManager(plugin,"Man10Loan")

        mysql.execute("INSERT INTO loan_table " +
                "(lend_player, lend_uuid, borrow_player, borrow_uuid, borrow_date, payback_date, amount) " +
                "VALUES ('${lend.name}', " +
                "'${lend.uniqueId}', " +
                "'${borrow.name}', " +
                "'${borrow.uniqueId}', " +
                "now()', " +
                "(SELECT FROM_UNIXTIME(${paybackDate.time})), " +
                "$nowAmount)")

        val rs = mysql.query("SELECT id from loan_table order by id desc limit 1;")?:return -1
        rs.next()

        id = rs.getInt("id")

        rs.close()
        mysql.close()

        return id

    }

    fun load(): LoanData? {

        val mysql = MySQLManager(plugin,"Man10Loan")

        val rs = mysql.query("select * from loan_table where id=$id;")?:return null

        if (!rs.next())return null

        borrow = UUID.fromString(rs.getString("borrow_uuid"))
        nowAmount = rs.getDouble("amount")
        paybackDate = rs.getDate("payback_date")

        rs.close()
        mysql.close()

        return this

    }

    fun save(amount:Double){

        val mysql = MySQLManager(plugin,"Man10Loan")

        mysql.execute("UPDATE loan_table set amount=$amount where id=$id;")

    }

    fun payback(): Double {

        if (nowAmount <= 0.0)return -1.0

        val man10Bank = Bank.getBalance(borrow)

        val balance = Man10Bank.vault.getBalance(borrow)

        var paybackAmount = 0.0

        val takeMan10Bank = if (man10Bank<nowAmount)man10Bank else nowAmount

        if (takeMan10Bank != 0.0 && Bank.withdraw(borrow,takeMan10Bank, plugin,"paybackMoney")){

            nowAmount -=takeMan10Bank

            paybackAmount +=takeMan10Bank

        }

        val takeBalance = if (balance<nowAmount)balance else nowAmount

        if (takeBalance != 0.0 && Man10Bank.vault.withdraw(borrow,takeBalance)){

            nowAmount -= takeBalance

            paybackAmount += takeBalance

        }

        save(nowAmount)

        return paybackAmount
    }

}