package red.man10.man10bank

import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10bank.util.Utility
import java.util.*


/**
 * Created by takatronix on 2017/03/04.
 */
class VaultManager(private val plugin: JavaPlugin) {
    private fun setupEconomy(): Boolean {
        Bukkit.getLogger().info("setupEconomy")
        if (plugin.server.pluginManager.getPlugin("Vault") == null) {
            Bukkit.getLogger().info("Vault plugin is not installed")
            return false
        }
        val rsp = plugin.server.servicesManager.getRegistration(Economy::class.java)
        if (rsp == null) {
            Bukkit.getLogger().info("Can't get vault service")
            return false
        }
        economy = rsp.provider
        Bukkit.getLogger().info("Economy setup")
        return economy != null
    }

    /////////////////////////////////////
    //      残高確認
    /////////////////////////////////////
    fun getBalance(uuid: UUID?): Double {

        return economy!!.getBalance(Bukkit.getOfflinePlayer(uuid!!).name)
    }

    /////////////////////////////////////
    //      引き出し
    /////////////////////////////////////
    fun withdraw(uuid: UUID, money: Double): Boolean {
        val p = Bukkit.getOfflinePlayer(uuid)
        val resp = economy!!.withdrawPlayer(p.name, money)
        if (resp.transactionSuccess()) {
            if (p.isOnline) {
                Utility.msg(p.player!!,"§e§l電子マネーを${Utility.format(money)}円支払いました")
            }
            return true
        }
        return false
    }

    /////////////////////////////////////
    //      お金を入れる
    /////////////////////////////////////
    fun deposit(uuid: UUID, money: Double): Boolean {
        val p = Bukkit.getOfflinePlayer(uuid)
        val resp = economy!!.depositPlayer(p.name, money)
        if (resp.transactionSuccess()) {
            if (p.isOnline) {
                Utility.msg(p.player!!,"§e§l電子マネーを${Utility.format(money)}円受け取りました")
            }
            return true
        }
        return false
    }

    companion object {
        var economy: Economy? = null
    }

    init {
        setupEconomy()
    }
}