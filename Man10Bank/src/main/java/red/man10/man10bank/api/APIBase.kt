package red.man10.man10bank.api

import com.google.gson.Gson
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import org.bukkit.Bukkit
import red.man10.man10bank.Man10Bank
import java.lang.Exception
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager


object APIBase {

    var url = "https://localhost:7031"
    val mediaType = "application/json; charset=utf-8".toMediaType()
    lateinit var client : OkHttpClient
    val gson = Gson()

    fun setup(){

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

        url = Man10Bank.instance.config.getString("api.url")?:"https://localhost:7031"


        if (connectionCheck() != 0){
            Bukkit.getLogger().info("Man10BankServerの接続に失敗したので、ホワイトリストをかけます。")
            Man10Bank.instance.server.setWhitelist(true)
        }
    }

    private fun connectionCheck(): Int {

        val request = Request.Builder()
            .url("${url}/bank/try-connect")
            .build()

        var result = -1

        try {
            val response = client.newCall(request).execute()
            result = response.body?.string()?.toIntOrNull()?:-1

            if (response.code!=200){
                result = -1
            }

        }catch (e: Exception){
            Bukkit.getLogger().info(e.message)
        }

        return result

    }

}