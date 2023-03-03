package red.man10.man10bank.bank

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import red.man10.man10bank.Man10Bank.Companion.instance
import red.man10.man10bank.Man10Bank.Companion.vault
import red.man10.man10bank.api.History
import red.man10.man10bank.util.Utility.msg
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object ATM :CommandExecutor{

    val moneyItems = ConcurrentHashMap<Double,ItemStack>()
    val moneyAmount = arrayOf(10.0,100.0,1000.0,10000.0,100000.0,1000000.0)
    private val itemKey = "money"

    fun load(){
        for (amount in moneyAmount){
            moneyItems[amount] = instance.config.getItemStack("money.${amount}")?: ItemStack(Material.STONE)
        }
        Bukkit.getLogger().info("現金を読み込みました")
    }

    fun setCashItem(itemStack: ItemStack,amount:Double){

        if (!moneyAmount.contains(amount))return

        val meta = itemStack.itemMeta
        meta.persistentDataContainer.set(NamespacedKey.fromString(itemKey)!!, PersistentDataType.DOUBLE,amount)
        itemStack.itemMeta = meta

        moneyItems[amount] = itemStack

        Thread{
            instance.config.set("money.${amount}",itemStack)
            instance.saveConfig()
        }.start()
    }

    fun getMoneyAmount(itemStack: ItemStack?):Double{

        if (itemStack ==null ||itemStack.type == Material.AIR)return 0.0
        if (!itemStack.hasItemMeta())return -1.0

        val ret = itemStack.itemMeta.persistentDataContainer[NamespacedKey.fromString(itemKey)!!, PersistentDataType.DOUBLE]?:return -1.0

        if (ret != -1.0){ return ret*itemStack.amount }

        return 0.0
    }

    fun deposit(p:Player,itemStack: ItemStack):Double{

        val amount = getMoneyAmount(itemStack)

        if (amount > 0){
            itemStack.amount = 0
            vault.deposit(p.uniqueId,amount)

            History.addATMLog(History.ATMLog(0,p.name,p.uniqueId.toString(),amount,true, Date()))
        }

        return amount

    }

    fun withdraw(p: Player, itemStack: ItemStack){

        val amount = getMoneyAmount(itemStack)

        if (!moneyAmount.contains(amount))return

        if (p.inventory.firstEmpty()==-1) {
            msg(p, "§c§lインベントリが満タンです！")
            return
        }

        if (vault.withdraw(p.uniqueId,amount)){
            p.inventory.addItem(moneyItems[amount]!!)
            History.addATMLog(History.ATMLog(0,p.name,p.uniqueId.toString(),amount,false, Date()))
        }
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {

        if (label != "atm")return true

        


        return true
    }
}