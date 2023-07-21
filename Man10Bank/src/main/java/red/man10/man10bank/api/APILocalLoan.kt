package red.man10.man10bank.api

import okhttp3.RequestBody.Companion.toRequestBody
import red.man10.man10bank.api.APIBase.getRequest
import red.man10.man10bank.api.APIBase.gson
import red.man10.man10bank.api.APIBase.postAndGetResponse
import java.time.LocalDateTime
import java.util.*

object APILocalLoan {

    private const val apiRoute="/localloan/"

    fun create(data: LocalLoanTable): Int {
        val jsonStr = gson.toJson(data)
        val body = jsonStr.toRequestBody(APIBase.mediaType)
        return postAndGetResponse("${apiRoute}create", body).toIntOrNull() ?: 0
    }

    fun pay(id:Int,amount:Double):String{
        return getRequest("${apiRoute}pay?id=${id}&amount=${amount}")?:"Null"
    }

    fun getInfo(id:Int):LocalLoanTable?{
        val result = getRequest("${apiRoute}get-info?uuid=${id}")?:return null
        return gson.fromJson(result,LocalLoanTable::class.java)
    }

    fun property():LocalLoanProperty{
        val result = getRequest("${apiRoute}property")
        return gson.fromJson(result,LocalLoanProperty::class.java)
    }


    data class LocalLoanTable(

        var id : Int,
        var lend_player:String,
        var lend_uuid:String,
        var borrow_player:String,
        var borrow_uuid:String,
        var borrow_date:LocalDateTime,
        var payback_date:LocalDateTime,
        var amount:Double

    )

    data class LocalLoanProperty(
        var MinimumInterest : Double,
        var MaximumInterest : Double,
        var Fee : Double
    )

}