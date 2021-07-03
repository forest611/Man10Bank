package red.man10.man10bank.atm

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import red.man10.man10bank.Man10Bank.Companion.plugin
import red.man10.man10bank.Man10Bank.Companion.sendMsg
import red.man10.man10bank.Man10Bank.Companion.vault
import java.util.concurrent.ConcurrentHashMap

object ATMData {

    val moneyItems = ConcurrentHashMap<Double,ItemStack>()
    val moneyAmount = listOf(10000.0,100000.0,1000000.0,10000000.0,100000000.0)//1万〜1億

    fun loadItem(){
        for (money in moneyAmount){
            moneyItems[money] = plugin.config.getItemStack("money.$money")?: ItemStack(Material.STONE)
        }
    }

    fun setItem(itemStack: ItemStack,amount: Double):Boolean{

        if (!moneyAmount.contains(amount))return false

        val meta = itemStack.itemMeta
        meta.persistentDataContainer.set(NamespacedKey.fromString("money")!!, PersistentDataType.DOUBLE,amount)
        itemStack.itemMeta = meta

        moneyItems[amount] = itemStack

        plugin.es.execute {
            plugin.config.set("money.$amount",itemStack)
            plugin.saveConfig()
        }

        return true
    }

    private fun getMoneyAmount(itemStack: ItemStack):Double{

        val type = getMoneyType(itemStack)

        if (type != -1.0){
            return type*itemStack.amount
        }

        return 0.0
    }

    //お金じゃなかったら-1を返す
    private fun getMoneyType(itemStack: ItemStack):Double{
        return itemStack.itemMeta.persistentDataContainer[NamespacedKey.fromString("money")!!, PersistentDataType.DOUBLE]?:return -1.0
    }

    fun deposit(p:Player,itemStack: ItemStack):Double{

        val amount = getMoneyAmount(itemStack)

        itemStack.amount = 0

        if (itemStack.amount != 0){
            vault.deposit(p.uniqueId,amount)
        }

        return amount

    }

    fun withdraw(p:Player,itemStack: ItemStack){

        val amount = getMoneyAmount(itemStack)

        if (!moneyAmount.contains(amount))return

        if (p.inventory.firstEmpty()==-1) {
            sendMsg(p, "§c§lインベントリが満タンです！")
            return
        }

        if (vault.withdraw(p.uniqueId,amount)){
            p.inventory.addItem(moneyItems[amount]!!)
        }
    }
}