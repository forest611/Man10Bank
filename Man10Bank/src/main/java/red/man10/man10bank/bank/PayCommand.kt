package red.man10.man10bank.bank

import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.event.ClickEvent
import net.kyori.adventure.text.event.HoverEvent
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import red.man10.man10bank.Man10Bank
import red.man10.man10bank.Man10Bank.Companion.threadPool
import red.man10.man10bank.Man10Bank.Companion.vault
import red.man10.man10bank.api.APIBank
import red.man10.man10bank.util.Utility
import red.man10.man10bank.util.Utility.msg
import red.man10.man10bank.util.Utility.prefix
import java.util.UUID

object PayCommand : CommandExecutor {

    val confirmList = mutableListOf<UUID>()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>?): Boolean {

        if (sender !is Player) return true

        if (args.isNullOrEmpty() || args.size < 2){
            msg(sender,"§b§l/pay <相手> <金額> : §n電子マネー§b§lを相手に送る")
            msg(sender,"§b§l/mpay <相手> <金額> : §n銀行のお金§b§lを相手に送る")
            return true
        }

        val cmd = args[0]

        //初期入力
        if (args.size == 2){

            val amount = Utility.fixedPerse(args[1])

            if (amount == null){
                msg(sender,"§c§l金額は数字で入力してください！")
                return true
            }

            val random = UUID.randomUUID()
            confirmList.add(random)

            msg(sender,"§6§l送金相手:${cmd} 金額:${Utility.format(amount)}円")
            sender.sendMessage(text(prefix)
                .append(text(" §a§l§n[確定] ")
                    .hoverEvent(HoverEvent.showText(text("§c§l確定を押すと取消はできません！")))
                    .clickEvent(ClickEvent.runCommand("/$label confirm $cmd $amount $random")))
                .append(text(" §c§l§n[取消] ")
                    .clickEvent(ClickEvent.runCommand("/$label cancel $random a"))))
            return true
        }

        if (label == "pay"){

//            if (args.size != 3)return true

            //取り消しを押した場合    /pay cancel random
            if (cmd == "cancel"){
                msg(sender,"§a§l送金を取り消しました")
                confirmList.remove(UUID.fromString(args[1]))
                return true
            }

            if (cmd != "confirm")return false

            val key = UUID.fromString(args[3])

            if (!confirmList.contains(key)){
                msg(sender,"§c§lこのpayは無効です")
                return true
            }

            //確定した場合    /pay confirm player amount random
            confirmList.remove(key)
            val mcid = args[1]
            val p = Bukkit.getPlayer(mcid)
            val amount = Utility.fixedPerse(args[2])?:return true

            if (!vault.withdraw(sender.uniqueId,amount)){
                msg(sender,"§c§l電子マネーの残高が足りません！")
                return true
            }

            //相手がオフラインの場合
            if (p == null){
                msg(sender,"§a送金先のプレイヤーがオフラインのため、相手の銀行口座に入金します")

                threadPool.execute {
                    val uuid = APIBank.getUUID(mcid)

                    if (uuid == null){
                        msg(sender,"§c§lMinecraftのIDに誤りがあります")
                        vault.deposit(sender.uniqueId,amount)
                        return@execute
                    }

                    val result = APIBank.addBalance(
                        APIBank.TransactionData(
                        uuid.toString(),
                        amount,
                        Man10Bank.instance.name,
                        "RemittanceTo${mcid}",
                        "${mcid}へ送金"
                    ))

                    if (result == APIBank.BankResult.SUCCESSFUL){
                        msg(sender,"§a§l送金に成功しました！(相手:${mcid} 金額:${Utility.format(amount)}円)")
                        return@execute
                    }else{
                        msg(sender,"§c§l送金に失敗しました。時間をおいて再度行ってください")
                        vault.deposit(sender.uniqueId,amount)
                    }
                }

                return true
            }

            //オンラインの場合
            vault.deposit(p.uniqueId,amount)
            msg(sender,"§a§l送金に成功しました！(相手:${p.name} 金額:${Utility.format(amount)}円)")

            return true
        }

        if (label == "mpay"){
//            if (args.size != 3)return true

            //取り消しを押した場合
            if (cmd == "cancel"){
                msg(sender,"§a§l送金を取り消しました")
                confirmList.remove(UUID.fromString(args[1]))
                return true
            }

            if (cmd != "confirm")return false

            val key = UUID.fromString(args[3])

            if (!confirmList.contains(key)){
                msg(sender,"§c§lこのpayは無効です")
                return true
            }

            //確定した場合    /pay confirm player amount random
            confirmList.remove(key)
            val mcid = args[1]
            val amount = Utility.fixedPerse(args[2])?:return true

            //送金処理
            threadPool.execute {

                val uuid = APIBank.getUUID(mcid)

                if (uuid == null){
                    msg(sender,"§c§lMinecraftのIDに誤りがあります")
                    return@execute
                }

                val takeResult = APIBank.takeBalance(APIBank.TransactionData(
                    sender.uniqueId.toString(),
                    amount,
                    Man10Bank.instance.name,
                    "RemittanceTo${mcid}",
                    "${mcid}へ送金"
                ))

                if (takeResult != APIBank.BankResult.SUCCESSFUL){
                    msg(sender,"§c§l出金に失敗しました")
                    return@execute
                }

                val addResult = APIBank.addBalance(
                    APIBank.TransactionData(
                        uuid.toString(),
                        amount,
                        Man10Bank.instance.name,
                        "RemittanceFrom${mcid}",
                        "${mcid}からの送金"
                    ))

                if (addResult == APIBank.BankResult.SUCCESSFUL){
                    msg(sender,"§a§l送金に成功しました！(相手:${mcid} 金額:${Utility.format(amount)}円)")
                    return@execute
                }else{
                    msg(sender,"§c§l送金に失敗しました。時間をおいて再度行ってください")
                    vault.deposit(sender.uniqueId,amount)
                }
            }

            return true
        }
        return true
    }
}