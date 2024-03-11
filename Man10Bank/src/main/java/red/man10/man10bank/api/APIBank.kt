package red.man10.man10bank.api

import okhttp3.RequestBody.Companion.toRequestBody
import red.man10.man10bank.api.APIBase.get
import red.man10.man10bank.api.APIBase.gson
import red.man10.man10bank.api.APIBase.mediaType
import red.man10.man10bank.api.APIBase.post
import java.time.LocalDateTime
import java.util.*

object APIBank {

    private const val PATH = "/bank/"

    suspend fun getUUID(name:String):UUID?{
        var uuid : UUID? = null
        get(PATH+"uuid?name=${name}"){
            if (it.code != 200)return@get
            uuid = UUID.fromString(it.body?.string())
        }
        return uuid
    }
    suspend fun getBalance(uuid: UUID): Double {
        var balance = 0.0
        get(PATH+"get?uuid=${uuid}"){
            if (it.code != 200){
                balance = -1.0
                return@get
            }
            balance = it.body?.string()?.toDoubleOrNull()?:0.0
        }
        return balance
    }

    suspend fun addBalance(data: TransactionData): BankResult {
        val body = gson.toJson(data).toRequestBody(mediaType)
        var result : BankResult = BankResult.UNKNOWN_STATUS_CODE
        post(PATH + "add",body){
            when(it.code){
                200 -> {
                    result = BankResult.SUCCESSFUL
                }
                404 -> {
                    result = BankResult.NOT_FOUND_ACCOUNT
                }
                500 -> {
                    result = BankResult.FAILED
                }
            }
        }
        return result
    }

    suspend fun takeBalance(data: TransactionData): BankResult{
        val body = gson.toJson(data).toRequestBody(mediaType)
        var result : BankResult = BankResult.UNKNOWN_STATUS_CODE
        post(PATH + "take",body){
            when(it.code){
                200 -> {
                    result = BankResult.SUCCESSFUL
                }
                404 -> {
                    result = BankResult.NOT_FOUND_ACCOUNT
                }
                400 -> {
                    result = BankResult.LACK_OF_MONEY
                }
            }
        }
        return result
    }

    suspend fun setBalance(data: TransactionData): BankResult{
        val body = gson.toJson(data).toRequestBody(mediaType)
        var result : BankResult = BankResult.UNKNOWN_STATUS_CODE
        post(PATH + "set",body){
            when(it.code){
                200 -> {
                    result = BankResult.SUCCESSFUL
                }
                404 -> {
                    result = BankResult.NOT_FOUND_ACCOUNT
                }
            }
        }
        return result
    }

    suspend fun getLog(uuid: UUID, count:Int, skip:Int): Array<MoneyLog> {
        var log : Array<MoneyLog> = arrayOf()
        get(PATH+"log?uuid=$uuid&count=$count&skip=$skip"){
            if (it.code != 200){
                return@get
            }
            log = gson.fromJson(it.body?.string()?:"", arrayOf<MoneyLog>()::class.java)
        }
        return log
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
        SUCCESSFUL,
        NOT_FOUND_ACCOUNT,
        LACK_OF_MONEY,
        UNKNOWN_STATUS_CODE,
        FAILED
    }
}