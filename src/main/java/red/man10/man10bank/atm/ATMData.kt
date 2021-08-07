package red.man10.man10bank.atm

import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import red.man10.man10bank.Man10Bank.Companion.es
import red.man10.man10bank.Man10Bank.Companion.plugin
import red.man10.man10bank.Man10Bank.Companion.sendMsg
import red.man10.man10bank.Man10Bank.Companion.vault
import red.man10.man10bank.MySQLManager
import java.util.concurrent.ConcurrentHashMap

object ATMData {

    val moneyItems = ConcurrentHashMap<Double,ItemStack>()
    val moneyAmount = listOf(10.0,100.0,1000.0,10000.0,100000.0,1000000.0)//100〜100万

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

        es.execute {
            plugin.config.set("money.$amount",itemStack)
            plugin.saveConfig()
        }

        return true
    }

    fun getMoneyAmount(itemStack: ItemStack):Double{

        val type = getMoneyType(itemStack)

        if (type != -1.0){
            return type*itemStack.amount
        }

        return 0.0
    }

    //お金じゃなかったら-1を返す
    fun getMoneyType(itemStack: ItemStack):Double{
        if (!itemStack.hasItemMeta())return -1.0

        return itemStack.itemMeta.persistentDataContainer[NamespacedKey.fromString("money")!!, PersistentDataType.DOUBLE]?:return -1.0
    }

    fun deposit(p:Player,itemStack: ItemStack):Double{

        val amount = getMoneyAmount(itemStack)

        if (amount > 0){
            itemStack.amount = 0
            vault.deposit(p.uniqueId,amount)
            addLog(p,amount,true)
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
            addLog(p,amount,false)
        }
    }

    fun getInventoryMoney(p:Player):Double{
        var cash = 0.0

        for (item in p.inventory.contents){
            if (item ==null ||item.type == Material.AIR)continue
            val money = getMoneyAmount(item)
            cash+=money
        }

        return cash
    }

    fun getEnderChestMoney(p:Player):Double{
        var cash = 0.0

        for (item in p.enderChest.contents){
            if (item ==null ||item.type == Material.AIR)continue
            val money = getMoneyAmount(item)
            cash+=money
        }

        return cash
    }

    private fun addLog(p:Player, amount: Double, deposit:Boolean){
        MySQLManager.mysqlQueue.add("INSERT INTO atm_log (player, uuid, amount, deposit, date) " +
                "VALUES ('${p.name}', '${p.uniqueId}', $amount, ${if (deposit) 1 else 0}, DEFAULT)")
    }
}