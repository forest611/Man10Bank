package red.man10.man10bank.api

import okhttp3.RequestBody.Companion.toRequestBody
import red.man10.man10bank.api.APIBase.mediaType

object APIStatus {

    private const val PATH = "/status/"

    suspend fun getStatus(): Status {

        var status = Status()

        APIBase.get("${PATH}get"){ response ->
            val body = response.body?.string()

            if (response.code != 200 || body == null){
                status.allFalse()
                return@get
            }
            status = APIBase.gson.fromJson(body,Status::class.java)
        }
        return status
    }

    suspend fun setStatus(status: Status){
        val body = APIBase.gson.toJson(status).toRequestBody(mediaType)
        APIBase.post("${PATH}set",body) {}
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

    }

    fun allFalse(){

    }
}