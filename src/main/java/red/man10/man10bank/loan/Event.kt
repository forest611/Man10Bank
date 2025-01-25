package red.man10.man10bank.loan

import com.destroystokyo.paper.event.player.PlayerElytraBoostEvent
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.NamespacedKey
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.persistence.PersistentDataType
import red.man10.man10bank.Man10Bank
import red.man10.man10bank.Man10Bank.Companion.plugin
import red.man10.man10bank.Man10Bank.Companion.sendMsg
import java.util.*

class Event : Listener{

    @EventHandler
    fun clickNote(e:PlayerInteractEvent){

        if (!e.hasItem())return
        if (e.action != Action.RIGHT_CLICK_AIR)return
        if (e.hand != EquipmentSlot.HAND)return

        val item = e.item?:return
        val p = e.player

        if (!item.hasItemMeta())return
        val id = item.itemMeta.persistentDataContainer[NamespacedKey(plugin,"id"), PersistentDataType.INTEGER]?:return

        e.isCancelled = true

        Thread{
            val data = LoanData.lendMap[id]?:LoanData().load(id)?:return@Thread
            Bukkit.getLogger().info("${p.name}が${id}の手形を使用しました")
            data.payback(p,item)
        }.start()

    }

    @EventHandler
    fun login(e:PlayerJoinEvent){

        val p = e.player

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable Thread@{

            Thread.sleep(5000)

            if (!ServerLoan.isLoser(p))return@Thread

            if (Man10Bank.kickDunce){
                Bukkit.getScheduler().runTask(plugin,Runnable{
                    p.kick(Component.text("§c§lあなたは§e[§8§lLoser§e]§c§lなのでこのワールドに入れません！"))
                })
                return@Thread
            }

            Bukkit.getScheduler().runTask(plugin,Runnable MainTask@{

                if (Man10Bank.workWorld !=null){
                    sendMsg(p,"§c§lあなたは§e[§8§lLoser§e]§c§lなので§7§l強制労働施設§c§lに転送されました！")
                    p.teleport(Man10Bank.workWorld!!)
                    return@MainTask
                }

                if (!p.hasPermission("man10bank.loser")){
                    sendMsg(p,"§c§lあなたは借金の支払いをせずにスコアが0を下回っているので、§e[§8§lLoser§e]§c§lになっています！ ")

                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(),"lp user ${p.name} parent add loser")
                }
            })
        })

    }


    @EventHandler
    fun worldChange(e:PlayerChangedWorldEvent){

        val p = e.player

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {

            if (!ServerLoan.isLoser(p))return@Runnable

            Bukkit.getScheduler().runTask(plugin,Runnable MainTask@{

                if (Man10Bank.workWorld !=null){
                    sendMsg(p,"§c§lあなたは§e[§8§lLoser§e]§c§lなので§7§l強制労働施設§c§lに転送されました！")
                    p.teleport(Man10Bank.workWorld!!)
                    return@MainTask
                }
            })

        })
    }

    @EventHandler
    fun elytraEvent(e:PlayerElytraBoostEvent){

        val p = e.player

        if (p.world.name == (Man10Bank.workWorld?.world?.name ?: "null")){
            e.isCancelled =  true
        }

    }
}