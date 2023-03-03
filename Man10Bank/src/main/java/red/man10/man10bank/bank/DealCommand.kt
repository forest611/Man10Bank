package red.man10.man10bank.bank

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender

object DealCommand : CommandExecutor{

    val labels = arrayOf("d","deposit","w","withdraw")
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {

        if (label == "d" || label == "deposit"){

        }

        if (label == "w" || label == "withdraw"){

        }

        return true
    }
}