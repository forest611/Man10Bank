package red.man10.man10bank.cheque

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.ItemFlag
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import red.man10.man10bank.Man10Bank
import red.man10.man10bank.Man10Bank.Companion.plugin
import red.man10.man10bank.Man10Bank.Companion.sendMsg
import red.man10.man10bank.Man10Bank.Companion.vault
import red.man10.man10bank.MySQLManager

object Cheque :Listener{

    private val mysql = MySQLManager(Man10Bank.plugin,"Man10BankCheque")

    fun createCheque(p:Player, amount:Double, note:String?, isOP:Boolean){

        val rs = mysql.query("select id from cheque_tbl order by id desc limit 1;")?:return

        val id = if (rs.next()){ rs.getInt("id")+1 }else 1

        if (!isOP && !vault.withdraw(p.uniqueId,amount)){
            sendMsg(p,"§c§l電子マネーがありません！")
            return
        }

        val chequeItem = ItemStack(Material.PAPER)
        val meta = chequeItem.itemMeta
        meta.setCustomModelData(1)

        meta.displayName(Component.text("§b§l小切手§7§l(Cheque)"))

        val lore = mutableListOf<Component>()

        lore.add(Component.text("§e====[Man10Bank]===="))
        lore.add(Component.text(""))
        lore.add(Component.text("§a§l発行者: ${if (isOP)"§c§l" else "§d§l"}${p.name}"))
        lore.add(Component.text("§a§l金額: ${Man10Bank.format(amount)}円"))
        if (note !=null){
            lore.add(Component.text("§d§lメモ: $note"))
        }
        lore.add(Component.text(""))
        lore.add(Component.text("§e=================="))

        meta.lore(lore)

        meta.addEnchant(Enchantment.DURABILITY,0,false)

        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE)

        meta.persistentDataContainer.set(NamespacedKey.fromString("cheque_id")!!, PersistentDataType.INTEGER,id)
        meta.persistentDataContainer.set(NamespacedKey.fromString("cheque_amount")!!, PersistentDataType.DOUBLE,amount)

        chequeItem.itemMeta = meta

        mysql.execute("INSERT INTO cheque_tbl (player, uuid, amount, note, date, used) " +
                "VALUES ('${p.name}', '${p.uniqueId}', ${amount}, now(), $note, DEFAULT);")

        p.inventory.addItem(chequeItem)

    }

    private fun getChequeID(item:ItemStack):Int{
        if (!item.hasItemMeta())return -1
        return item.itemMeta.persistentDataContainer[NamespacedKey.fromString("cheque_id")
            ?: return -1, PersistentDataType.INTEGER]?:return -1
    }

    fun useCheque(p:Player,item:ItemStack){

        val id = getChequeID(item)

        if (id == -1)return

        val rs = mysql.query("select used from cheque_tbl where id=$id;")?:return

        rs.next()

        if (rs.getInt("used") == 1){
            sendMsg(p,"§c§lこの小切手は使えません")
            return
        }

        mysql.execute("update cheque_tbl set used=1 where id=$id;")

        val amount = item.itemMeta.persistentDataContainer[NamespacedKey.fromString("cheque_amount")!!,
                PersistentDataType.DOUBLE]!!

        vault.deposit(p.uniqueId,amount)

        item.amount = 0
    }

    @EventHandler
    fun useCheque(e:PlayerInteractEvent){

        if (e.action != Action.RIGHT_CLICK_BLOCK && e.action != Action.RIGHT_CLICK_AIR)return
        if (!e.hasItem())return

        val item = e.item?:return

        if (getChequeID(item) == -1)return

        e.isCancelled = true

        plugin.es.execute { useCheque(e.player,item) }
    }

}