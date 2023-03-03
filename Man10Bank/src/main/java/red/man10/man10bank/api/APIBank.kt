package red.man10.man10bank.api

import okhttp3.RequestBody.Companion.toRequestBody
import org.bukkit.entity.Player
import red.man10.man10bank.api.APIBase.getRequest
import red.man10.man10bank.api.APIBase.gson
import red.man10.man10bank.api.APIBase.mediaType
import java.util.*

object APIBank {

    private const val apiRoute = "/bank/"

    fun getBalance(uuid: UUID): Double {
        return getRequest(apiRoute + "balance?uuid=${uuid}")?.toDoubleOrNull() ?: -1.0
    }

    fun getUserLog(uuid: UUID): Array<MoneyLog> {
        val result = getRequest(apiRoute+"log?uuid=${uuid}")
        return gson.fromJson(result, arrayOf<MoneyLog>()::class.java)
    }

    fun addBank(data: TransactionData): String {

        val jsonStr = gson.toJson(data)
        val body = jsonStr.toRequestBody(mediaType)
        return getRequest(apiRoute + "add", body) ?: "Null"
    }

    fun takeBank(data: TransactionData): String {

        val jsonStr = gson.toJson(data)
        val body = jsonStr.toRequestBody(mediaType)
        return getRequest(apiRoute + "take", body) ?: "Null"
    }

    fun setBank(data:TransactionData): String {

        val jsonStr = gson.toJson(data)
        val body = jsonStr.toRequestBody(mediaType)
        return getRequest(apiRoute+"set",body)?:"Null"
    }

    fun createBank(p:Player): String {
        return getRequest("${apiRoute}create?uuid=${p.uniqueId}&mcid=${p.name}")?:"Null"
    }

    data class TransactionData(
        var uuid: String,
        var amount : Double,
        var plugin : String,
        var note : String,
        var displayNote : String
    )

    data class MoneyLog(
        var id : Int,
        var player : String,
        var uuid : String,
        var plugin_name : String,
        var amount : Double,
        var note : String,
        var display_note : String,
        var server : String,
        var deposit : Boolean,
        var date : Date
    )
}