package red.man10.man10bank

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10bank.api.APIBase
import red.man10.man10bank.bank.*
import red.man10.man10bank.cheque.Cheque
import red.man10.man10bank.loan.LocalLoan
import red.man10.man10bank.loan.ServerLoan
import red.man10.man10bank.status.StatusManager
import red.man10.man10bank.util.MenuFramework

class Man10Bank : JavaPlugin() {

    companion object{

        lateinit var instance : Man10Bank
        lateinit var vault : VaultManager

        lateinit var coroutineScope : CoroutineScope

        //      システム起動
        fun systemSetup():Boolean{

            coroutineScope = CoroutineScope(Dispatchers.IO)

            Config.load()
            APIBase.setup()

            StatusManager.startStatusTask()
            ATM.load()
            coroutineScope.launch {
                ServerLoan.setup()
                LocalLoan.setup()
            }
            return true
        }

        //      システム終了
        fun systemClose(){
            coroutineScope.cancel()
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
        getCommand("bankstatus")!!.setExecutor(StatusManager)
        getCommand("pay")!!.setExecutor(PayCommand)
        getCommand("mpay")!!.setExecutor(PayCommand)
        BankCommand.labels.forEach { getCommand(it)!!.setExecutor(BankCommand) }
        DealCommand.labels.forEach { getCommand(it)!!.setExecutor(DealCommand) }


        //イベントの登録
        server.pluginManager.registerEvents(Cheque,this)
        server.pluginManager.registerEvents(MenuFramework.MenuListener,this)
        server.pluginManager.registerEvents(BankEvent,this)
        server.pluginManager.registerEvents(LocalLoan,this)

    }

    override fun onDisable() {
        // Plugin shutdown logic
        systemClose()
        StatusManager.cancelScope()

    }

}
