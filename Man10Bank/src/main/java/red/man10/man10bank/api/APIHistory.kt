package red.man10.man10bank.api

import okhttp3.RequestBody.Companion.toRequestBody
import red.man10.man10bank.api.APIBase.getRequest
import red.man10.man10bank.api.APIBase.gson
import red.man10.man10bank.api.APIBase.postRequest
import java.util.*

object APIHistory {

    private const val apiRoute = "/history/"

    fun getBalanceTop(size : Int):Array<EstateTable>{
        val result = getRequest("${apiRoute}get-balance-top?size=${size}")?:""
        return gson.fromJson(result,arrayOf<EstateTable>()::class.java)
    }

    fun getUserEstate(uuid: UUID):EstateTable?{
        val result = getRequest("${apiRoute}get-user-estate?uuid=${uuid}")?:return null
        return gson.fromJson(result, EstateTable::class.java)
    }

    fun getServerEstate():ServerEstate?{
        val result = getRequest(apiRoute+"get-user-estate")?:return null
        return gson.fromJson(result, ServerEstate::class.java)
    }

    fun addUserEstate(data : EstateTable){
        val jsonStr = gson.toJson(data)
        val body = jsonStr.toRequestBody(APIBase.mediaType)
        postRequest(apiRoute+"add-user-estate",body)
    }

    fun addATMLog(data:ATMLog){
        val jsonStr = gson.toJson(data)
        val body = jsonStr.toRequestBody(APIBase.mediaType)
        postRequest("${apiRoute}add-atm-log",body)
    }

    fun addVaultLog(data:VaultLog){
        val jsonStr = gson.toJson(data)
        val body = jsonStr.toRequestBody(APIBase.mediaType)
        postRequest("${apiRoute}add-vault-log",body)
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
        var date : Date
    )

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

    data class ATMLog(
        var id :Int,
        var player : String,
        var uuid : String,
        var amount : Double,
        var deposit : Boolean,
        var date : Date
    )
}