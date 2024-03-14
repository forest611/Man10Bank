package red.man10.man10bank.api

import red.man10.man10bank.api.APIBase.get
import red.man10.man10bank.api.APIBase.gson
import red.man10.man10bank.api.APIBase.post
import java.time.LocalDateTime
import java.util.*

object APIServerLoan {

    private const val PATH = "/serverloan/"

    suspend fun getBorrowableAmount(uuid: UUID):Double{
        get("${PATH}borrowable-amount?uuid=${uuid}").use{
            return it.body?.string()?.toDoubleOrNull()?:0.0
        }
    }

    suspend fun isLoser(uuid: UUID):Boolean{
        get("${PATH}is-loser?uuid=${uuid}").use{
            return it.body?.string()?.toBooleanStrictOrNull()?:false
        }
    }

    suspend fun nextPayDate(uuid:UUID): LocalDateTime? {
        get("${PATH}next-pay?uuid=${uuid}").use{
            if (it.code != 200)return null
            val body = it.body?.string()?:return null
            return gson.fromJson(body,LocalDateTime::class.java)
        }
    }

    suspend fun getInfo(uuid: UUID): ServerLoanTable? {
        get("${PATH}info?uuid=${uuid}").use{
            if (it.code!=200)return null
            val body = it.body?.string()?:return null
            return gson.fromJson(body,ServerLoanTable::class.java)
        }
    }

    suspend fun setPaymentAmount(uuid: UUID,amount: Double):Boolean{
        post("${PATH}set-payment?uuid=${uuid}&amount=${amount}").use {
            return it.code == 200
        }
    }

    suspend fun addPaymentDay(day:Int):Boolean{
        get("${PATH}add-payment-day?day=${day}").use {
            return it.code == 200
        }
    }

    //お金を借りる
    suspend fun borrow(uuid: UUID,amount:Double):BorrowResult{
        get("${PATH}borrow?uuid=${uuid}&amount=${amount}").use{
            try {
                return BorrowResult.valueOf(it.body?.string()?:"Failed")
            }catch (e:Exception){
                return BorrowResult.Failed
            }
        }
    }

    /**
     * リボを支払う
     */
    suspend fun pay(uuid: UUID,amount: Double): PaymentResult {
        get("${PATH}pay?uuid=${uuid}&amount=${amount}").use{
            try {
                return PaymentResult.valueOf(it.body?.string()?:"NotLoan")
            }catch (e:Exception){
                return PaymentResult.NotLoan
            }
        }
    }

    /**
     * リボの設定値の取得
     */
    suspend fun property():ServerLoanProperty{
        get("${PATH}property").use{
            val result = it.body?.string()?:""
            return gson.fromJson(result,ServerLoanProperty::class.java)
        }
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

    enum class BorrowResult{
        Failed,
        Success,
        FirstSuccess
    }

    enum class PaymentResult{
        Success,
        NotEnoughMoney,
        NotLoan
    }

}