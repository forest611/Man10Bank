package red.man10.man10bank.api

import okhttp3.RequestBody.Companion.toRequestBody
import org.bukkit.Bukkit
import red.man10.man10bank.status.StatusManager
import red.man10.man10bank.api.APIBase.getRequest
import red.man10.man10bank.api.APIBase.gson
import red.man10.man10bank.api.APIBase.mediaType
import red.man10.man10bank.api.APIBase.postRequest
import red.man10.man10bank.status.Status
import java.time.LocalDateTime
import java.util.*

object APIBank {

    private const val apiRoute = "/bank/"

    fun getScore(uuid: UUID):Int{
        return getRequest(apiRoute + "score?uuid=${uuid}")?.toIntOrNull() ?: 0
    }

    fun getUUID(mcid:String): UUID? {
        val uuid : UUID
        try {
            uuid = UUID.fromString(getRequest(apiRoute+"uuid?mcid=${mcid}"))
        }catch (e:Exception){
            return null
        }
        return uuid
    }

    fun getBalance(uuid: UUID): Double {
        return getRequest(apiRoute + "balance?uuid=${uuid}")?.toDoubleOrNull() ?: -1.0
    }

    fun getBankLog(uuid: UUID,record:Int,skip:Int): Array<MoneyLog> {
        val result = getRequest(apiRoute+"log?uuid=${uuid}&record=${record}&skip=${skip}")
        return gson.fromJson(result, arrayOf<MoneyLog>()::class.java)
    }

    fun addBank(data: TransactionData): BankResult {

        val jsonStr = gson.toJson(data)
        val body = jsonStr.toRequestBody(mediaType)
        when(postRequest(apiRoute + "add", body)){
            200 -> return BankResult.SUCCESSFUL
            550 -> return BankResult.NOT_FOUND_ACCOUNT
            551 -> return BankResult.FAILED
        }
        return BankResult.UNKNOWN_STATUS_CODE
    }

    fun takeBank(data: TransactionData): BankResult {

        val jsonStr = gson.toJson(data)
        val body = jsonStr.toRequestBody(mediaType)
        when(postRequest(apiRoute + "take", body)){
            200 -> return BankResult.SUCCESSFUL
            550 -> return BankResult.NOT_FOUND_ACCOUNT
            551 -> return BankResult.NOT_ENOUGH_MONEY
        }
        return BankResult.UNKNOWN_STATUS_CODE
    }

    fun setBank(data:TransactionData) {

        val jsonStr = gson.toJson(data)
        val body = jsonStr.toRequestBody(mediaType)
        postRequest(apiRoute+"set",body)
    }

    fun createBank(uuid:UUID) {
        val p = Bukkit.getOfflinePlayer(uuid)
        getRequest("${apiRoute}create?uuid=${p.uniqueId}&mcid=${p.name}")
    }

    fun getStatus(): Status {
        val result = getRequest("${apiRoute}get-status")
        return gson.fromJson(result, Status::class.java)
    }

    fun setStatus(data: Status){
        postRequest("${apiRoute}set-status", gson.toJson(data).toRequestBody(mediaType))
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
        NOT_ENOUGH_MONEY,
        UNKNOWN_STATUS_CODE,
        FAILED
    }
}