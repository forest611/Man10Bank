package red.man10.man10bank.cheque

import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
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
import red.man10.man10bank.Man10Bank.Companion.format
import red.man10.man10bank.Man10Bank.Companion.plugin
import red.man10.man10bank.Man10Bank.Companion.sendMsg
import red.man10.man10bank.Man10Bank.Companion.vault
import red.man10.man10bank.MySQLManager

object Cheque :Listener{

    private val mysql = MySQLManager(plugin,"Man10BankCheque")

    @Synchronized
    fun createCheque(p:Player, amount:Double, note:String?, isOP:Boolean){

        if (note !=null && note.length > 20){
            sendMsg(p,"§c§lメモは20文字以内にしてください。")
            return
        }

        val rs = mysql.query("select id from cheque_tbl order by id desc limit 1;")

        if (rs == null){
            sendMsg(p,"§c§l銀行への問い合わせに失敗しました。運営に報告してください。- 01")
            return
        }

        val id = if (rs.next()){ rs.getInt("id")+1 } else 1

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
        lore.add(Component.text("§a§l金額: ${format(amount)}円"))
        if (note !=null){ lore.add(Component.text("§d§lメモ: $note")) }
        lore.add(Component.text(""))
        lore.add(Component.text("§e=================="))

        meta.lore(lore)

        meta.addEnchant(Enchantment.DURABILITY,1,true)

        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE)

        meta.persistentDataContainer.set(NamespacedKey.fromString("cheque_id")!!, PersistentDataType.INTEGER,id)
        meta.persistentDataContainer.set(NamespacedKey.fromString("cheque_amount")!!, PersistentDataType.DOUBLE,amount)

        chequeItem.itemMeta = meta

        val result = mysql.execute("INSERT INTO cheque_tbl (player, uuid, amount, note, date, used) " +
                "VALUES ('${p.name}', '${p.uniqueId}', ${amount}, '${if (note !=null)MySQLManager.escapeStringForMySQL(note) else null}', now(),  DEFAULT);")

        if (!result){
            sendMsg(p,"§c§l銀行への問い合わせに失敗しました。運営に報告してください。- 02")

            //返金
            if (!isOP){
                vault.deposit(p.uniqueId,amount)
            }

            return
        }

        p.inventory.addItem(chequeItem)

        sendMsg(p,"§a§l小切手を作成しました！§e(金額:${format(amount)}円)")

    }

    private fun getChequeID(item:ItemStack):Int{
        if (!item.hasItemMeta())return -1
        return item.itemMeta.persistentDataContainer[NamespacedKey.fromString("cheque_id")
            ?: return -1, PersistentDataType.INTEGER]?:return -1
    }

    fun getChequeAmount(item:ItemStack?):Double{
        if (item ==null ||item.type == Material.AIR)return 0.0

        return item.itemMeta.persistentDataContainer[NamespacedKey.fromString("cheque_amount")!!,
                PersistentDataType.DOUBLE]?:return 0.0
    }

    @Synchronized
    private fun useCheque(p:Player, item:ItemStack){

        val id = getChequeID(item)

        if (id == -1)return

        val rs = mysql.query("select used from cheque_tbl where id=$id;")

        if (rs == null){
            sendMsg(p,"§c§l銀行への問い合わせに失敗しました。運営に報告してください。- 03")
            return
        }

        if (!rs.next() || rs.getInt("used") == 1){
            sendMsg(p,"§c§lこの小切手は使えません")
            return
        }

        val result = mysql.execute("update cheque_tbl set used=1,use_date=now(),use_player='${p.name}' where id=$id;")

        if (!result){
            sendMsg(p,"§c§l銀行への問い合わせに失敗しました。運営に報告してください。- 04")
            return
        }

        val amount = getChequeAmount(item)

        vault.deposit(p.uniqueId,amount)

        item.amount = 0

        sendMsg(p,"§e§l${format(amount)}円の小切手を電子マネーに変えた！")
    }

    @EventHandler
    fun useCheque(e:PlayerInteractEvent){

        if (e.action != Action.RIGHT_CLICK_BLOCK && e.action != Action.RIGHT_CLICK_AIR)return
        if (!e.hasItem())return

        val item = e.item?:return

        if (getChequeID(item) == -1)return

        e.isCancelled = true

        if (!e.player.hasPermission(Man10Bank.USE_CHEQUE)){
            sendMsg(e.player,"§cあなたは小切手をお金に変える権限がありません")
            return
        }


        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable { useCheque(e.player,item) })
    }

}