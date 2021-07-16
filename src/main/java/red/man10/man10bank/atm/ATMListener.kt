package red.man10.man10bank.atm

import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.player.PlayerInteractEvent
import red.man10.man10bank.Man10Bank.Companion.format
import red.man10.man10bank.Man10Bank.Companion.sendMsg
import red.man10.man10bank.atm.ATMInventory.InventoryID.*
import red.man10.man10bank.atm.ATMInventory.menuMap
import red.man10.man10bank.atm.ATMInventory.openDepositMenu
import red.man10.man10bank.atm.ATMInventory.openWithdrawMenu

object ATMListener : Listener {

    @EventHandler
    fun inventoryClickEvent(e:InventoryClickEvent){

        val p = e.whoClicked
        if (p !is Player)return

        val slot = e.slot
        val id = menuMap[p.uniqueId]?:return

        when(id){

            MAIN_MENU ->{
                e.isCancelled = true

                if (slot in 10..12){
                    openDepositMenu(p) }
                if (slot in 14..16){
                    openWithdrawMenu(p)
                }

                return
            }

            WITHDRAW_MENU ->{

                e.isCancelled = true

                val item = e.currentItem?:return

                ATMData.withdraw(p,item)

                openWithdrawMenu(p)

                return
            }

            DEPOSIT_MENU ->{

                if (slot >= 45){

                    e.isCancelled = true

                    var amount = 0.0

                    var hasAnyItem = false

                    for (i in 0..44){
                        val item = e.inventory.getItem(i)
                        if (item ==null ||item.type == Material.AIR)continue

                        val depositAmount = ATMData.deposit(p,item)

                        if (depositAmount==0.0){
                            hasAnyItem = true
                            continue
                        }

                        amount += depositAmount
                    }

                    if (amount > 0.0){
                        sendMsg(p,"§e§l${format(amount)}円チャージしました！")
                    }

                    if (hasAnyItem){
                        sendMsg(p,"§c§l現金以外のアイテムが残っています！")
                        return
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

        if (menuMap[p.uniqueId] == DEPOSIT_MENU){
            var amount = 0.0

            for (item in e.inventory.contents){
                if (item ==null ||item.type == Material.AIR)continue
                amount += ATMData.deposit(p,item)
            }

            if (amount > 0.0){
                sendMsg(p,"§e§l${format(amount)}円チャージしました！")
            }
        }

        if (menuMap.containsKey(p.uniqueId)){
            menuMap.remove(p.uniqueId)
        }
    }
    
    @EventHandler
    fun moneyClickEvent(e:PlayerInteractEvent){

        if (!e.hasItem())return
        if (e.action != Action.RIGHT_CLICK_BLOCK && e.action!=Action.RIGHT_CLICK_AIR)return

        val item = e.item?:return

        if (ATMData.getMoneyType(item) != -1.0){
            e.player.performCommand("atm")
        }

    }
}