package red.man10.man10bank.api

import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.bukkit.Bukkit
import java.util.*

object History {

    private const val apiRoute = "/history/"

    fun getBalanceTop(size : Double):Array<EstateTable>{

        val request = Request.Builder()
            .url("${APIBase.url + apiRoute}get-balance-top?size=${size}")
            .build()

        var ret = arrayOf<EstateTable>()

        try {
            val response = APIBase.client.newCall(request).execute()
            ret = APIBase.gson.fromJson(response.body?.string()?:"", arrayOf<EstateTable>()::class.java)
        }catch (e: Exception){
            Bukkit.getLogger().info(e.message)
        }

        return ret
    }

    fun getUserEstate(uuid: UUID):EstateTable?{
        val request = Request.Builder()
            .url("${APIBase.url + apiRoute}get-user-estate?uuid=${uuid}")
            .build()

        var ret : EstateTable? = null

        try {
            val response = APIBase.client.newCall(request).execute()
            ret = APIBase.gson.fromJson(response.body?.string()?:"", EstateTable::class.java)
        }catch (e: Exception){
            Bukkit.getLogger().info(e.message)
        }

        return ret
    }

    fun getServerEstate():ServerEstate?{

        val request = Request.Builder()
            .url("${APIBase.url + apiRoute}get-user-estate")
            .build()

        var ret : ServerEstate? = null

        try {
            val response = APIBase.client.newCall(request).execute()
            ret = APIBase.gson.fromJson(response.body?.string()?:"", ServerEstate::class.java)
        }catch (e: Exception){
            Bukkit.getLogger().info(e.message)
        }

        return ret
    }

    fun addUserEstate(data : EstateTable){

        val jsonStr = APIBase.gson.toJson(data)

        val body = jsonStr.toRequestBody(APIBase.mediaType)

        val request = Request.Builder()
            .url("${APIBase.url + apiRoute}add-user-estate")
            .post(body)
            .build()

        try {
            APIBase.client.newCall(request).execute()
        }catch (e: java.lang.Exception){
            Bukkit.getLogger().info(e.message)
        }
    }

//    fun addVaultTransaction(){
//
//    }

    data class EstateTable(
        var id : Int,
        var player : String,
        var uuid : String,
        var date : Date,
        var vault : Double,
        var bank : Double,
        var cash : Double,
        var estete : Double,
        var loan : Double,
        var shop : Double,
        var crypto : Double,
        var total : Double
    )

    data class ServerEstate(
        var id : Int,
        var vault : Double,
        var bank : Double,
        var cash : Double,
        var estete : Double,
        var loan : Double,
        var shop : Double,
        var crypto : Double,
        var total : Double,
        var year : Int,
        var month : Int,
        var day : Int,
        var hour : Int,
        var date : Date,
    )

}