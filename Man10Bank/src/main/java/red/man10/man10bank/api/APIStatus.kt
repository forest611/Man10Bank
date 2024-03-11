package red.man10.man10bank.api

import okhttp3.RequestBody.Companion.toRequestBody
import red.man10.man10bank.api.APIBase.mediaType

object APIStatus {

    private const val PATH = "/status/"

    suspend fun getStatus(): Status {
        val status = Status()

        APIBase.get("${PATH}get").use{ response ->
            val body = response.body?.string()

            if (response.code != 200 || body == null){
                status.allFalse()
                return status
            }
            return APIBase.gson.fromJson(body,Status::class.java)
        }
    }

    suspend fun setStatus(status: Status){
        val body = APIBase.gson.toJson(status).toRequestBody(mediaType)
        APIBase.post("${PATH}set",body).use {  }
    }
}

class Status {
    var enableDealBank = false
    var enableATM = false
    var enableCheque = false
    var enableLocalLoan = false
    var enableServerLoan = false
    var enableAccessUserServer = false

    fun allTrue(){
        enableDealBank = true
        enableATM = true
        enableCheque = true
        enableLocalLoan = true
        enableServerLoan = true
        enableAccessUserServer = true

    }

    fun allFalse(){
        enableDealBank = false
        enableATM = false
        enableCheque = false
        enableLocalLoan = false
        enableServerLoan = false
        enableAccessUserServer = false
    }
}