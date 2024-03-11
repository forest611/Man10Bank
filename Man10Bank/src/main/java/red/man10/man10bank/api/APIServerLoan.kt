package red.man10.man10bank.api

import red.man10.man10bank.api.APIBase.get
import red.man10.man10bank.api.APIBase.gson
import java.time.LocalDateTime
import java.util.*

object APIServerLoan {

    private const val PATH = "/serverloan/"

    fun getBorrowableAmount(uuid: UUID):Double{
        var amount = 0.0
        get("${PATH}borrowable-amount?uuid=${uuid}"){
            amount = it.body?.string()?.toDoubleOrNull()?:0.0
        }
        return amount
    }

    fun isLoser(uuid: UUID):Boolean{
        var result = false
        get("${PATH}is-loser?uuid=${uuid}"){
            result = it.body?.string()?.toBooleanStrictOrNull()?:false
        }
        return result
    }

    fun nextPayDate(uuid:UUID): LocalDateTime? {
        var time : LocalDateTime? = null
        get("${PATH}next-pay?uuid=${uuid}"){
            if (it.code != 200)return@get
            val body = it.body?.string()?:return@get
            time = gson.fromJson(body,LocalDateTime::class.java)
        }
        return time
    }

    fun getInfo(uuid: UUID): ServerLoanTable? {
        var data : ServerLoanTable? = null
        get("${PATH}info?uuid=${uuid}"){
            if (it.code!=200)return@get
            val body = it.body?.string()?:return@get
            data = gson.fromJson(body,ServerLoanTable::class.java)
        }
        return data
    }

    //お金を借りる
    fun borrow(uuid: UUID,amount:Double):String{
        var result = "Null"
        get("${PATH}borrow?uuid=${uuid}&amount=${amount}"){
            result = it.body?.string()?:"Null"
        }
        return result
    }

    /**
     * リボを支払う
     */
    fun pay(uuid: UUID,amount: Double): String {
        var result = "Null"
        get("${PATH}pay?uuid=${uuid}&amount=${amount}"){
            result = it.body?.string()?:"Null"
        }
        return result
    }

    /**
     * リボの設定値の取得
     */
    fun property():ServerLoanProperty{
        var result = ""
        get("${PATH}property"){
            result = it.body?.string()?:""
        }
        return gson.fromJson(result,ServerLoanProperty::class.java)
    }

    data class ServerLoanTable(
        var id : Int,
        var player : String,
        var uuid : String,
        var borrow_date : LocalDateTime,
        var last_pay_date : LocalDateTime,
        var borrow_amount : Double,
        var payment_amount : Double,
        var failed_payment : Int,
        var stop_interest : Boolean

    )

    data class ServerLoanProperty(
        var dailyInterest:Double,
        var paymentInterval:Int,
        var minimumAmount:Double,
        var maximumAmount:Double
    )

}