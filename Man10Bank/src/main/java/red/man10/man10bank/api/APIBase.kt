package red.man10.man10bank.api

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import red.man10.man10bank.Man10Bank

object APIBase {

    var url = "https://localhost:7031"
    val mediaType = "application/json; charset=utf-8".toMediaType()
    lateinit var client : OkHttpClient
    val gson = Gson()

    fun setup(){

        client = OkHttpClient.Builder().cache(null).build()
        url = Man10Bank.instance.config.getString("api.url")?:"https://localhost:7031/Bank/"

    }

}