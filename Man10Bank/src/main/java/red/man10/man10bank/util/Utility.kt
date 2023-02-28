package red.man10.man10bank.util

import org.bukkit.entity.Player

object Utility{

    const val prefix = "§l[§e§lMan10Bank§f§l]"

    fun msg(p: Player?, msg: String) {
        p?.sendMessage(prefix + msg)
    }

    fun format(amount: Double, digit: Int = 0): String {
        return String.format("%,.${digit}f", amount)
    }

}