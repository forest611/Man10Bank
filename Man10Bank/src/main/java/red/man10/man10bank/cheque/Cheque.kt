package red.man10.man10bank.cheque

import kotlinx.coroutines.launch
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
import red.man10.man10bank.Man10Bank.Companion.coroutineScope
import red.man10.man10bank.Permissions
import red.man10.man10bank.api.APICheque
import red.man10.man10bank.status.StatusManager
import red.man10.man10bank.util.Utility
import red.man10.man10bank.util.Utility.format
import red.man10.man10bank.util.Utility.msg
import kotlin.math.floor

object Cheque : CommandExecutor, Listener {

    private const val MAX_CHARACTERS = 32

    private suspend fun create(p:Player,amount:Double,isOP:Boolean,note:String = "empty"){

        if (note.length > MAX_CHARACTERS){
            msg(p,"§c§l文字数が多すぎます！")
            return
        }

        val fixedAmount = floor(amount)

        if (fixedAmount<1){
            msg(p,"§c§l1円未満の小切手は作れません")
            return
        }

        if (!isOP && !Man10Bank.vault.withdraw(p.uniqueId,fixedAmount)){
            msg(p,"§c§l電子マネーがありません")
            return
        }

        val id = APICheque.create(p.uniqueId,fixedAmount,note)

        //失敗
        if (id==-1){
            msg(p,"§c§l小切手の発行に失敗しました。時間をおいて作り直してください")
            Man10Bank.vault.deposit(p.uniqueId,fixedAmount)
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
        lore.add(Component.text("§a§l発行者: ${if (isOP)"§e[§4§lGM§e]§c§l" else "§d§l"}${p.name}"))
        lore.add(Component.text("§a§l金額: ${format(fixedAmount)}円"))
        if (note != "null"){
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

        msg(p,"§a§l小切手を作成しました§e(金額:${format(fixedAmount)}円)")
    }

    private suspend fun use(p:Player,item:ItemStack){

        val id = getChequeID(item)?:return

        val amount = APICheque.use(id,p)
        //失敗
        if (amount < 0){
            msg(p,"§c§lこの小切手は使えません")
            return
        }

        Man10Bank.vault.deposit(p.uniqueId,amount)

        item.amount = 0

        msg(p,"§e§l${format(amount)}円の小切手を電子マネーに変えた！")
    }

    //インベとecにある小切手を数える。スレッドで呼ぶ
    suspend fun getChequeInInventory(p:Player):Double{
        var amount = 0.0

        for (item in p.inventory.contents){
            if (item ==null ||item.type == Material.AIR)continue
            val money = getChequeAmount(item)
            amount+=money

            for (i in Utility.getShulkerItem(item)){
                amount+= getChequeAmount(item)

                for (j in Utility.getBundleItem(i)){
                    amount+= getChequeAmount(item)
                }
            }

            for (i in Utility.getBundleItem(item)){
                amount+= getChequeAmount(item)
            }
        }

        for (item in p.enderChest.contents){
            if (item ==null ||item.type == Material.AIR)continue
            val money = getChequeAmount(item)
            amount+=money

            for (i in Utility.getShulkerItem(item)){
                amount+= getChequeAmount(item)

                for (j in Utility.getBundleItem(i)){
                    amount+= getChequeAmount(item)
                }
            }

            for (i in Utility.getBundleItem(item)){
                amount+= getChequeAmount(item)
            }
        }

        return amount
    }

    private fun getChequeID(item: ItemStack): Int? {
        if (!item.hasItemMeta())return null
        return item.itemMeta.persistentDataContainer[(NamespacedKey.fromString("cheque_id") ?: return null), PersistentDataType.INTEGER]
    }

    private suspend fun getChequeAmount(item:ItemStack):Double{
        val id = getChequeID(item) ?: return 0.0
        return APICheque.amount(id)
    }

    @EventHandler
    fun useChequeEvent(e:PlayerInteractEvent){

        if (e.action != Action.RIGHT_CLICK_BLOCK && e.action != Action.RIGHT_CLICK_AIR)return
        if (!e.hasItem())return

        val item = e.item?:return

        if (getChequeID(item) == null)return

        val p = e.player

        e.isCancelled = true

        if (!p.hasPermission(Permissions.USE_CHEQUE)){
            msg(p,"§cあなたは小切手をお金に変える権限がありません")
            return
        }

        if (!StatusManager.status.enableCheque){
            msg(p,"現在メンテナンスにより小切手は使えません")
            return
        }

        coroutineScope.launch { use(p,item) }
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {

        if (label != "mcheque" && label!="mchequeop"){
            return false
        }

        if (!StatusManager.status.enableCheque){
            msg(sender,"現在メンテナンスにより小切手は使えません")
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

        val note = if (args.size>=2) args[1] else "null"

        coroutineScope.launch { create(sender,amount,isOp,note) }

        return false
    }

}