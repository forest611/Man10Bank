package red.man10.man10bank.api

import okhttp3.RequestBody.Companion.toRequestBody
import red.man10.man10bank.api.APIBase.get
import red.man10.man10bank.api.APIBase.gson
import red.man10.man10bank.api.APIBase.post
import java.time.LocalDateTime
import java.util.*

object APILocalLoan {

    private const val PATH = "/localloan/"

    suspend fun create(data: LocalLoanTable): Int {
        var id = 0
        val body = gson.toJson(data).toRequestBody(APIBase.mediaType)
        post("${PATH}create", body) {
            if (it.code != 200)return@post
            id = it.body?.string()?.toIntOrNull()?:0
        }
        return id
    }

    suspend fun pay(id:Int,amount:Double):String{
        var result = "Null"
        get("${PATH}pay?id=${id}&amount=${amount}"){
            result = it.body?.string()?:"Null"
        }
        return result
    }

    suspend fun getInfo(id:Int):LocalLoanTable?{
        var json = ""
        get("${PATH}get-info?id=${id}"){
            json = it.body?.string()?:""
        }
        return gson.fromJson(json,LocalLoanTable::class.java)
    }

    suspend fun property():LocalLoanProperty{
        var json = ""
        get("${PATH}property"){
            json = it.body?.string()?:""
        }
        return gson.fromJson(json,LocalLoanProperty::class.java)
    }

    suspend fun totalLoan(uuid: UUID):Double{
        var amount = 0.0
        get("${PATH}total-loan?uuid=${uuid}"){
            amount = it.body?.string()?.toDoubleOrNull()?:0.0
        }
        return amount
    }

    data class LocalLoanTable(

        var id : Int,
        var lend_player:String,
        var lend_uuid:String,
        var borrow_player:String,
        var borrow_uuid:String,
        var borrow_date:LocalDateTime,
        var payback_date:LocalDateTime,
        var amount:Double

    )

    data class LocalLoanProperty(
        var minimumInterest : Double,
        var maximumInterest : Double,
        var fee : Double
    )

}