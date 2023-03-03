package red.man10.man10bank

import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10bank.api.APIBase
import red.man10.man10bank.bank.ATM
import red.man10.man10bank.cheque.Cheque

class Man10Bank : JavaPlugin() {

    companion object{

        lateinit var instance : Man10Bank
        lateinit var vault : VaultManager

    }

    override fun onEnable() {
        // Plugin startup logic

        saveDefaultConfig()

        instance = this
        vault = VaultManager(this)

        APIBase.setup()
        ATM.load()

        getCommand("mcheque")!!.setExecutor(Cheque)
        getCommand("mchequeop")!!.setExecutor(Cheque)

        server.pluginManager.registerEvents(Cheque,this)

    }

    override fun onDisable() {
        // Plugin shutdown logic
    }
}
