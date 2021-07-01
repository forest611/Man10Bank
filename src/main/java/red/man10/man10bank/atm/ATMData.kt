package red.man10.man10bank.atm

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import red.man10.man10bank.Man10Bank.Companion.plugin
import java.util.concurrent.ConcurrentHashMap

object ATMData {

    val moneyItem = ConcurrentHashMap<Double,ItemStack>()
    private val moneyAmount = listOf(10000.0,100000.0,1000000.0,10000000.0,100000000.0)//1万〜1億

    fun loadItem(){

        for (money in moneyAmount){

            moneyItem[money] = plugin.config.getItemStack("$money")?: ItemStack(Material.STONE)

        }

    }

    fun setItem(itemStack: ItemStack,amount: Double){

        if (!moneyAmount.contains(amount))return

        val meta = itemStack.itemMeta
        meta.persistentDataContainer.set(NamespacedKey.fromString("money")!!, PersistentDataType.STRING,"$amount")
        itemStack.itemMeta = meta

        moneyItem[amount] = itemStack

        plugin.es.execute {
            plugin.config.set("$amount",itemStack)
            plugin.saveConfig()
        }

    }

    fun getMoneyAmount(itemStack: ItemStack):Double{

        val type = getMoneyType(itemStack)

        if (type != -1.0){
            return type*itemStack.amount
        }

        return 0.0
    }

    fun getMoneyType(itemStack: ItemStack):Double{

        for (item in moneyItem){
            if (item.value.isSimilar(itemStack))return item.key
        }

        return -1.0

    }

}