package red.man10.man10bank.api

import com.google.gson.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
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

    var userName = ""
    var password = ""
    private val credential = Credentials.basic(userName, password)


    //      POST
    fun post(path: String,body: RequestBody? = null,callback : (Response) -> Unit){

        loggerDebug("PostRequest:${path}")

        val request = if (body!=null){
            Request.Builder()
                .url(Config.url+path)
                .addHeader("Authorization", credential)
                .post(body)
                .build()
        } else{
            Request.Builder()
                .url(Config.url+path)
                .addHeader("Authorization", credential)
                .build()
        }

        client.newCall(request).execute().use {
            callback.invoke(it)
            loggerDebug("Code:${it.code} Body:${it.body?.string()}")
        }
    }

    fun get(path: String,callback: (Response) -> Unit){

        loggerDebug("GetRequest:${path}")

        val request = Request.Builder()
            .url(Config.url+path)
            .addHeader("Authorization", credential)
            .build()

        client.newCall(request).execute().use {
            callback.invoke(it)
            loggerDebug("Code:${it.code} Body:${it.body?.string()}")
        }
    }

    //      接続  接続に成功したらtrueを返す
    fun setup(){

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

        val status = APIStatus.getStatus()

        if (!status.enableAccessUserServer){
            throw RuntimeException("Man10BankServerとMan10UserServerの接続ができていません！")
        }
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