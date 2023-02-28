package red.man10.man10bank.api

import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import red.man10.man10bank.api.APIBase.client
import red.man10.man10bank.api.APIBase.gson
import red.man10.man10bank.api.APIBase.mediaType
import red.man10.man10bank.api.APIBase.url
import java.lang.Exception
import java.util.UUID

object Bank {

    private const val apiRoute = "/bank/"

    fun getBalance(uuid: UUID) : Double{

        val request = Request.Builder()
            .url("${url+ apiRoute}balance?uuid=${uuid}")
            .build()

        var balance : Double = -1.0

        try {
            val response = client.newCall(request).execute()
            balance = response.body?.string()?.toDoubleOrNull()?:-1.0
        }catch (e:Exception){
            Bukkit.getLogger().info(e.message)
        }

        return balance
    }

    fun getUserLog(uuid: UUID): Array<Model.MoneyLog> {

        val request = Request.Builder()
            .url("${url+ apiRoute}log?uuid=${uuid}")
            .build()

        var arrayOfLog = arrayOf<Model.MoneyLog>()

        try {
            val response = client.newCall(request).execute()
            arrayOfLog = gson.fromJson(response.body?.string(), arrayOf<Model.MoneyLog>()::class.java)
//            response.close()
        }catch (e:Exception){
            Bukkit.getLogger().info(e.message)
        }

        return arrayOfLog
    }

    fun addBank(data : TransactionData): String {

        val jsonStr = gson.toJson(data)

        val body = jsonStr.toRequestBody(mediaType)

        val request = Request.Builder()
            .url("${url+ apiRoute}add")
            .post(body)
            .build()

        var result = ""

        try {
            val response = client.newCall(request).execute()
            result = response.body?.string()?:"Null"
        }catch (e:Exception){
            Bukkit.getLogger().info(e.message)
        }

        return result
    }

    fun takeBank(data: TransactionData): String {

        val jsonStr = gson.toJson(data)

        val body = jsonStr.toRequestBody(mediaType)

        val request = Request.Builder()
            .url("${url+ apiRoute}take")
            .post(body)
            .build()

        var result = ""

        try {
            val response = client.newCall(request).execute()
            result = response.body?.string()?:"Null"
        }catch (e:Exception){
            Bukkit.getLogger().info(e.message)
        }

        return result
    }

    fun setBank(data:TransactionData): String {

        val jsonStr = gson.toJson(data)

        val body = jsonStr.toRequestBody(mediaType)
        val request = Request.Builder()
            .url("${url+ apiRoute}set")
            .post(body)
            .build()

        var result = ""

        try {
            val response = client.newCall(request).execute()
            result = response.body?.string()?:"Null"
        }catch (e:Exception){
            Bukkit.getLogger().info(e.message)
        }

        return result
    }

    fun createBank(p:Player): String {

        val request = Request.Builder()
            .url("${url+ apiRoute}create?uuid=${p.uniqueId}&mcid=${p.name}")
            .build()

        var result = ""

        try {
            val response = client.newCall(request).execute()
            result = response.body?.string()?:"Null"
        }catch (e:Exception){
            Bukkit.getLogger().info(e.message)
        }

        return result
    }

    data class TransactionData(
        var uuid: UUID,
        var amount : Double,
        var plugin : String,
        var note : String,
        var displayNote : String
    )

}