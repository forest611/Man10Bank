package red.man10.man10bank.api

import com.google.gson.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.kyori.adventure.text.Component
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import org.bukkit.Bukkit
import red.man10.man10bank.Man10Bank
import red.man10.man10bank.util.Utility.loggerDebug
import java.lang.reflect.Type
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.naming.AuthenticationException
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
    var baseUrl = ""
    var isKickPlayers = false
    private lateinit var credential : String
    var enable = false
        private set

    //      POST
    suspend fun post(path: String,body: RequestBody? = null):Response{
        if (!enable) throw IllegalStateException("サーバーに問題あり")

        loggerDebug("PostRequest:${path}")

        val request = if (body!=null){
            Request.Builder()
                .url(baseUrl+path)
                .addHeader("Authorization", credential)
                .post(body)
                .build()
        } else{
            Request.Builder()
                .url(baseUrl+path)
                .addHeader("Authorization", credential)
                .build()
        }

        return withContext(Dispatchers.IO){
            val response = try {
                client.newCall(request).execute()
            }catch (e:Exception){
                disable()
                throw e
            }

            when(response.code){
                500 -> {
                    disable()
                    throw RuntimeException("Man10BankServerのエラーの可能性 修正後にリロードをしてください")
                }
                401 -> {
                    disable()
                    throw AuthenticationException("Httpの認証に問題あり 修正後にリロードをしてください")
                }
                else -> {
                    loggerDebug("Code:${response.code}")
                    response
                }
            }
        }
    }

    suspend fun get(path: String) : Response{
        if (!enable)throw IllegalStateException("サーバーに問題あり")

        loggerDebug("GetRequest:${path}")

        val request = Request.Builder()
            .url(baseUrl+path)
            .addHeader("Authorization", credential)
            .build()

        return withContext(Dispatchers.IO){
            val response = client.newCall(request).execute()
            when(response.code){
                500 -> {
                    disable()
                    throw RuntimeException("Man10BankServerのエラーの可能性 修正後にリロードをしてください")
                }
                401 -> {
                    disable()
                    throw AuthenticationException("Httpの認証に問題あり 修正後にリロードをしてください")
                }
                else -> {
                    loggerDebug("Code:${response.code}")
                    response
                }
            }
        }
    }

    //      接続  接続に成功したらtrueを返す
    suspend fun setup() {

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
        credential = Credentials.basic(userName, password)

        enable = true

        val status = APIStatus.getStatus()

        if (!status.enableAccessUserServer){
            enable = false
            throw RuntimeException("Man10BankServerとMan10UserServerの接続ができていません！")
        }

        Bukkit.getLogger().info("Man10BankServerへの接続に成功しました")

    }

    private fun setupGson(){
        gson = GsonBuilder()
            .registerTypeHierarchyAdapter(LocalDateTime::class.java,LocalDateTimeSerializer())
            .create()
    }

    private fun disable(){
        enable = false

        if (isKickPlayers){
            Bukkit.getScheduler().runTask(Man10Bank.instance, Runnable {
                Bukkit.getOnlinePlayers().forEach { it.kick(Component.text("不具合のためホワイトリストをONにします")) }
                Bukkit.setWhitelist(true)
            })
        }
    }

    //  C#DateTime->KotlinLocalDateTime
//    2023-12-17T20:13:45.1087216+09:00
    class LocalDateTimeSerializer : JsonSerializer<LocalDateTime>,JsonDeserializer<LocalDateTime> {
        private val deserializeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        private val serializeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")

        override fun deserialize(json: JsonElement?, typeOfT: Type?, context: JsonDeserializationContext?): LocalDateTime {
            val dateString = json?.asString?.replace("T"," ")?.replace("+9:00","")
//            Bukkit.getLogger().info("JsonToLocalDateTime:${dateString}")
            return LocalDateTime.parse(dateString, deserializeFormatter)
        }

        override fun serialize(src: LocalDateTime?, typeOfSrc: Type?, context: JsonSerializationContext?): JsonElement {
            val dateString = src?.format(serializeFormatter)
            return JsonPrimitive(dateString)
        }
    }


}