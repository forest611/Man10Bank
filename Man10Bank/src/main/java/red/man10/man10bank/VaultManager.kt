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
 * Updated by Jin Morikawa on 2023/07/20
 */
class VaultManager(private val plugin: JavaPlugin) {

    init {
        setupEconomy()
    }

    companion object {
        lateinit var economy: Economy
    }
    private fun setupEconomy() {
        Bukkit.getLogger().info("VaultManagerのセットアップ")
        if (plugin.server.pluginManager.getPlugin("Vault") == null) {
            Bukkit.getLogger().info("Vaultが導入されていません")
            return
        }
        val rsp = plugin.server.servicesManager.getRegistration(Economy::class.java)
        if (rsp == null) {
            Bukkit.getLogger().info("セットアップ失敗")
            return
        }
        economy = rsp.provider
        Bukkit.getLogger().info("セットアップ完了")
    }

    /////////////////////////////////////
    //      残高確認
    /////////////////////////////////////
    fun getBalance(uuid: UUID): Double {
        return economy.getBalance(Bukkit.getOfflinePlayer(uuid))
    }

    /////////////////////////////////////
    //      引き出し
    /////////////////////////////////////
    fun withdraw(uuid: UUID, money: Double): Boolean {
        val p = Bukkit.getOfflinePlayer(uuid)
        if (getBalance(uuid)<money)return false
        val resp = economy.withdrawPlayer(p,money)
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
        val resp = economy.depositPlayer(p,money)
        if (resp.transactionSuccess()) {
            if (p.isOnline) {
                Utility.msg(p.player!!,"§e§l電子マネーを${Utility.format(money)}円受け取りました")
            }
            return true
        }
        return false
    }


}