package red.man10.man10bank.webapi

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import red.man10.man10bank.Man10Bank
import java.lang.Exception
import java.util.UUID

object Bank {

    //TODO:仮のURL
    private const val URL = "https://localhost:7031/Bank/"
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    fun getBalance(uuid: UUID) : Double{

        val request = Request.Builder()
            .url(URL+"balance?uuid=${uuid}")
            .build()

        var balance : Double = -1.0

        try {
            val response = Man10Bank.client.newCall(request).execute()
            balance = response.body?.string()?.toDoubleOrNull()?:-1.0
//            response.close()
        }catch (e:Exception){
            Bukkit.getLogger().info(e.message)
        }

        return balance
    }

    fun getUserLog(uuid: UUID): Array<Model.MoneyLog> {

        val request = Request.Builder()
            .url(URL+"log?uuid=${uuid}")
            .build()

        var arrayOfLog = arrayOf<Model.MoneyLog>()

        try {
            val response = Man10Bank.client.newCall(request).execute()
            arrayOfLog = Man10Bank.gson.fromJson(response.body?.string(), arrayOf<Model.MoneyLog>()::class.java)
//            response.close()
        }catch (e:Exception){
            Bukkit.getLogger().info(e.message)
        }

        return arrayOfLog
    }

    fun addBank(uuid: UUID,amount:Double): String {

        val body = "".toRequestBody(mediaType)

        val request = Request.Builder()
            .url(URL+"add")
            .post(body)
            .build()

        var result = ""

        try {
            val response = Man10Bank.client.newCall(request).execute()
            result = response.body?.string()?:"Null"
        }catch (e:Exception){
            Bukkit.getLogger().info(e.message)
        }

        return result
    }

    fun takeBank(uuid: UUID,amount:Double): String {

        val body = "".toRequestBody(mediaType)

        val request = Request.Builder()
            .url(URL+"take")
            .post(body)
            .build()

        var result = ""

        try {
            val response = Man10Bank.client.newCall(request).execute()
            result = response.body?.string()?:"Null"
        }catch (e:Exception){
            Bukkit.getLogger().info(e.message)
        }

        return result
    }

    fun setBank(uuid: UUID,amount: Double): String {

        val body = "".toRequestBody(mediaType)

        val request = Request.Builder()
            .url(URL+"set")
            .post(body)
            .build()

        var result = ""

        try {
            val response = Man10Bank.client.newCall(request).execute()
            result = response.body?.string()?:"Null"
        }catch (e:Exception){
            Bukkit.getLogger().info(e.message)
        }

        return result
    }

    fun createBank(p:Player): String {

        val request = Request.Builder()
            .url(URL+"/create?uuid=${p.uniqueId}&mcid=${p.name}")
            .build()

        var result = ""

        try {
            val response = Man10Bank.client.newCall(request).execute()
            result = response.body?.string()?:"Null"
        }catch (e:Exception){
            Bukkit.getLogger().info(e.message)
        }

        return result
    }


}