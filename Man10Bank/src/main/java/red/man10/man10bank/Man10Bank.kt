package red.man10.man10bank

import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10bank.api.APIBase

class Man10Bank : JavaPlugin() {

    companion object{

        lateinit var instance : Man10Bank

    }

    override fun onEnable() {
        // Plugin startup logic

        instance = this

        APIBase.setup()

    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}