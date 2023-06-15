package red.man10.man10bank

import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10bank.api.APIBase
import red.man10.man10bank.bank.ATM
import red.man10.man10bank.bank.Bank
import red.man10.man10bank.bank.BankEvent
import red.man10.man10bank.bank.DealCommand
import red.man10.man10bank.cheque.Cheque
import red.man10.man10bank.loan.LocalLoan
import red.man10.man10bank.loan.ServerLoan
import red.man10.man10bank.util.MenuFramework
import java.util.concurrent.Executors

class Man10Bank : JavaPlugin() {

    companion object{

        lateinit var instance : Man10Bank
        lateinit var vault : VaultManager

        val thread = Executors.newSingleThreadExecutor()

        private var bankOpen = true

        private var canConnectServer = false

        fun open(){
            bankOpen = true
            instance.config.set(Config.BANK_ENABLE,true)
            instance.saveConfig()
        }

        fun close(){
            bankOpen = false
            instance.config.set(Config.BANK_ENABLE,false)
            instance.saveConfig()
        }

        fun isEnableSystem():Boolean{
            return bankOpen && canConnectServer
        }
        //      システム起動
        fun systemSetup(){
            loadConfig()
            canConnectServer = APIBase.setup()
            if (!canConnectServer)return
            ATM.load()
            ServerLoan.setup()
        }

        //      システム終了
        fun systemClose(){
            thread.shutdownNow()
        }

        //      Configの読み込み
        private fun loadConfig(){

            instance.saveDefaultConfig()
            instance.reloadConfig()

            bankOpen = instance.config.getBoolean(Config.BANK_ENABLE)
            ServerLoan.isEnable = instance.config.getBoolean(Config.SERVER_LOAN_ENABLE)
        }
    }

    override fun onEnable() {
        // Plugin startup logic

        //変数の登録
        instance = this
        vault = VaultManager(this)

        //システムセットアップ
        systemSetup()

        //コマンドの登録
        getCommand("mrevo")!!.setExecutor(ServerLoan)
        getCommand("mcheque")!!.setExecutor(Cheque)
        getCommand("mchequeop")!!.setExecutor(Cheque)
        getCommand("atm")!!.setExecutor(ATM)
        getCommand("mlend")!!.setExecutor(LocalLoan)
        Bank.labels.forEach { getCommand(it)!!.setExecutor(Bank) }
        DealCommand.labels.forEach { getCommand(it)!!.setExecutor(DealCommand) }

        //イベントの登録
        server.pluginManager.registerEvents(Cheque,this)
        server.pluginManager.registerEvents(MenuFramework.MenuListener,this)
        server.pluginManager.registerEvents(BankEvent,this)

    }

    override fun onDisable() {
        // Plugin shutdown logic
        systemClose()
    }

}
