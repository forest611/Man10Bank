package red.man10.man10bank

import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.event.ClickEvent
import org.apache.commons.lang.math.NumberUtils
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10bank.MySQLManager.Companion.mysqlQueue
import red.man10.man10bank.atm.ATMData
import red.man10.man10bank.atm.ATMInventory
import red.man10.man10bank.atm.ATMListener
import red.man10.man10bank.cheque.Cheque
import red.man10.man10bank.cheque.ChequeCommand
import red.man10.man10bank.command.BankCommand
import red.man10.man10bank.history.EstateData
import red.man10.man10bank.loan.*
import red.man10.man10score.ScoreDatabase
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.UUID
import kotlin.math.abs
import kotlin.math.floor


class Man10Bank : JavaPlugin(),Listener {

    companion object{
        const val prefix = "§l[§e§lMan10Bank§f§l]"

        lateinit var vault : VaultManager

        lateinit var plugin : Man10Bank

        var kickDunce = false

        var isInstalledShop = false

        val localLoanDisableWorlds=ArrayList<String>()

        fun sendMsg(p:Player,msg:String){
            p.sendMessage(prefix+msg)
        }

        fun format(double: Double):String{
            return String.format("%,.0f",double)
        }

        const val OP = "man10bank.op"
        const val USE_CHEQUE = "man10bank.use_cheque"
        const val ISSUE_CHEQUE = "man10bank.issue_cheque"

        var bankEnable = true

        var loanFee : Double = 1.0
        var loanRate : Double = 1.0
        var loanMax : Double = 10000000.0
        var enableLocalLoan : Boolean = false

        var paymentThread = false
        var loggingServerHistory = false

        var workWorld : Location? = null

        val loadedPlayerUUIDs=ArrayList<UUID>()
    }

    private lateinit var bankCommand: BankCommand

    override fun onEnable() {
        // Plugin startup logic

        plugin = this

        saveDefaultConfig()

        mysqlQueue()

        vault = VaultManager(this)

        loadConfig()

        server.pluginManager.registerEvents(this,this)
        server.pluginManager.registerEvents(Event(),this)
        server.pluginManager.registerEvents(ATMListener,this)
        server.pluginManager.registerEvents(Cheque,this)

        getCommand("mlend")!!.setExecutor(LocalLoanCommand())
        getCommand("mrevo")!!.setExecutor(ServerLoanCommand())

        bankCommand = BankCommand(this)
        val executor = bankCommand
        arrayOf("bal","balance","money","bank","mbal","atm","mpay","mbaltop","mloantop","pay","deposit","withdraw","ballog").forEach {
            getCommand(it)?.setExecutor(executor)
        }

        val chequeCommand = ChequeCommand(this)
        arrayOf("mchequeop", "mcheque").forEach { getCommand(it)?.setExecutor(chequeCommand) }

    }

    override fun onDisable() {
        // Plugin shutdown logic
        mysqlQueue.add("quit")
        Bukkit.getScheduler().cancelTasks(this)
    }

    private fun loadConfig(){

        reloadConfig()

        loanFee = config.getDouble("mlendFee",0.1)
        loanMax = config.getDouble("mlendMax",10000000.0)
        loanRate = config.getDouble("mlendRate",1.0)
        enableLocalLoan = config.getBoolean("enableLocalLoan",false)

        loggingServerHistory = config.getBoolean("loggingServerHistory",false)
        paymentThread = config.getBoolean("paymentThread",false)

        kickDunce = config.getBoolean("kickDunce",false)

        isInstalledShop = config.getBoolean("isInstalledShop", true)

        workWorld = config.getLocation("workWorld",null)

        localLoanDisableWorlds.clear()
        localLoanDisableWorlds.addAll(config.getStringList("localLoanDisableWorlds"))

        val hasShop = plugin.server.pluginManager.getPlugin("Man10ShopV2")!=null

        if (!hasShop && isInstalledShop){
            Bukkit.getLogger().warning("このサーバーにはMan10ShopV2が導入されていません！")
        }

        ServerLoan.lendParameter = config.getDouble("revolving.lendParameter")
        ServerLoan.borrowStandardScore = config.getInt("revolving.borrowStandardScore")
        ServerLoan.minServerLoanAmount = config.getDouble("revolving.minServerLoan")
        ServerLoan.maxServerLoanAmount = config.getDouble("revolving.maxServerLoan")
        ServerLoan.revolvingFee = config.getDouble("revolving.revolvingFee")
        ServerLoan.lastPaymentCycle = config.getInt("revolving.lastPaymentCycle")
        ServerLoan.isEnable = config.getBoolean("revolving.enable",false)

        for (value in config.getStringList("revolving.maximumOfLoginTime")){
            val time = value.replace(",","").split(":")
            ServerLoan.maximumOfLoginTime[time[0].toInt()] = time[1].toDoubleOrNull()?:0.0
        }

        ATMData.loadItem()
    }

    @EventHandler
    fun login(e:PlayerJoinEvent){

        val p = e.player

        Bank.loginProcess(p)

        Bukkit.getScheduler().runTaskAsynchronously(this, Runnable Thread@{
            Thread.sleep(3000)
            bankCommand.showBalance(p, p)

            if(server.onlinePlayers.contains(p)) loadedPlayerUUIDs.add(p.uniqueId)
        })
    }

    @EventHandler (priority = EventPriority.LOWEST)
    fun logout(e:PlayerQuitEvent){


        loadedPlayerUUIDs.remove(e.player.uniqueId)

        Bukkit.getScheduler().runTaskAsynchronously(this, Runnable { EstateData.saveCurrentEstate(e.player) })
    }

    @EventHandler
    fun closeEnderChest(e:InventoryCloseEvent){

        if (e.inventory.type != InventoryType.ENDER_CHEST)return

        val p = e.player as Player

        Bukkit.getScheduler().runTaskAsynchronously(this, Runnable { EstateData.saveCurrentEstate(p) })
    }
}