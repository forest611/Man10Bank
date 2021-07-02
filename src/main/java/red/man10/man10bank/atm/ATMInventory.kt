package red.man10.man10bank.atm

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import red.man10.man10bank.Man10Bank

object ATMInventory {

    fun openMainMenu(p:Player){

        val inv = Bukkit.createInventory(null,27, Component.text("§d§lMa§f§ln§a§l10§e§l[ATM]"))

        for(i in 0..26){
            inv.setItem(i, ItemStack(Material.GRAY_STAINED_GLASS_PANE))
        }

        val deposit = ItemStack(Material.CHEST)
        val dMeta = deposit.itemMeta
        dMeta.displayName(Component.text("§9§lアイテム通貨を預ける"))
        dMeta.lore(mutableListOf(Component.text("§e§l所持金").asComponent(),
            Component.text(Man10Bank.format(Man10Bank.vault.getBalance(p.uniqueId))).asComponent()))
        deposit.itemMeta = dMeta

        inv.setItem(10,deposit)
        inv.setItem(11,deposit)
        inv.setItem(12,deposit)

        val withdraw = ItemStack(Material.CHEST)
        val wMeta = withdraw.itemMeta
        wMeta.displayName(Component.text("§9§lアイテム通貨を引き出す"))
        wMeta.lore(mutableListOf(Component.text("§e§l所持金").asComponent(),
            Component.text(Man10Bank.format(Man10Bank.vault.getBalance(p.uniqueId))).asComponent()))
        withdraw.itemMeta = wMeta

        inv.setItem(14,withdraw)
        inv.setItem(15,withdraw)
        inv.setItem(16,withdraw)

        p.openInventory(inv)
    }

    fun openWithdrawMenu(p:Player){

        val inv = Bukkit.createInventory(null,27, Component.text("§d§lMa§f§ln§a§l10§e§l[ATM]§9お金を引き出す"))

        for(i in 0..26){
            inv.setItem(i, ItemStack(Material.GRAY_STAINED_GLASS_PANE))
        }

        p.openInventory(inv)
    }

    fun openDepositMenu(p:Player){

        val inv = Bukkit.createInventory(null,54, Component.text("§d§lMa§f§ln§a§l10§e§l[ATM]§9お金を預ける"))

        val quit = ItemStack(Material.CYAN_STAINED_GLASS_PANE)
        val qMeta = quit.itemMeta
        qMeta.displayName(Component.text("§b§l預けて閉じる"))
        quit.itemMeta = qMeta

        inv.setItem(48,quit)
        inv.setItem(49,quit)
        inv.setItem(50,quit)

        p.openInventory(inv)

    }

}