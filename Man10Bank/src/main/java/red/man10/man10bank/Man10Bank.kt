package red.man10.man10bank

import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10bank.api.APIBase
import red.man10.man10bank.bank.ATM

class Man10Bank : JavaPlugin() {

    companion object{

        lateinit var instance : Man10Bank
        lateinit var vault : VaultManager

    }

    override fun onEnable() {
        // Plugin startup logic

        instance = this
        vault = VaultManager(instance)

        APIBase.setup()
        ATM.load()

    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}