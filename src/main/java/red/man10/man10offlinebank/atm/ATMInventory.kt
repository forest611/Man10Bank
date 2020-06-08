package red.man10.man10offlinebank.atm

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import red.man10.man10offlinebank.Man10OfflineBank
import red.man10.man10offlinebank.Man10OfflineBank.Companion.vault

class ATMInventory {

    fun openMainMenu(p:Player){
        val inv = Bukkit.createInventory(null,27,"§e§lMan10Bank")

        val panel = ItemStack(Material.LIGHT_BLUE_STAINED_GLASS_PANE)

        for (i in 0..26){
            inv.addItem(panel)
        }

        val depositIcon = ItemStack(Material.CHEST)
        val depositMeta = depositIcon.itemMeta

        depositMeta.setDisplayName("§9&l口座に入金する")
        val bal = vault.getBalance(p.uniqueId)
        depositMeta.lore = mutableListOf("§e§l現在の所持金:$${String.format("&,.1f",bal)}")

        depositIcon.itemMeta = depositMeta

        inv.setItem(10,depositIcon)
        inv.setItem(11,depositIcon)
        inv.setItem(12,depositIcon)

        val withdrawIcon = ItemStack(Material.DISPENSER)
        val withdrawMeta = withdrawIcon.itemMeta

        withdrawMeta.setDisplayName("§9§l口座から出金する")
        withdrawIcon.lore = mutableListOf("§e§l現在の所持金:$${String.format("&,.1f",bal)}")

        withdrawIcon.itemMeta = withdrawMeta

        inv.setItem(14,withdrawIcon)
        inv.setItem(15,withdrawIcon)
        inv.setItem(16,withdrawIcon)

        p.openInventory(inv)
    }

    fun openDepositMenu(p:Player,amount:Double):Double{

        val inv = Bukkit.createInventory(null,54,"§e§lDeposit")


        return amount

    }

    //cancel:10,enter:11,clear:12
    fun createNumberKey():MutableList<ItemStack>{

        val list = mutableListOf<ItemStack>()

        for (i in 97..106){
            val item = ItemStack(Material.DIAMOND_HOE)
            val meta = item.itemMeta

            meta.setDisplayName("§a§l${i-96}")
            meta.setCustomModelData(i)

            item.itemMeta = meta

            list.add(item)
        }

        val cancelIcon = ItemStack(Material.REDSTONE_BLOCK)
        val cancelMeta = cancelIcon.itemMeta

        cancelMeta.setDisplayName("§4§l戻る")
        cancelIcon.itemMeta = cancelMeta

        val enterIcon = ItemStack(Material.EMERALD_BLOCK)
        val enterMeta = enterIcon.itemMeta

        enterMeta.setDisplayName("§a§l確定")
        enterIcon.itemMeta = enterMeta

        val clearIcon = ItemStack(Material.GOLD_BLOCK)
        val clearMeta = clearIcon.itemMeta

        clearMeta.setDisplayName("§e§l削除")
        clearIcon.itemMeta = clearMeta

        list.add(cancelIcon)
        list.add(enterIcon)
        list.add(clearIcon)

        return list

    }

}