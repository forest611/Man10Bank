package red.man10.man10bank.atm

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import red.man10.man10bank.Man10Bank
import red.man10.man10bank.Man10Bank.Companion.vault
import red.man10.man10bank.atm.ATMInventory.InventoryID.*
import java.util.*
import kotlin.collections.HashMap

object ATMInventory {

    val menuMap = mutableMapOf<UUID,InventoryID>()
    val slots = arrayOf(10,11,12,14,15,16)

    enum class InventoryID{
        MAIN_MENU,
        WITHDRAW_MENU,
        DEPOSIT_MENU
    }

    fun openMainMenu(p:Player){

        val inv = Bukkit.createInventory(null,27, Component.text("§d§lMa§f§ln§a§l10§e§l[ATM]"))

        for(i in 0..26){
            inv.setItem(i, ItemStack(Material.GRAY_STAINED_GLASS_PANE))
        }

        val deposit = ItemStack(Material.CHEST)
        val dMeta = deposit.itemMeta
        dMeta.displayName(Component.text("§9§l現金を預ける"))
        dMeta.lore(mutableListOf(Component.text("§e§l電子マネー:§b§l${Man10Bank.format(vault.getBalance(p.uniqueId))}").asComponent()))
        deposit.itemMeta = dMeta

        inv.setItem(10,deposit)
        inv.setItem(11,deposit)
        inv.setItem(12,deposit)

        val withdraw = ItemStack(Material.DISPENSER)
        val wMeta = withdraw.itemMeta
        wMeta.displayName(Component.text("§9§l現金を引き出す"))
        wMeta.lore(mutableListOf(Component.text("§e§l電子マネー:§b§l${Man10Bank.format(vault.getBalance(p.uniqueId))}").asComponent()))
        withdraw.itemMeta = wMeta

        inv.setItem(14,withdraw)
        inv.setItem(15,withdraw)
        inv.setItem(16,withdraw)

        p.openInventory(inv)
        menuMap[p.uniqueId] = MAIN_MENU
    }

    fun openWithdrawMenu(p:Player){

        val inv = Bukkit.createInventory(null,27, Component.text("§d§lMa§f§ln§a§l10§e§l[ATM]§9現金を引き出す"))

        for(i in 0..26){
            inv.setItem(i, ItemStack(Material.GRAY_STAINED_GLASS_PANE))
        }

        var i = 0

        for (money in  ATMData.moneyAmount.sorted()){

            val item = ATMData.moneyItems[money]!!.clone()
            val lore = item.lore()?: mutableListOf()
            lore.add(Component.text("§e§l電子マネー:§b§l${Man10Bank.format(vault.getBalance(p.uniqueId))}").asComponent())
            item.lore(lore)

            inv.setItem(slots[i],item)

            i ++

        }

        p.openInventory(inv)
        menuMap[p.uniqueId] = WITHDRAW_MENU
    }

    fun openDepositMenu(p:Player){

        val inv = Bukkit.createInventory(null,54, Component.text("§d§lMa§f§ln§a§l10§e§l[ATM]§9現金を預ける"))

        val quit = ItemStack(Material.CYAN_STAINED_GLASS_PANE)
        val qMeta = quit.itemMeta
        qMeta.displayName(Component.text("§b§l現金を預けて閉じる"))
        quit.itemMeta = qMeta

        for (i in 45..53){
            inv.setItem(i,quit)
        }

        p.openInventory(inv)
        menuMap[p.uniqueId] = DEPOSIT_MENU
    }

}