package red.man10.man10bank.bank

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import red.man10.man10bank.Man10Bank
import red.man10.man10bank.Permissions
import red.man10.man10bank.history.EstateHistory

object BankEvent : Listener{

    @EventHandler
    fun login(e: PlayerJoinEvent){
        val p = e.player

        Man10Bank.coroutineScope.launch(Dispatchers.Default) {
            delay(3000)

            BankCommand.asyncShowBalance(p, p.uniqueId)

            //OPに対しては、ログイン時に稼働状況を表示
            if (e.player.hasPermission(Permissions.BANK_OP_COMMAND)){
                Bukkit.getScheduler().runTask(Man10Bank.instance, Runnable {
                    e.player.performCommand("bankstatus")
                })
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun logout(e: PlayerQuitEvent){
        EstateHistory.asyncAddEstate(e.player)
    }

    @EventHandler
    fun closeEnderChest(e: InventoryCloseEvent){
        if (e.inventory.type != InventoryType.ENDER_CHEST)return
        val p = e.player as Player
        EstateHistory.asyncAddEstate(p)
    }

    @EventHandler
    fun clickCash(e:PlayerInteractEvent){
        if (!e.hasItem())return
        if (e.action != Action.RIGHT_CLICK_AIR && e.action != Action.RIGHT_CLICK_BLOCK)return

        val p = e.player
        val item = e.item!!
        if (ATM.getMoneyAmount(item) == 0.0)return
        e.isCancelled = true
        p.performCommand("atm")
    }
}