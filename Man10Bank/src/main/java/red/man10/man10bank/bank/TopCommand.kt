package red.man10.man10bank.bank

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import red.man10.man10bank.history.EstateHistory

object TopCommand : CommandExecutor{
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {

        if (label == "mbaltop"){
            val page = if (args.isNullOrEmpty()) 0 else args[0].toIntOrNull()?:0
            EstateHistory.asyncShowBalanceTop(sender,page)
        }

        if (label == "estateinfo"){
            EstateHistory.asyncShowServerEstate(sender)
        }

        return true
    }
}