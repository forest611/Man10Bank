package red.man10.man10bank

import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10bank.api.APIBase
import red.man10.man10bank.bank.ATM
import red.man10.man10bank.bank.Bank
import red.man10.man10bank.bank.DealCommand
import red.man10.man10bank.cheque.Cheque
import red.man10.man10bank.util.BlockingQueue
import red.man10.man10bank.util.MenuFramework

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
        BlockingQueue.start()

        getCommand("mcheque")!!.setExecutor(Cheque)
        getCommand("mchequeop")!!.setExecutor(Cheque)

        getCommand("atm")!!.setExecutor(ATM)

        Bank.labels.forEach { getCommand(it)!!.setExecutor(Bank) }
        DealCommand.labels.forEach { getCommand(it)!!.setExecutor(DealCommand) }

        server.pluginManager.registerEvents(Cheque,this)
        server.pluginManager.registerEvents(MenuFramework.MenuListener,this)
        server.pluginManager.registerEvents(Bank,this)

    }

    override fun onDisable() {
        // Plugin shutdown logic
        BlockingQueue.stop()
    }
}
