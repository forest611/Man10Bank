package red.man10.man10bank.bank

import org.bukkit.Material
import org.bukkit.entity.Player
import red.man10.man10bank.util.MenuFramework

class WithdrawMenu(p:Player) : MenuFramework(p,27,"§d§lMa§f§ln§a§l10§e§l[ATM]§9現金をクリックして引き出す"){

    private val slot = arrayOf(10,11,12,13,14,15,16,17)

    override fun init() {

        val back = Button(Material.GRAY_STAINED_GLASS_PANE)
        fill(back)

        for ((i, money) in ATM.moneyAmount.sortedArray().withIndex()){
            val moneyItem = ATM.moneyItems[money]!!
            val m = Button(moneyItem.type)
            m.fromItemStack(moneyItem)
            m.setClickAction{
                ATM.withdraw((it.whoClicked as Player),ATM.moneyAmount[i])
            }
            setButton(m,slot[i])

        }
    }
}