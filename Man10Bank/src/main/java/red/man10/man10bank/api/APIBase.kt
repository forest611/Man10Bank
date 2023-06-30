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

    var url = "https://localhost:7031"
    val mediaType = "application/json; charset=utf-8".toMediaType()
    private lateinit var client : OkHttpClient
    lateinit var gson : Gson

    //      POST
    fun postRequest(url: String,body: RequestBody? = null):Int{

        loggerDebug("PostRequest:${url}")

        val request = if (body!=null){
            Request.Builder()
                .url(APIBase.url+url)
                .post(body)
                .build()
        } else{
            Request.Builder()
                .url(APIBase.url+url)
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
    fun postAndGetResponse(url: String,body: RequestBody? = null):String{

        loggerDebug("PostRequest:${url}")

        val request = if (body!=null){
            Request.Builder()
                .url(APIBase.url+url)
                .post(body)
                .build()
        } else{
            Request.Builder()
                .url(APIBase.url+url)
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
    fun getRequest(url:String): String? {

        loggerDebug("GetRequest:${url}")

        val request = Request.Builder()
            .url(APIBase.url+url)
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

        url = Man10Bank.instance.config.getString(Config.API_URL)?:"https://localhost:7031"

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
            .registerTypeHierarchyAdapter(LocalDateTime::class.java,LocalDateTimeDeserializer())
            .create()
    }

    //  C#DateTime->KotlinLocalDateTime
    class LocalDateTimeDeserializer : JsonDeserializer<LocalDateTime> {
        private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): LocalDateTime {
            val dateString = json?.asString?.replace("T"," ")
            return LocalDateTime.parse(dateString, formatter)
        }
    }

    //  KotlinLocalDateTIme->C#DateTime
    class LocalDateTimeSerializer : JsonSerializer<LocalDateTime> {
        private val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

        override fun serialize(src: LocalDateTime?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
            val dateString = src?.format(formatter)
            return JsonPrimitive(dateString)
        }
    }

}