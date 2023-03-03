package red.man10.man10bank

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import red.man10.man10bank.api.APIBase

object Command : CommandExecutor{
    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (label!="banksystem")return true

        if (args.isEmpty()){

            return true
        }

        when(args[0]){

            "reload"->{

                Thread{
                    APIBase.setup()

                }.start()
            }



        }

        return true
    }
}