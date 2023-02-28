package red.man10.man10bank.api

import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.bukkit.Bukkit
import java.util.*

object LocalLoan {

    private const val apiRoute="/localloan/"

    fun create(data:LocalLoanTable):Int{
        val jsonStr = APIBase.gson.toJson(data)

        val body = jsonStr.toRequestBody(APIBase.mediaType)

        val request = Request.Builder()
            .url("${APIBase.url + apiRoute}create")
            .post(body)
            .build()

        var result = -1

        try {
            val response = APIBase.client.newCall(request).execute()
            result = response.body?.string()?.toIntOrNull()?:-1
        }catch (e: Exception){
            Bukkit.getLogger().info(e.message)
        }

        return result
    }

    fun pay(id:Int,amount:Double):String{

        val request = Request.Builder()
            .url("${APIBase.url + apiRoute}pay?id=${id}&amount=${amount}")
            .build()

        var result = ""

        try {
            val response = APIBase.client.newCall(request).execute()
            result = response.body?.string()?:"Null"
        }catch (e: Exception){
            Bukkit.getLogger().info(e.message)
        }

        return result
    }

    fun getInfo(id:Int):LocalLoanTable?{
        val request = Request.Builder()
            .url("${APIBase.url + apiRoute}get-info?uuid=${id}")
            .build()

        var ret : LocalLoanTable? = null

        try {
            val response = APIBase.client.newCall(request).execute()
            ret = APIBase.gson.fromJson(response.body?.string()?:"", LocalLoanTable::class.java)
        }catch (e: Exception){
            Bukkit.getLogger().info(e.message)
        }

        return ret
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