package red.man10.man10bank.api

import com.google.gson.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import org.bukkit.Bukkit
import red.man10.man10bank.Config
import red.man10.man10bank.Man10Bank
import red.man10.man10bank.util.Utility.loggerDebug
import red.man10.man10bank.util.Utility.loggerInfo
import java.lang.reflect.Type
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager


object APIBase {

//    private var url = "https://localhost:7031"
    val mediaType = "application/json; charset=utf-8".toMediaType()
    private lateinit var client : OkHttpClient
    lateinit var gson : Gson

    //      POST
    fun postRequest(path: String, body: RequestBody? = null):Int{

        loggerDebug("PostRequest:${path}")

        val request = if (body!=null){
            Request.Builder()
                .url(Config.url+path)
                .post(body)
                .build()
        } else{
            Request.Builder()
                .url(Config.url+path)
                .build()
        }

        var code = 0

        try {
            val response = client.newCall(request).execute()
            code = response.code
            response.close()
            loggerDebug("StatusCode:${code}")
        }catch (e:Exception){
            Bukkit.getLogger().info(e.message)
        }

        return code
    }

    //      POST
    fun postAndGetResponse(path: String, body: RequestBody? = null):String{

        loggerDebug("PostRequest:${path}")

        val request = if (body!=null){
            Request.Builder()
                .url(Config.url+path)
                .post(body)
                .build()
        } else{
            Request.Builder()
                .url(Config.url+path)
                .build()
        }

        var result = ""

        try {
            val response = client.newCall(request).execute()
            result = response.body?.string()?:""
            response.close()
            loggerDebug("ResponseBody:${result}")
        }catch (e:Exception){
            Bukkit.getLogger().info(e.message)
        }

        return result
    }

    //      GET
    fun getRequest(path:String): String? {

        loggerDebug("GetRequest:${path}")

        val request = Request.Builder()
            .url(Config.url+path)
            .build()

        var result : String? = null

        try {
            val response = client.newCall(request).execute()
            result = response.body?.string()
            loggerDebug("ResponseBody:$result")
            response.close()
        }catch (e:Exception){
            Bukkit.getLogger().info(e.message)
        }

        return result
    }

    //      接続  接続に成功したらtrueを返す
    fun setup() : Boolean{

        setupGson()

        if (::client.isInitialized){
            client.connectionPool.evictAll()
        }

        val trustAllCerts = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> {
                    return arrayOf()
                }
            }
        )

        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, SecureRandom())


        client = OkHttpClient.Builder()
            .cache(null)
            .readTimeout(10,TimeUnit.SECONDS)
            .writeTimeout(10,TimeUnit.SECONDS)
            .sslSocketFactory(sslContext.socketFactory,trustAllCerts[0] as X509TrustManager)
            .build()

        Man10Bank.instance.reloadConfig()

//        url = Man10Bank.instance.config.getString(Config.API_URL)?:"https://localhost:7031"

        when(val code = connectionCheck()){
            0 ->{
                loggerInfo("Man10BankServerの接続を確認しました")
                return true
            }

            1 ->{
                Man10Bank.instance.server.setWhitelist(true)
                Bukkit.getLogger().warning("Man10BankServerがMySQLに接続できていません。ホワイトリストをかけます Code:${code}")
                Bukkit.getLogger().warning("接続できる状態にした後にPaperMCの再起動をしてください")
                return false
            }

            else ->{
                Man10Bank.instance.server.setWhitelist(true)
                Bukkit.getLogger().warning("Man10BankServerの接続に失敗したので、ホワイトリストをかけます Code:${code}")
                Bukkit.getLogger().warning("接続できる状態にした後にPaperMCの再起動をしてください")
                return false
            }
        }
    }

    //      接続を試す
    private fun connectionCheck(): Int {
        return getRequest("/bank/try-connect")?.toIntOrNull() ?: -1
    }


    private fun setupGson(){
        gson = GsonBuilder()
            .registerTypeHierarchyAdapter(LocalDateTime::class.java,LocalDateTimeSerializer())
            .create()
    }

    //  C#DateTime->KotlinLocalDateTime
//    2023-12-17T20:13:45.1087216+09:00
    class LocalDateTimeSerializer : JsonSerializer<LocalDateTime>,JsonDeserializer<LocalDateTime> {
        private val deserializeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private val serializeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

        override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): LocalDateTime {
            val dateString = json?.asString?.replace("T"," ")?.replace("+9:00","")
            Bukkit.getLogger().info("JsonToLocalDateTime:${dateString}")
            return LocalDateTime.parse(dateString, deserializeFormatter)
        }

        override fun serialize(src: LocalDateTime?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
            val dateString = src?.format(serializeFormatter)
            return JsonPrimitive(dateString)
        }
    }


}