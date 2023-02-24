package red.man10.man10bank

import com.google.gson.Gson
import okhttp3.OkHttpClient
import org.bukkit.plugin.java.JavaPlugin

class Man10Bank : JavaPlugin() {

    companion object{
        val client = OkHttpClient()
        val gson = Gson()
    }

    override fun onEnable() {
        // Plugin startup logic
    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}