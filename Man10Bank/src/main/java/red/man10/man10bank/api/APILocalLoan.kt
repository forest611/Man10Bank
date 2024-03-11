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
        val body = gson.toJson(data).toRequestBody(APIBase.mediaType)
        post("${PATH}create", body).use {
            if (it.code != 200)return 0
            return it.body?.string()?.toIntOrNull()?:0
        }
    }

    suspend fun pay(id:Int,amount:Double):String{
        get("${PATH}pay?id=${id}&amount=${amount}").use{
            return it.body?.string()?:"Null"
        }
    }

    suspend fun getInfo(id:Int):LocalLoanTable?{
        get("${PATH}get-info?id=${id}").use{
            val json = it.body?.string()?:""
            return gson.fromJson(json,LocalLoanTable::class.java)
        }
    }

    suspend fun property():LocalLoanProperty{
        get("${PATH}property").use{
            val json = it.body?.string()?:""
            return gson.fromJson(json,LocalLoanProperty::class.java)
        }
    }

    suspend fun totalLoan(uuid: UUID):Double{
        get("${PATH}total-loan?uuid=${uuid}").use{
            val amount = it.body?.string()?.toDoubleOrNull()?:0.0
            return amount
        }
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