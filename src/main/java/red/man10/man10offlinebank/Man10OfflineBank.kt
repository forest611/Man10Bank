package red.man10.man10offlinebank

import org.apache.commons.lang.math.NumberUtils
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.plugin.java.JavaPlugin
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class Man10OfflineBank : JavaPlugin(),Listener {

    companion object{
        private const val prefix = "§l[§e§lMan10Bank§f§l]"

        lateinit var vault : VaultManager

        lateinit var plugin : Man10OfflineBank

        fun sendMsg(p:Player,msg:String){
            p.sendMessage(prefix+msg)
        }

        fun sendMsg(p: CommandSender, msg: String) {
            p.sendMessage(prefix+msg)
        }

        fun format(double: Double):String{
            return String.format("%,.1f",double)
        }

        const val OP = "man10bank.op"
        const val USER = "man10bank.user"

        var fee = 0.00

        var rate = 1.0

    }

    private val checking = HashMap<Player,Command>()


    lateinit var es : ExecutorService

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {

        if (label == "mbaltop"){

            if (sender !is Player)return false

            sendMsg(sender,"§e§l現在取得中....")

            es.execute{

                val list = Bank.balanceTop()?:return@execute

                for (data in list){
                    sendMsg(sender,"§b§l${data.first.name} : §e§l$ ${format(data.second)}")
                }

                sendMsg(sender,"§e§l合計口座残高")

                sendMsg(sender,"§b§kXX§e§l${format(Bank.totalBalance())}§b§kXX")

                sendMsg(sender,"§e§l平均口座残高")

                sendMsg(sender,"§b§kXX§e§l${format(Bank.average())}§b§kXX")

            }

        }

        if (label == "mbal"){

            if (args.isEmpty()){

                if (sender !is Player)return false

                sendMsg(sender,"現在取得中....")

                es.execute{
                    sendMsg(sender,"§e§l==========現在の銀行口座残高==========")
                    sendMsg(sender,"§b§kXX§e§l${format(Bank.getBalance(sender.uniqueId))}§b§kXX")

                }

                return true
            }

            val cmd = args[0]

            if (cmd == "user"&& args.size == 2){
                if (sender !is Player)return false
                if (!sender.hasPermission(OP))return true

                sendMsg(sender,"現在取得中....")

                es.execute{
                    val uuid = Bank.getUUID(args[1])

                    if (uuid == null){
                        sendMsg(sender,"まだ口座が解説されていない可能性があります")
                        return@execute
                    }

                    sendMsg(sender,"§e§l==========現在の銀行口座残高==========")
                    sendMsg(sender,"§b§kXX§e§l${format(Bank.getBalance(uuid))}§b§kXX")

                }

            }

            if (!sender.hasPermission(USER))return true

            if (cmd == "help"){

                if (sender !is Player)return false
                sendMsg(sender,"§eMan10Bank")
                sendMsg(sender,"§e/mbal : 口座残高を確認する")
                sendMsg(sender,"§e/mbal deposit(d) <金額>: 所持金のいくらかを、口座に入金する")
                sendMsg(sender,"§e/mbal withdraw(w) <金額>: 口座のお金を、出金する")
            }

            if (cmd == "mail"){
                if (!sender.hasPermission(OP))return false

                Thread{
                    Bank.sendProfitAndLossMail()
                }.start()
                sender.sendMessage("送信完了")
                return true
            }

            //deposit withdraw
            if ((cmd == "deposit" || cmd == "d")&& args.size == 2){

                if (sender !is Player)return false

                if (!NumberUtils.isNumber(args[1])){
                    sendMsg(sender,"§c§l入金する額を半角数字で入力してください！")
                    return true
                }

                val amount = args[1].toDouble()

                if (amount < 1){
                    sendMsg(sender,"§c§l1未満の値は入金出来ません！")
                    return true
                }

                if (vault.getBalance(sender.uniqueId)<amount){
                    sendMsg(sender,"§c§l入金失敗！、所持金が足りません！")
                    return true

                }

                !vault.withdraw(sender.uniqueId,amount)

                es.execute {
                    Bank.deposit(sender.uniqueId,amount,this,"PlayerDepositOnCommand")

                    sendMsg(sender,"§a§l入金成功！")
                }


                return true
            }

            if ((cmd == "withdraw" || cmd == "w") && args.size == 2){
                if (sender !is Player)return false

                if (!NumberUtils.isNumber(args[1])){
                    sendMsg(sender,"§c§l出金する額を半角数字で入力してください！")
                    return true
                }

                var amount = args[1].toDouble()/rate

                if (amount < 1){
                    sendMsg(sender,"§c§l1未満の値は出金出来ません！")
                    return true
                }

                es.execute {
                    if (!Bank.withdraw(sender.uniqueId,amount,this,"PlayerWithdrawOnCommand")){
                        sendMsg(sender,"§c§l出金失敗！口座残高が足りません！")
                        return@execute
                    }

                    val fee1 = (amount*fee)
                    amount*=rate
                    amount -= fee1

                    vault.deposit(sender.uniqueId,amount)

                    sendMsg(sender,"§a§l出金成功！")
                    if (fee1 != 0.0){
                        sendMsg(sender,"§a$${format(fee1)}手数料を徴収しました")
                    }

                }

                return true
            }

            if (cmd == "take"){//mbal take <name> <amount>

                if (!sender.hasPermission(OP))return true

                if (!NumberUtils.isNumber(args[2])){
                    sendMsg(sender,"§c§l回収する額を半角数字で入力してください！")
                    return true
                }

                val amount = args[2].toDouble()

                if (amount < 1){
                    sendMsg(sender,"§c§l1未満の値は入金出来ません！")
                    return true
                }

                es.execute {

                    val uuid = Bank.getUUID(args[1])?: return@execute

                    if (!Bank.withdraw(uuid,amount,this,"TakenByCommand")){
                        Bank.setBalance(uuid,0.0)
                        sendMsg(sender,"§a回収額が残高を上回っていたので、残高が0になりました")
                        return@execute
                    }
                    sendMsg(sender,"§a${format(amount)}円回収しました")
                    sendMsg(sender,"§a現在の残高：${format(Bank.getBalance(uuid))}")

                }
                return true

            }

            if (cmd == "give"){

                if (!sender.hasPermission(OP))return true

                if (!NumberUtils.isNumber(args[2])){
                    sendMsg(sender,"§c§l入金する額を半角数字で入力してください！")
                    return true
                }

                val amount = args[2].toDouble()

                if (amount < 1){
                    sendMsg(sender,"§c§l1未満の値は入金出来ません！")
                    return true
                }

                es.execute {

                    val uuid =  Bank.getUUID(args[1])?: return@execute

                    Bank.deposit(uuid,amount,this,"GivenFromServer")

                    sendMsg(sender,"§a${format(amount)}円入金しました")
                    sendMsg(sender,"§a現在の残高：${format(Bank.getBalance(uuid))}")

                }
            }

            if (cmd == "set"){

                if (!sender.hasPermission(OP))return true

                if (!NumberUtils.isNumber(args[2])){
                    sendMsg(sender,"§c§l設定する額を半角数字で入力してください！")
                    return true
                }

                val amount = args[2].toDouble()

                if (amount < 1){
                    sendMsg(sender,"§c§l1未満の値は入金出来ません！")
                    return true
                }

                es.execute {

                    val uuid =  Bank.getUUID(args[1])?: return@execute

                    Bank.setBalance(uuid,amount)

                    sendMsg(sender,"§a${format(amount)}円に設定しました")

                }
            }

            if (cmd == "reload"){
                if (!sender.hasPermission(OP))return false

                es.execute{
                    reloadConfig()

                    fee = config.getDouble("fee")
                }

            }


        }


        if (sender !is Player)return false

        if (label == "mpay"){

            if (!sender.hasPermission(USER))return true

            if (args.size != 2)return false

            if (!NumberUtils.isNumber(args[1])){
                sendMsg(sender,"§c§l/mpay <ユーザー名> <金額>")
                return true
            }

            val amount = args[1].toDouble()

            if (amount <0.1){
                sendMsg(sender,"§c§l0.1未満の額は送金できません！")
                return true
            }

            if (checking[sender] == null||checking[sender]!! != command){

                sendMsg(sender,"§7§l送金金額:${format(amount)}")
                sendMsg(sender,"§7§l送金相手:${args[0]}")
                sendMsg(sender,"§7§l確認のため、もう一度入力してください")

                checking[sender] = command

                return true
            }

            checking.remove(sender)

            sendMsg(sender,"§e§l現在入金中・・・")


            es.execute {
                val uuid = Bank.getUUID(args[0])

                if (uuid == null){
                    sendMsg(sender,"§c§l存在しないユーザ、もしくは口座がありません！")
                    return@execute
                }

                if (vault.getBalance(sender.uniqueId)<amount){
                    sendMsg(sender,"§c§l送金する残高が足りません！")
                    return@execute

                }

                !vault.withdraw(sender.uniqueId,amount)

                Bank.deposit(uuid,amount,this,"RemittanceFrom${sender.name}")

                sendMsg(sender,"§a§l送金成功！")
            }
        }


        return false
    }


    override fun onEnable() {
        // Plugin startup logic

        plugin = this

        saveDefaultConfig()

        es = Executors.newCachedThreadPool()

        Bank.mysqlQueue()

        vault = VaultManager(this)

        fee = config.getDouble("fee",1.0)
        rate = config.getDouble("rate",1.0)

        server.pluginManager.registerEvents(this,this)

        Bank.mailThread()

    }

    override fun onDisable() {
        // Plugin shutdown logic
        es.shutdown()
    }

    @EventHandler
    fun login(e:PlayerJoinEvent){
        e.player.performCommand("mbal")
    }
}