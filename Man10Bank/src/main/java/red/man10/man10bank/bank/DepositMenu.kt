package red.man10.man10bank.bank

import org.bukkit.Material
import org.bukkit.entity.Player
import red.man10.man10bank.util.MenuFramework
import red.man10.man10bank.util.Utility
import red.man10.man10bank.util.Utility.format

class DepositMenu(p:Player) : MenuFramework(p,54,"§d§lMa§f§ln§a§l10§e§l[ATM]§9現金を電子マネーにチャージ"){

    override fun init() {

        val closeButton = Button(Material.CYAN_STAINED_GLASS_PANE)
        closeButton.title("§b§lチャージして閉じる")

        //クリックして現金を電子マネーに変える
        closeButton.setClickAction{

            val player = p
            var amount = 0.0
            var hasItem = false

            for (i in 0..44){
                val item = it.inventory.getItem(i)?:continue
                val a = ATM.deposit(player,item)
                if (a==0.0)hasItem = true
                amount+=a
            }

            if (amount!=0.0)Utility.msg(player,"§e§l${format(amount)}円チャージしました！")

            if (!hasItem){
                player.closeInventory()
            }
        }

        setCloseAction{
            val player = p
            var amount = 0.0

            for (i in 0..44){
                val item = it.inventory.getItem(i)?:continue
                val a = ATM.deposit(player,item)
                amount+=a
            }

            if (amount!=0.0)Utility.msg(player,"§e§l${format(amount)}円チャージしました！")
        }

        for (i in 45..53){
            setButton(closeButton,i)
        }
    }

}