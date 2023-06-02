package red.man10.man10bank.util

import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.text.Normalizer

object Utility{

    const val prefix = "§l[§e§lMan10Bank§f§l]§f"

    fun msg(p: Player, msg: String) {
        p.sendMessage(prefix + msg)
    }

    fun msg(sender: CommandSender,msg:String){
        sender.sendMessage(prefix + msg)
    }

    fun format(amount: Double, digit: Int = 0): String {
        return String.format("%,.${digit}f", amount)
    }

    fun ZenkakuToHankaku(number: String): Double? {
        val normalize = Normalizer.normalize(number, Normalizer.Form.NFKC)
        return normalize.toDoubleOrNull()
    }

    fun parse(str:String) : Double? {
        return ZenkakuToHankaku(str.replace(",",""))
    }

    fun loggerInfo(str: String?){
        Bukkit.getLogger().info("[Man10Bank]$str")
    }

    fun loggerWarn(str: String?){
        Bukkit.getLogger().warning("[Man10Bank]$str")
    }

}