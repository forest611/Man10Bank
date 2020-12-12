package red.man10.man10bank.loan

import org.bukkit.entity.Player
import red.man10.man10bank.Bank
import red.man10.man10bank.Man10Bank
import java.util.*
import kotlin.math.floor

class LoanData {


    lateinit var debtor: UUID

    var nowAmount : Double = 0.0

    lateinit var borrowDate : Date
    lateinit var paybackDate : Date


    fun create(creditor:Player, debtor: Player, borrowedAmount : Double, rate:Double, paybackDay:Int):Int{

        nowAmount = floor(borrowedAmount * (1.0+rate))

        this.debtor = debtor.uniqueId

        borrowDate = Date()
        val cal = Calendar.getInstance()
        cal.time = borrowDate
        cal.add(Calendar.DAY_OF_YEAR,paybackDay)
        paybackDate = cal.time

        Bank.transfer(creditor.uniqueId,debtor.uniqueId,Man10Bank.plugin,borrowedAmount)

        //TODO:dbからidを取得する
        val id = 0


        return id

    }

    fun load(id:Int):LoanData{



        return this

    }

    fun payback():Double{

        val man10Bank = Bank.getBalance(debtor)

        val balance = Man10Bank.vault.getBalance(debtor)

        var paybackAmount = 0.0

        val takeMan10Bank = if (man10Bank<nowAmount)man10Bank else nowAmount

        if (takeMan10Bank != 0.0 && Bank.withdraw(debtor,takeMan10Bank,Man10Bank.plugin,"paybackMoney")){

            nowAmount -=takeMan10Bank

            paybackAmount +=takeMan10Bank

        }

        val takeBalance = if (balance<nowAmount)balance else nowAmount

        if (takeBalance != 0.0 && Man10Bank.vault.withdraw(debtor,takeBalance)){

            nowAmount -= takeBalance

            paybackAmount += takeBalance

        }

        if (nowAmount <= 0.0)delete()

        return paybackAmount

    }

    fun delete(){

        //TODO:DB

    }

}