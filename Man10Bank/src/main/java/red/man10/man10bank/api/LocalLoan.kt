package red.man10.man10bank.api

import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.bukkit.Bukkit
import red.man10.man10bank.api.APIBase.getRequest
import red.man10.man10bank.api.APIBase.gson
import java.util.*

object LocalLoan {

    private const val apiRoute="/localloan/"

    fun create(data:LocalLoanTable):Int{
        val jsonStr = APIBase.gson.toJson(data)
        val body = jsonStr.toRequestBody(APIBase.mediaType)
        val result = getRequest("${apiRoute}create",body)
        return result?.toIntOrNull()?:-1
    }

    fun pay(id:Int,amount:Double):String{
        return getRequest("${apiRoute}pay?id=${id}&amount=${amount}")?:"Null"
    }

    fun getInfo(id:Int):LocalLoanTable?{
        val result = getRequest("${apiRoute}get-info?uuid=${id}")?:return null
        return gson.fromJson(result,LocalLoanTable::class.java)
    }


    data class LocalLoanTable(

        var id : Int,
        var lend_player:String,
        var lend_uuid:String,
        var borrow_player:String,
        var borrow_uuid:String,
        var borrow_date:Date,
        var payback_date:Date,
        var amount:Double

    )

}