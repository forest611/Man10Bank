package red.man10.man10bank.cheque

import net.kyori.adventure.text.Component
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
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
import red.man10.man10bank.Permissions
import red.man10.man10bank.api.APICheque
import red.man10.man10bank.util.BlockingQueue
import red.man10.man10bank.util.Utility.format
import red.man10.man10bank.util.Utility.msg

object Cheque : CommandExecutor, Listener {

    private fun create(p:Player,amount:Double,isOP:Boolean,note:String = "empty"){

        if (amount<1){
            msg(p,"§c§l1円未満の小切手は作れません")
            return
        }

        if (!Man10Bank.vault.withdraw(p.uniqueId,amount)){
            msg(p,"§c§l電子マネーがありません")
            return
        }

        val id = APICheque.create(p.uniqueId,amount,note,isOP)

        //失敗
        if (id==-1){
            msg(p,"§c§l小切手の発行に失敗しました。時間をおいて作り直してください")
            Man10Bank.vault.deposit(p.uniqueId,amount)
            return
        }

        if (p.inventory.firstEmpty()==-1){
            msg(p,"§c§lインベントリに空きがありません")
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
        if (note != "empty"){
            lore.add(Component.text("§d§lメモ: $note"))
        }
        lore.add(Component.text(""))
        lore.add(Component.text("§e=================="))

        meta.lore(lore)

        meta.addEnchant(Enchantment.DURABILITY,1,true)

        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS)
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE)

        meta.persistentDataContainer.set(NamespacedKey.fromString("cheque_id")!!, PersistentDataType.INTEGER,id)

        chequeItem.itemMeta = meta

        p.inventory.addItem(chequeItem)

        msg(p,"§a§l小切手を作成しました§e(金額:${format(amount)}円)")
    }

    private fun use(p:Player,item:ItemStack){

        val id = getChequeID(item)?:return

        val amount = APICheque.use(id)
        //失敗
        if (amount < 0){
            msg(p,"§c§lこの小切手は使えません")
            return
        }

        Man10Bank.vault.deposit(p.uniqueId,amount)

        item.amount = 0

        msg(p,"§e§l${format(amount)}円の小切手を電子マネーに変えた！")
    }

    private fun getChequeID(item: ItemStack): Int? {
        if (!item.hasItemMeta())return null
        return item.itemMeta.persistentDataContainer[(NamespacedKey.fromString("cheque_id") ?: return null), PersistentDataType.INTEGER]
    }

    @EventHandler
    fun useChequeEvent(e:PlayerInteractEvent){

        if (e.action != Action.RIGHT_CLICK_BLOCK && e.action != Action.RIGHT_CLICK_AIR)return
        if (!e.hasItem())return

        val item = e.item?:return

        if (getChequeID(item) == null)return

        e.isCancelled = true

        if (!e.player.hasPermission(Permissions.USE_CHEQUE)){
            msg(e.player,"§cあなたは小切手をお金に変える権限がありません")
            return
        }

        BlockingQueue.addTask {
            use(e.player,item)
        }

    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {

        if (label != "mcheque" && label!="mchequeop"){
            return false
        }

        if (sender !is Player)return true

        if (args.isEmpty()){//mcheque amount note
            msg(sender,"§a§l/mcheque <金額> <メモ>")
            return true
        }

        if (!sender.hasPermission(Permissions.ISSUE_CHEQUE)){
            msg(sender,"§a§l権限がありません")
            return true
        }

        val isOp = label=="mchequeop"

        if (isOp && !sender.hasPermission(Permissions.ISSUE_CHEQUE_OP)){
            msg(sender,"§a§l権限がありません")
            return true
        }

        val amount = args[0].toDoubleOrNull()

        if (amount == null){
            msg(sender,"§a§l数字で入力してください")
            return true
        }

        val note = if (args.size>=2) args[1] else "empty"

        BlockingQueue.addTask {
            create(sender,amount,isOp,note)
        }

        return false
    }

}