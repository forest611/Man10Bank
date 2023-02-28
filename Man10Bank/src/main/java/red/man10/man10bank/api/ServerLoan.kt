package red.man10.man10bank.api

import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.bukkit.Bukkit
import red.man10.man10bank.api.APIBase.gson
import java.util.*

object ServerLoan {

    private val apiRoute = "/serverloan/"

    fun getBorrowableAmount(uuid: UUID):Double{

        val request = Request.Builder()
            .url("${APIBase.url + apiRoute}borrowable-amount?uuid=${uuid}")
            .build()

        var amount : Double = -1.0

        try {
            val response = APIBase.client.newCall(request).execute()
            amount = response.body?.string()?.toDoubleOrNull()?:-1.0
        }catch (e: Exception){
            Bukkit.getLogger().info(e.message)
        }

        return amount
    }

    fun isLoser(uuid: UUID):Boolean{
        val request = Request.Builder()
            .url("${APIBase.url + apiRoute}is-loser?uuid=${uuid}")
            .build()

        var ret = false

        try {
            val response = APIBase.client.newCall(request).execute()
            ret = response.body?.string()?.toBooleanStrict()?:false
        }catch (e: Exception){
            Bukkit.getLogger().info(e.message)
        }

        return ret

    }

    fun getInfo(uuid: UUID): ServerLoanTable? {
        val request = Request.Builder()
            .url("${APIBase.url + apiRoute}get-info?uuid=${uuid}")
            .build()

        var ret : ServerLoanTable? = null

        try {
            val response = APIBase.client.newCall(request).execute()
            ret = gson.fromJson(response.body?.string()?:"",ServerLoanTable::class.java)
        }catch (e: Exception){
            Bukkit.getLogger().info(e.message)
        }

        return ret
    }

    fun setInfo(data:ServerLoanTable): String {
        val jsonStr = gson.toJson(data)

        val body = jsonStr.toRequestBody(APIBase.mediaType)

        val request = Request.Builder()
            .url("${APIBase.url + apiRoute}set-info")
            .post(body)
            .build()

        var result = ""

        try {
            val response = APIBase.client.newCall(request).execute()
            result = response.body?.string()?:"Null"
        }catch (e: java.lang.Exception){
            Bukkit.getLogger().info(e.message)
        }

        return result
    }

    fun borrow(uuid: UUID,amount:Double):String{

        val request = Request.Builder()
            .url("${APIBase.url + apiRoute}try-borrow?uuid=${uuid}&amount=${amount}")
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

    fun pay(uuid: UUID,amount: Double): Boolean {
        val request = Request.Builder()
            .url("${APIBase.url + apiRoute}pay?uuid=${uuid}&amount=${amount}")
            .build()

        var result = false

        try {
            val response = APIBase.client.newCall(request).execute()
            result = response.body?.string()?.toBoolean()?:false
        }catch (e: Exception){
            Bukkit.getLogger().info(e.message)
        }

        return result
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