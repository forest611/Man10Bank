package red.man10.man10bank

import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10bank.api.APIBase
import red.man10.man10bank.bank.ATM
import red.man10.man10bank.bank.Bank
import red.man10.man10bank.bank.BankEvent
import red.man10.man10bank.bank.DealCommand
import red.man10.man10bank.cheque.Cheque
import red.man10.man10bank.loan.ServerLoan
import red.man10.man10bank.util.BlockingQueue
import red.man10.man10bank.util.MenuFramework

class Man10Bank : JavaPlugin() {

    companion object{

        lateinit var instance : Man10Bank
        lateinit var vault : VaultManager

        private var bankOpen = true

        private var canConnectServer = false

        fun open(){
            bankOpen = true
            instance.config.set("enable",true)
            instance.saveConfig()
        }

        fun close(){
            bankOpen = false
            instance.config.set("enable",false)
            instance.saveConfig()
        }

        fun isEnableSystem():Boolean{
            return bankOpen && canConnectServer
        }
        //      システム起動
        fun systemSetup(){
            canConnectServer = APIBase.setup()
            ATM.load()
            BlockingQueue.start()

        }

        //      システム終了
        fun systemClose(){
            BlockingQueue.stop()
        }
    }

    override fun onEnable() {
        // Plugin startup logic

        saveDefaultConfig()

        //変数の登録
        bankOpen = config.getBoolean("enable")
        instance = this
        vault = VaultManager(this)

        //コマンドの登録
        getCommand("mrevo")!!.setExecutor(ServerLoan)
        getCommand("mcheque")!!.setExecutor(Cheque)
        getCommand("mchequeop")!!.setExecutor(Cheque)
        getCommand("atm")!!.setExecutor(ATM)
        Bank.labels.forEach { getCommand(it)!!.setExecutor(Bank) }
        DealCommand.labels.forEach { getCommand(it)!!.setExecutor(DealCommand) }

        //イベントの登録
        server.pluginManager.registerEvents(Cheque,this)
        server.pluginManager.registerEvents(MenuFramework.MenuListener,this)
        server.pluginManager.registerEvents(BankEvent,this)

        systemSetup()
    }

    override fun onDisable() {
        // Plugin shutdown logic
        systemClose()
    }
}
