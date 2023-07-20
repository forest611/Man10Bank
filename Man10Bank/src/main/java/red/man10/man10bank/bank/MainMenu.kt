package red.man10.man10bank.bank

import org.bukkit.Material
import org.bukkit.entity.Player
import red.man10.man10bank.Man10Bank.Companion.vault
import red.man10.man10bank.util.MenuFramework
import red.man10.man10bank.util.Utility.format
import red.man10.man10bank.util.Utility.msg

class MainMenu(p:Player) : MenuFramework(p,27,"§d§lMa§f§ln§a§l10§e§l[ATM](現金を扱う)") {

    override fun init() {

        val back = Button(Material.GRAY_STAINED_GLASS_PANE)
        back.setClickAction{
            it.isCancelled = true
        }
        fill(back)

        val youtubeLink = Button(Material.WOODEN_SHOVEL)
        youtubeLink.cmd(7)
        youtubeLink.title("§b§l§nATMのつかいかた")

        youtubeLink.setClickAction{
            p.closeInventory()
            msg(p,"§e§lクリックしてYouTubeをみる>>> §b§l§nhttps://youtu.be/HK6VTlNzCX4?t=89")
        }

        setButton(youtubeLink,22)

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