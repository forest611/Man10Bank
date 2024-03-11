package red.man10.man10bank.api

import okhttp3.RequestBody.Companion.toRequestBody
import red.man10.man10bank.api.APIBase.get
import red.man10.man10bank.api.APIBase.gson
import red.man10.man10bank.api.APIBase.post
import java.time.LocalDateTime
import java.util.*

object APIHistory {

    private const val PATH = "/history/"

    fun getBalanceTop(record : Int, skip:Int):Array<EstateTable>{
        var json = ""
        get("${PATH}get-balance-top?record=${record}&skip=${skip}"){
            json = it.body?.string()?:""
        }
        return gson.fromJson(json,arrayOf<EstateTable>()::class.java)
    }

    fun getLoanTop(record : Int, skip:Int):Array<APIServerLoan.ServerLoanTable>{
        var json = ""
        get("${PATH}get-loan-top?record=${record}&skip=${skip}"){
            json = it.body?.string()?:""
        }
        return gson.fromJson(json,arrayOf<APIServerLoan.ServerLoanTable>()::class.java)
    }

    fun getUserEstate(uuid: UUID):EstateTable?{
        var json = ""
        get("${PATH}get-user-estate?uuid=${uuid}"){
            json = it.body?.string()?:""
        }
        return gson.fromJson(json, EstateTable::class.java)
    }

    fun getServerEstate():ServerEstate?{
        var json = ""
        get(PATH+"get-server-estate"){
            json = it.body?.string()?:""
        }
        return gson.fromJson(json, ServerEstate::class.java)
    }

    fun addUserEstate(data : EstateTable){
        val body = gson.toJson(data).toRequestBody(APIBase.mediaType)
        post(PATH+"add-user-estate",body) {}
    }

    fun addATMLog(data:ATMLog){
        val body = gson.toJson(data).toRequestBody(APIBase.mediaType)
        post("${PATH}add-atm-log",body) {}
    }

    fun addVaultLog(data:VaultLog){
        val body = gson.toJson(data).toRequestBody(APIBase.mediaType)
        post("${PATH}add-vault-log",body) {}
    }

    data class VaultLog(
        var id : Int,
        var from_player : String,
        var from_uuid : String,
        var to_player : String,
        var to_uuid : String,
        var amount : Double,
        var plugin : String,
        var note : String,
        var display_note : String,
        var category : String,
        var date : LocalDateTime
    )

    data class EstateTable(
        var id : Int,
        var player : String,
        var uuid : String,
        var date : LocalDateTime,
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
        var date : LocalDateTime,
    )

    data class ATMLog(
        var id :Int,
        var player : String,
        var uuid : String,
        var amount : Double,
        var deposit : Boolean,
        var date : LocalDateTime
    )
}