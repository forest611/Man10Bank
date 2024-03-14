package red.man10.man10bank.api

import okhttp3.RequestBody.Companion.toRequestBody
import red.man10.man10bank.api.APIBase.get
import red.man10.man10bank.api.APIBase.gson
import red.man10.man10bank.api.APIBase.mediaType
import red.man10.man10bank.api.APIBase.post
import java.time.LocalDateTime
import java.util.*
import kotlin.math.floor

object APIBank {

    private const val PATH = "/bank/"

    suspend fun getUUID(name:String):UUID?{
        get(PATH+"uuid?name=${name}").use {
            val body = it.body?.string()?:return null
            return UUID.fromString(body)
        }
    }
    suspend fun getBalance(uuid: UUID): Double {
        get(PATH+"get?uuid=${uuid}").use{
            if (it.code != 200){
                return 0.0
            }
            return it.body?.string()?.toDoubleOrNull()?:0.0
        }
    }

    suspend fun addBalance(data: TransactionData): BankResult {
        if (floor(data.amount) != data.amount) return BankResult.Failed
        val body = gson.toJson(data).toRequestBody(mediaType)
        post(PATH + "add",body).use{
            when(it.code){
                200 -> {
                    return BankResult.Successful
                }
                404 -> {
                    return BankResult.NotFoundAccount
                }
                500 -> {
                    return BankResult.Failed
                }
                else -> {
                    return BankResult.UnknownStatusCode
                }
            }
        }
    }

    suspend fun takeBalance(data: TransactionData): BankResult{
        if (floor(data.amount) != data.amount) return BankResult.Failed
        val body = gson.toJson(data).toRequestBody(mediaType)
        post(PATH + "take",body).use{
            when(it.code){
                200 -> {
                    return BankResult.Successful
                }
                404 -> {
                    return BankResult.NotFoundAccount
                }
                400 -> {
                    return BankResult.LackOfMoney
                }
                else -> {
                    return BankResult.UnknownStatusCode
                }
            }
        }
    }

    suspend fun setBalance(data: TransactionData): BankResult{
        val body = gson.toJson(data).toRequestBody(mediaType)
        post(PATH + "set",body).use{
            when(it.code){
                200 -> {
                    return BankResult.Successful
                }
                404 -> {
                    return BankResult.NotFoundAccount
                }
                else -> {
                    return BankResult.UnknownStatusCode
                }
            }
        }
    }

    suspend fun getLog(uuid: UUID, count:Int, skip:Int): Array<MoneyLog> {
        get(PATH+"log?uuid=$uuid&count=$count&skip=$skip").use{
            if (it.code != 200){
                return arrayOf()
            }
            return gson.fromJson(it.body?.string()?:"", arrayOf<MoneyLog>()::class.java)
        }
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
        var date : LocalDateTime
    )

    enum class BankResult{
        Successful,
        NotFoundAccount,
        LackOfMoney,
        UnknownStatusCode,
        Failed
    }
}