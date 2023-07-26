package red.man10.man10bank.bank

import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import red.man10.man10bank.util.Utility
import red.man10.man10bank.util.Utility.msg

object PayCommand : CommandExecutor {
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {

        if (sender !is Player) return true

        if (args.isNullOrEmpty()){
            msg(sender,"§b§l/pay <相手> <金額> : §n電子マネー§b§lを相手に送る")
            msg(sender,"§b§l/mpay <相手> <金額> : §n銀行のお金§b§lを相手に送る")
            return true
        }

        val p = Bukkit.getPlayer(args[1])
        val amount = Utility.fixedPerse(args[2])

        if (p == null){

            return true
        }

        if (amount == null){

            return true
        }

        if (label == "pay"){


        }

        if (label == "mpay"){

        }
        return true
    }
}