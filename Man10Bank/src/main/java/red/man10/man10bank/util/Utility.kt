package red.man10.man10bank.util

import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.text.Normalizer

object Utility{

    const val prefix = "§l[§e§lMan10Bank§f§l]"

    fun msg(p: Player, msg: String) {
        p.sendMessage(prefix + msg)
    }

    fun msg(sender: CommandSender,msg:String){
        sender.sendMessage(prefix + msg)
    }

    fun format(amount: Double, digit: Int = 0): String {
        return String.format("%,.${digit}f", amount)
    }

    fun ZenkakuToHankaku(number: String): Double {
        val normalize = Normalizer.normalize(number, Normalizer.Form.NFKC)
        return normalize.toDoubleOrNull() ?: return -1.0
    }

}