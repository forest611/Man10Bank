package red.man10.man10bank.loan

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import red.man10.man10bank.Man10Bank

class ServerLoanCommand : CommandExecutor{
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {

        if (sender !is Player)return false

        if (label != "slend")return false

        if (args.isEmpty()) {


            return false

        }

        when(args[0]){

            "check" ->{
                Man10Bank.es.execute {
                    ServerLoan.checkServerLoan(sender)
                }
            }


        }



        return false
    }
}