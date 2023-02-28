package red.man10.man10bank.bank

import org.bukkit.Material
import org.bukkit.entity.Player
import red.man10.man10bank.Man10Bank.Companion.vault
import red.man10.man10bank.util.MenuFramework
import red.man10.man10bank.util.Utility.format

class MainMenu(p:Player) : MenuFramework(p,27,"§d§lMa§f§ln§a§l10§e§l[ATM](現金を扱う)") {

    init {

        val back = Button(Material.GRAY_STAINED_GLASS_PANE)
        fill(back)

        val depositButton = Button(Material.CHEST)
        depositButton.setClickAction{
            DepositMenu(p).open()
        }
        depositButton.title("§9§l現金を電子マネーにチャージ")
        depositButton.lore(mutableListOf("§e§l電子マネー:§b§l${format(vault.getBalance(p.uniqueId))}",))

        setButton(depositButton,10)
        setButton(depositButton,11)
        setButton(depositButton,12)

        val withdrawButton = Button(Material.DISPENSER)
        withdrawButton.setClickAction{
            WithdrawMenu(p).open()
        }
        withdrawButton.title("§9§l現金を電子マネーを現金にする")
        withdrawButton.lore(mutableListOf("§e§l電子マネー:§b§l${format(vault.getBalance(p.uniqueId))}",))

        setButton(withdrawButton,14)
        setButton(withdrawButton,15)
        setButton(withdrawButton,16)

    }


}