package red.man10.man10bank

import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10bank.api.APIBase
import red.man10.man10bank.bank.*
import red.man10.man10bank.cheque.Cheque
import red.man10.man10bank.loan.LocalLoan
import red.man10.man10bank.loan.ServerLoan
import red.man10.man10bank.util.MenuFramework
import java.util.concurrent.Executors

class Man10Bank : JavaPlugin() {

    companion object{

        lateinit var instance : Man10Bank
        lateinit var vault : VaultManager

        var threadPool = Executors.newSingleThreadExecutor()

//        private var bankOpen = true

        private var canConnectServer = false

//        fun open(){
//            bankOpen = true
//            instance.config.set(Config.BANK_ENABLE,true)
//            instance.saveConfig()
//        }
//
//        fun close(){
//            bankOpen = false
//            instance.config.set(Config.BANK_ENABLE,false)
//            instance.saveConfig()
//        }

        fun isEnableServer():Boolean{
            return canConnectServer
        }
        //      システム起動
        fun systemSetup():Boolean{

            if (threadPool.isShutdown || threadPool.isTerminated){
                threadPool = Executors.newSingleThreadExecutor()
            }

            Config.load()
            canConnectServer = APIBase.setup()
            //接続に失敗したらこれ以降の読み込みをやめる
            if (!canConnectServer){ return false }

            Status.startStatusTimer()
            ATM.load()
            ServerLoan.setup()
            LocalLoan.setup()
            return true
        }

        //      システム終了
        fun systemClose(){
            threadPool.shutdownNow()
            Status.stopStatusTimer()
        }

    }

    override fun onEnable() {
        // Plugin startup logic

        //変数の登録
        instance = this
        vault = VaultManager(this)

        //システムセットアップ
        systemSetup()
        MenuFramework.setup(this)

        //コマンドの登録
        getCommand("mrevo")!!.setExecutor(ServerLoan)
        getCommand("mcheque")!!.setExecutor(Cheque)
        getCommand("mchequeop")!!.setExecutor(Cheque)
        getCommand("atm")!!.setExecutor(ATM)
        getCommand("mlend")!!.setExecutor(LocalLoan)
        getCommand("mbaltop")!!.setExecutor(TopCommand)
        getCommand("mloantop")!!.setExecutor(TopCommand)
        getCommand("estateinfo")!!.setExecutor(TopCommand)
        getCommand("bankstatus")!!.setExecutor(Status.status)
        getCommand("pay")!!.setExecutor(PayCommand)
        getCommand("mpay")!!.setExecutor(PayCommand)
        BankCommand.labels.forEach { getCommand(it)!!.setExecutor(BankCommand) }
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
