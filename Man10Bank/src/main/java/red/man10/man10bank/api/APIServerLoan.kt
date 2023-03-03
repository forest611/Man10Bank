package red.man10.man10bank.api

import okhttp3.RequestBody.Companion.toRequestBody
import red.man10.man10bank.api.APIBase.getRequest
import red.man10.man10bank.api.APIBase.gson
import java.util.*

object APIServerLoan {

    private const val apiRoute = "/serverloan/"

    fun getBorrowableAmount(uuid: UUID):Double{
        val result = getRequest("${apiRoute}borrowable-amount?uuid=${uuid}")
        return result?.toDoubleOrNull()?:-1.0
    }

    fun isLoser(uuid: UUID):Boolean{
        val result = getRequest("${apiRoute}is-loser?uuid=${uuid}")
        return result?.toBooleanStrictOrNull()?:false
    }

    fun getInfo(uuid: UUID): ServerLoanTable? {
        val result = getRequest("${apiRoute}get-info?uuid=${uuid}")?:return null
        return gson.fromJson(result,ServerLoanTable::class.java)
    }

    fun setInfo(data: ServerLoanTable): String {
        val jsonStr = gson.toJson(data)
        val body = jsonStr.toRequestBody(APIBase.mediaType)
        return getRequest("${apiRoute}set-info", body) ?: "Null"
    }

    fun borrow(uuid: UUID,amount:Double):String{
        return getRequest("${apiRoute}try-borrow?uuid=${uuid}&amount=${amount}")?:"Null"
    }

    fun pay(uuid: UUID,amount: Double): Boolean {
        val result = getRequest("${apiRoute}pay?uuid=${uuid}&amount=${amount}")
        return result?.toBooleanStrictOrNull()?:false
    }

    data class ServerLoanTable(
        var id : Int,
        var player : String,
        var uuid : String,
        var borrow_date : Date,
        var last_pay_date : Date,
        var borrow_amount : Double,
        var payment_amount : Double,
        var failed_payment : Int,
        var stop_interest : Boolean

    )

}