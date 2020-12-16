package red.man10.man10bank.loan

import org.bukkit.NamespacedKey
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.persistence.PersistentDataType
import red.man10.man10bank.Man10Bank

class Event : Listener{

    @EventHandler
    fun clickNote(e:PlayerInteractEvent){

        if (!e.hasItem())return
        if (e.action != Action.RIGHT_CLICK_AIR)return
        if (e.hand != EquipmentSlot.HAND)return

        val item = e.item?:return
        val p = e.player

        if (!item.hasItemMeta())return

        val id = item.itemMeta.persistentDataContainer[NamespacedKey(Man10Bank.plugin,"id"), PersistentDataType.INTEGER]?:return

        p.inventory.remove(item)

        Thread{
            val data = LoanData.lendMap[id]?:LoanData().load(id)?:return@Thread

            data.payback(p)
        }.start()

    }

}