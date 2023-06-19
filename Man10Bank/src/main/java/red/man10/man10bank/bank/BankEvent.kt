package red.man10.man10bank.bank

import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryCloseEvent
import org.bukkit.event.inventory.InventoryType
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import red.man10.man10bank.history.EstateHistory

object BankEvent : Listener{

    @EventHandler
    fun login(e: PlayerJoinEvent){
        val p = e.player

        Thread{
            Thread.sleep(3000)
            BankCommand.showBalance(p, p)
        }.start()
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
}