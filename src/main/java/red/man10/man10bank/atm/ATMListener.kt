package red.man10.man10bank.atm

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import red.man10.man10bank.Man10Bank.Companion.format
import red.man10.man10bank.Man10Bank.Companion.sendMsg
import red.man10.man10bank.atm.ATMInventory.InventoryID.*

object ATMListener : Listener {

    @EventHandler
    fun inventoryClickEvent(e:InventoryClickEvent){

        val p = e.whoClicked
        if (p !is Player)return

        val slot = e.slot
        val id = ATMInventory.menuMap[p.uniqueId]?:return

        when(id){

            MAIN_MENU ->{
                e.isCancelled = true

                if (slot in 10..12){ATMInventory.openDepositMenu(p) }
                if (slot in 14..16){ATMInventory.openWithdrawMenu(p)}

                return
            }

            WITHDRAW_MENU ->{

                e.isCancelled = true

                val item = e.currentItem?:return

                ATMData.withdraw(p,item)

                return
            }

            DEPOSIT_MENU ->{

                if (slot in 48..50){

                    var amount = 0.0

                    for (item in e.inventory.contents){
                        amount += ATMData.deposit(p,item)
                    }

                    if (amount > 0.0){
                        sendMsg(p,"§e§l${format(amount)}円預け入れました！")
                    }

                    p.closeInventory()

                    return
                }
            }
        }
    }


    @EventHandler
    fun inventoryCloseEvent(e:InventoryCloseEvent){

        val p = e.player as Player

        if (ATMInventory.menuMap[p.uniqueId] == DEPOSIT_MENU){
            var amount = 0.0

            for (item in e.inventory.contents){
                amount += ATMData.deposit(p,item)
            }

            if (amount > 0.0){
                sendMsg(p,"§e§l${format(amount)}円預け入れました！")
            }
        }

        ATMInventory.menuMap.remove(p.uniqueId)
    }

}