package red.man10.man10bank.api

import okhttp3.Request
import org.bukkit.Bukkit
import java.util.UUID

object Cheque {

    private const val apiRoute = "/cheque/"

    fun create(uuid: UUID,amount:Double,note:String,isOp:Boolean=false):Int{
        val request = Request.Builder()
            .url("${APIBase.url + apiRoute}create?uuid=${uuid}&amount=${amount}&note=${note}&isOp=${isOp}")
            .build()

        var result = -1

        try {
            val response = APIBase.client.newCall(request).execute()
            result = response.body?.string()?.toIntOrNull()?:-1
        }catch (e: Exception){
            Bukkit.getLogger().info(e.message)
        }

        return result
    }

    fun use(id: Int):Double{
        val request = Request.Builder()
            .url("${APIBase.url + apiRoute}try-use?id=${id}")
            .build()

        var amount : Double = -1.0

        try {
            val response = APIBase.client.newCall(request).execute()
            amount = response.body?.string()?.toDoubleOrNull()?:-1.0
        }catch (e: java.lang.Exception){
            Bukkit.getLogger().info(e.message)
        }

        return amount
    }

}