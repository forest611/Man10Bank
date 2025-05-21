package red.man10.man10bank.command

import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.event.ClickEvent
import org.apache.commons.lang.math.NumberUtils
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import red.man10.man10bank.*
import red.man10.man10bank.Man10Bank.Companion.*
import red.man10.man10bank.atm.ATMData
import red.man10.man10bank.atm.ATMInventory
import red.man10.man10bank.history.EstateData
import red.man10.man10bank.loan.*
import red.man10.man10score.ScoreDatabase
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.UUID
import kotlin.math.abs
import kotlin.math.floor

class BankCommand(private val plugin: Man10Bank) : CommandExecutor {

    private val checking = HashMap<Player, Command>()

        override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
    
    
            when(label){
    

    
                "atm" ->{
                    if (sender !is Player)return false
    
                    if (args.isEmpty()){
                        if (!bankEnable)return false
                        ATMInventory.openMainMenu(sender)
                        return true
                    }
    
                    when(args[0]){
    
                        "setmoney"->{
    
                            if (!sender.hasPermission(OP))return false
    
                            val amount = args[1].toDoubleOrNull() ?: return true
    
                            val ret = ATMData.setItem(sender.inventory.itemInMainHand,amount)
    
                            if (ret){
                                sendMsg(sender,"設定完了:${amount}")
                            }
                        }
                    }
    
                }
    
                "mbaltop" ->{
    
                    val page = if (args.isEmpty()) 1 else args[0].toIntOrNull()?:1
    
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                        val balTopMap = EstateData.getBalanceTop(page)
                        val total = EstateData.getBalanceTotal()
    
                        var i = (page*10)-9
    
                        if (sender is Player){
    
                            sendMsg(sender,"§6§k§lXX§e§l富豪トップ${page*10}§6§k§lXX")
    
                            for (data in balTopMap){
                                sendMsg(sender,"§7§l${i}.§b§l${data.first} : §e§l${format(data.second)}円")
                                i++
                            }
    
                            sendMsg(sender,"§e§l電子マネーの合計:${format(total.vault)}円")
                            sendMsg(sender,"§e§l現金の合計:${format(total.cash)}円")
                            sendMsg(sender,"§e§l銀行口座の合計:${format(total.bank)}円")
                            sendMsg(sender,"§e§lショップ残高の合計:${format(total.shop)}円")
                            sendMsg(sender,"§e§lその他資産の合計:${format(total.estate)}円")
                            sendMsg(sender,"§c§l公的ローンの合計:${format(total.loan)}円")
                            sendMsg(sender,"§e§l全ての合計:${format(total.total())}円")
    
                            return@Runnable
                        }
    
                        sender.sendMessage("§6§k§lXX§e§l富豪トップ${page*10}§6§k§lXX")
    
                        for (data in balTopMap){
                            sender.sendMessage("§7§l${i}.§b§l${data.first} : §e§l${format(data.second)}円")
                            i++
                        }
                        sender.sendMessage("§e§l電子マネーの合計:${format(total.vault)}円")
                        sender.sendMessage("§e§l現金の合計:${format(total.estate)}円")
                        sender.sendMessage("§e§l銀行口座の合計:${format(total.bank)}円")
                        sender.sendMessage("§e§lショップ残高の合計:${format(total.shop)}円")
                        sender.sendMessage("§e§lその他資産の合計:${format(total.estate)}円")
                        sender.sendMessage("§c§l公的ローンの合計:${format(total.loan)}円")
                        sender.sendMessage("§e§l全ての合計:${format(total.total())}円")
    
                    })
    
                }
    
                "mloantop" ->{
    
                    val page = if (args.isEmpty()) 1 else args[0].toIntOrNull()?:1
    
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                        val loanTop = ServerLoan.getLoanTop(page)
                        val total = EstateData.getBalanceTotal()
    
                        var i = (page*10)-9
    
                        if (sender is Player){
    
                            sendMsg(sender,"§4§k§lXX§c§l借金トップ${page*10}§4§k§lXX")
    
                            for (data in loanTop){
                                sendMsg(sender,"§c§l${i}.§l${data.first} : §4§l${format(data.second)}円")
                                i++
                            }
    
                            sendMsg(sender,"§c§l公的ローンの合計:${format(total.loan)}円")
    
                            return@Runnable
                        }
    
                        sender.sendMessage("§4§k§lXX§c§l借金トップ${page*10}§4§k§lXX")
    
                        for (data in loanTop){
                            sender.sendMessage("§c§l${i}.§l${data.first} : §4§l${format(data.second)}円")
                            i++
                        }
    
                        sender.sendMessage("§c§l公的ローンの合計:${format(total.loan)}円")
    
                    })
    
                }
    
                "bal","balance","money","bank","man10bank:bal","man10bank:balance","man10bank:money","man10bank:bank" ->{
    
                    if (sender !is Player)return false
    
                    if (args.isEmpty()){
                        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable { showBalance(sender,sender) })
                        return true
                    }
    
                    when(args[0]){
    
                        "help" ->{
                            showCommand(sender)
                        }
    
                        "bs" ->{
                            Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                                showBalanceSheet(sender,sender)
                            })
                        }
    
                        "log" ->{
    
                            val page = if (args.size>=2) args[1].toIntOrNull()?:0 else 0
    
                            Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                                val list = Bank.getBankLog(sender,page)
    
                                sendMsg(sender,"§d§l===========銀行の履歴==========")
                                for (data in list){
    
                                    val tag = if (data.isDeposit) "§a[入金]" else "§c[出金]"
                                    sendMsg(sender,"$tag §e${data.dateFormat} §e§l${format(data.amount)} §e${data.note}")
    
                                }
    
                                val previous = if (page!=0) {
                                    text("${prefix}§b§l<<==前のページ ").clickEvent(ClickEvent.runCommand("/bal log ${page-1}"))
                                }else text(prefix)
    
                                val next = if (list.size == 10){
                                    text("§b§l次のページ==>>").clickEvent(ClickEvent.runCommand("/bal log ${page+1}"))
                                }else text("")
    
                                sender.sendMessage(previous.append(next))
    
                            })
    
                            return true
                        }
    
                        "logop" ->{
    
                            if (!sender.hasPermission(OP))return false
    
                            val p = if (args.size >= 2)Bukkit.getPlayer(args[1]) else sender
                            val page = if (args.size>=2) args[2].toIntOrNull()?:0 else 0
    
                            if (p == null){
                                sender.sendMessage("プレイヤーがオフラインです")
                                return true
                            }
    
                            Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                                val list = Bank.getBankLog(p,page)
    
                                sendMsg(sender,"§d§l===========銀行の履歴==========")
                                for (data in list){
    
                                    val tag = if (data.isDeposit) "§a[入金]" else "§c[出金]"
                                    sendMsg(sender,"$tag §e${data.dateFormat} ${data.note} ${format(data.amount)}")
    
                                }
    
                                val previous = if (page!=0) {
                                    text("${prefix}§b§l<<==前のページ ").clickEvent(ClickEvent.runCommand("/bal logop ${page-1}"))
                                }else text(prefix)
    
                                val next = if (list.size == 10){
                                    text("§b§l次のページ==>>").clickEvent(ClickEvent.runCommand("/bal logop ${page+1}"))
                                }else text("")
    
                                sender.sendMessage(previous.append(next))
    
                            })
    
                        }
    
                        "init" ->{
                            if (!sender.hasPermission(OP))return false
    
                            if (args.size!=2)return false
    
                            if (checking[sender]== null || checking[sender]!=command){
                                sendMsg(sender,"データは復元できません。確認のため、もう一度入力してください")
                                checking[sender] = command
                                return true
                            }
    
                            Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                                Bank.init(args[1])
                                sendMsg(sender,"${args[1]}のデータを初期化しました")
    
                            })
    
                            checking.remove(sender)
    
                        }
    
                        "take" ->{
                            if (!sender.hasPermission(OP))return true
    
                            val a = args[2].replace(",","")
    
                            if (!NumberUtils.isDigits(a)){
                                sendMsg(sender,"§c§l回収する額を半角数字で入力してください！")
                                return true
                            }
    
                            val amount = a.toDouble()
    
                            if (amount < 0){
                                sendMsg(sender,"§c§l0未満の値は入金出来ません！")
                                return true
                            }
    
                            Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                                val uuid = Bank.getUUID(args[1])?: return@Runnable
    
                                if (Bank.withdraw(uuid,amount,plugin,"TakenByCommand","サーバーから徴収").first!=0){
                                    Bank.setBalance(uuid,0.0)
                                    sendMsg(sender,"§a回収額が残高を上回っていたので、残高が0になりました")
                                    return@Runnable
                                }
                                sendMsg(sender,"§a${format(amount)}円回収しました")
                                sendMsg(sender,"§a現在の残高：${format(Bank.getBalance(uuid))}")
    
                            })
    
                            return true
    
                        }
    
                        "give"->{
                            if (!sender.hasPermission(OP))return true
    
                            val a = args[2].replace(",","")
    
                            if (!NumberUtils.isDigits(a)){
                                sendMsg(sender,"§c§l入金する額を半角数字で入力してください！")
                                return true
                            }
    
                            val amount = a.toDouble()
    
                            if (amount < 0){
                                sendMsg(sender,"§c§l0未満の値は入金出来ません！")
                                return true
                            }
    
                            Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                                val uuid =  Bank.getUUID(args[1])?: return@Runnable
    
                                Bank.deposit(uuid,amount,plugin,"GivenFromServer","サーバーから発行")
    
                                sendMsg(sender,"§a${format(amount)}円入金しました")
                                sendMsg(sender,"§a現在の残高：${format(Bank.getBalance(uuid))}")
    
                            })
    
                        }
    
                        "set"->{
                            if (!sender.hasPermission(OP))return true
    
                            val a = args[2].replace(",","")
    
                            if (!NumberUtils.isDigits(a)){
                                sendMsg(sender,"§c§l設定する額を半角数字で入力してください！")
                                return true
                            }
    
                            val amount = a.toDouble()
    
                            if (amount < 0){
                                sendMsg(sender,"§c§l0未満の値は入金出来ません！")
                                return true
                            }
    
                            Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                                val uuid =  Bank.getUUID(args[1])?: return@Runnable
    
                                Bank.setBalance(uuid,amount)
    
                                sendMsg(sender,"§a${format(amount)}円に設定しました")
    
                            })
                        }
    
                        "on" ->{
                            if (!sender.hasPermission(OP))return false
    
                            bankEnable = true
    
                            Bukkit.broadcast(text("§e§lMan10Bankが開きました！"))
                            return true
    
                        }
    
                        "off" ->{
                            if (!sender.hasPermission(OP))return false
    
                            bankEnable = false
                            Bukkit.broadcast(text("§e§lMan10Bankが閉じました！"))
                            return true
    
                        }
    
                        "reload" ->{
                            if (!sender.hasPermission(OP))return false
    
                            Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                                reloadConfig()
                                loadConfig()
                                Bank.reload()
                                sendMsg(sender,"リロード完了")
    
                                sendMsg(sender,"laonFee:${loanFee}")
                                sendMsg(sender,"loanMax:${loanMax}")
                                sendMsg(sender,"loanRate:${loanRate}")
                                sendMsg(sender,"loggingServerHistory:${loggingServerHistory}")
                                sendMsg(sender,"kickDunce:${kickDunce}")
                                sendMsg(sender,"workWorld:${workWorld?.world?.name?:"NULL"}")
                                sendMsg(sender,"lendParameter:${ServerLoan.lendParameter}")
                                sendMsg(sender,"borrowStandardScore:${ServerLoan.borrowStandardScore}")
                                sendMsg(sender,"minServerLoanAmount:${ServerLoan.minServerLoanAmount}")
                                sendMsg(sender,"maxServerLoanAmount:${ServerLoan.maxServerLoanAmount}")
                                sendMsg(sender,"revoFee:${ServerLoan.revolvingFee}")
    
                            })
    
                        }
    
                        "work" ->{
                            if (!sender.hasPermission(OP))return false
    
                            workWorld = sender.location
                            config.set("workWorld", workWorld)
                            saveConfig()
                        }
    
    
                        else ->{
                            val p = Bukkit.getOfflinePlayer(args[0]).player
    
                            if (!sender.hasPermission(OP))return true
    
                            Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                                if (p==null){
                                    EstateData.showOfflineUserEstate(sender,args[0])
                                    return@Runnable
                                }
    
                                showBalance(sender,p)
                            })
    
                            return true
    
                        }
                    }
    
                }
    
                "deposit" ->{

                    if (sender !is Player)return false

                    return handleDeposit(sender, args)

                }
    
                "withdraw" ->{

                    if (sender !is Player)return false

                    return handleWithdraw(sender, args)
                }
    
                "pay","man10bank:pay" ->{
                    if (sender !is Player)return true

                    return handlePay(sender, command, args)

                }
    
                "mpay" ->{
                    if (sender !is Player)return true

                    return handleMpay(sender, command, args)

                }
    
                "ballog" -> {
    
                    if (sender !is Player)return false
    
                    val page = if (args.isNotEmpty()) args[0].toIntOrNull()?:0 else 0
    
                    Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
                        val list = Bank.getBankLog(sender,page)
    
                        sendMsg(sender,"§d§l===========銀行の履歴==========")
                        for (data in list){
    
                            val tag = if (data.isDeposit) "§a[入金]" else "§c[出金]"
                            sendMsg(sender,"$tag §e${data.dateFormat} §e§l${format(data.amount)} §e${data.note}")
    
                        }
    
                        val previous = if (page!=0) {
                            text("${prefix}§b§l<<==前のページ ").clickEvent(ClickEvent.runCommand("/ballog ${page-1}"))
                        }else text(prefix)
    
                        val next = if (list.size == 10){
                            text("§b§l次のページ==>>").clickEvent(ClickEvent.runCommand("/ballog ${page+1}"))
                        }else text("")
    
                        sender.sendMessage(previous.append(next))
    
                    })
    
                    return true
    
    
                }
            }
    
            return true
        }
    
    private fun ZenkakuToHankaku(number: String): Double {

        val normalize = Normalizer.normalize(number, Normalizer.Form.NFKC)

        return normalize.toDoubleOrNull() ?: -1.0
    }

    private fun parseAmount(value: String): Double? {
        val num = ZenkakuToHankaku(value.replace(",", ""))
        return if (num == -1.0) null else num
    }

    private fun handleDeposit(sender: Player, args: Array<out String>): Boolean {
        if (!bankEnable) return false

        if (args.isEmpty()) {
            sendMsg(sender, "§c§l/deposit <金額> : 銀行に電子マネーを入れる")
            return true
        }

        val amount = if (args[0] == "all") {
            vault.getBalance(sender.uniqueId)
        } else {
            val a = parseAmount(args[0]) ?: run {
                sendMsg(sender, "§c§l数字で入力してください！")
                return true
            }
            a
        }

        if (amount < 1) {
            sendMsg(sender, "§c§l1円以上を入力してください！")
            return true
        }

        if (!vault.withdraw(sender.uniqueId, amount)) {
            sendMsg(sender, "§c§l電子マネーが足りません！")
            return true
        }

        Bank.asyncDeposit(sender.uniqueId, amount, plugin, "PlayerDepositOnCommand", "/depositによる入金") { code, _, _ ->
            if (code != 0) {
                sendMsg(sender, "入金エラーが発生しました")
                vault.deposit(sender.uniqueId, amount)
                return@asyncDeposit
            }
            sendMsg(sender, "§a§l入金できました！")
        }

        return true
    }

    private fun handleWithdraw(sender: Player, args: Array<out String>): Boolean {
        if (!bankEnable) return false

        if (args.isEmpty()) {
            sendMsg(sender, "§c§l/withdraw <金額> : 銀行から電子マネーを出す")
            return true
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            val amount = if (args[0] == "all") {
                Bank.getBalance(sender.uniqueId)
            } else {
                val a = parseAmount(args[0]) ?: run {
                    sendMsg(sender, "§c§l数字で入力してください！")
                    return@Runnable
                }
                a
            }

            if (amount < 1) {
                sendMsg(sender, "§c§l1円以上を入力してください！")
                return@Runnable
            }

            Bank.asyncWithdraw(sender.uniqueId, amount, plugin, "PlayerWithdrawOnCommand", "/withdrawによる出金") { code, _, _ ->
                if (code == 2) {
                    sendMsg(sender, "§c§l銀行のお金が足りません！")
                    return@asyncWithdraw
                }
                vault.deposit(sender.uniqueId, amount)
                sendMsg(sender, "§a§l出金できました！")
            }
        })

        return true
    }

    private fun handlePay(sender: Player, command: Command, args: Array<out String>): Boolean {
        if (!isEnabled) return true

        if (args.size != 2) {
            sendMsg(sender, "§c§l/pay <送る相手> <金額> : 電子マネーを友達に振り込む")
            return true
        }

        val amount = parseAmount(args[1]) ?: run {
            sendMsg(sender, "§c§l/pay <送る相手> <金額> : 電子マネーを友達に振り込む")
            return true
        }

        if (amount < 1) {
            sendMsg(sender, "§c§l1円以上を入力してください！")
            return true
        }

        if (checking[sender] == null || checking[sender] != command) {
            sendMsg(sender, "§7§l送る電子マネー:${format(amount)}円")
            sendMsg(sender, "§7§l送る相手:${args[0]}")
            sendMsg(sender, "§7§l確認のため、同じコマンドをもう一度入力してください")
            checking[sender] = command
            return true
        }

        checking.remove(sender)

        val p = Bukkit.getPlayer(args[0])
        if (p == null) {
            sendMsg(sender, "§c§l送る相手がオフラインかもしれません")
            return true
        }

        if (!vault.withdraw(sender.uniqueId, amount)) {
            sendMsg(sender, "§c§l送る電子マネーが足りません！")
            return true
        }

        vault.deposit(p.uniqueId, amount)
        sendMsg(sender, "§a§l送金できました！")
        sendMsg(p, "§a${sender.name}さんから${format(amount)}円送られました！")

        return true
    }

    private fun handleMpay(sender: Player, command: Command, args: Array<out String>): Boolean {
        if (!isEnabled) return true

        if (args.size != 2) {
            sendMsg(sender, "§c§l/mpay <送る相手> <金額> : 銀行のお金を友達に振り込む")
            return false
        }

        val amount = parseAmount(args[1]) ?: run {
            sendMsg(sender, "§c§l/mpay <送る相手> <金額> : 銀行のお金を友達に振り込む")
            return true
        }

        if (amount < 1) {
            sendMsg(sender, "§c§l1円以上を入力してください！")
            return true
        }

        if (checking[sender] == null || checking[sender] != command) {
            sendMsg(sender, "§7§l送る銀行のお金:${format(amount)}円")
            sendMsg(sender, "§7§l送る相手:${args[0]}")
            sendMsg(sender, "§7§l確認のため、同じコマンドをもう一度入力してください")
            checking[sender] = command
            return true
        }

        checking.remove(sender)

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            val uuid = Bank.getUUID(args[0]) ?: run {
                sendMsg(sender, "§c§l送金失敗！まだman10サーバーにきたことがない人かもしれません！")
                return@Runnable
            }

            if (Bank.withdraw(sender.uniqueId, amount, plugin, "RemittanceTo${args[0]}", "${args[0]}へ送金").first != 0) {
                sendMsg(sender, "§c§l送金する銀行のお金が足りません！")
                return@Runnable
            }

            Bank.deposit(uuid, amount, plugin, "RemittanceFrom${sender.name}", "${sender.name}からの送金")
            sendMsg(sender, "§a§l送金成功！")

            val p = Bukkit.getPlayer(uuid) ?: return@Runnable
            sendMsg(p, "§a${sender.name}さんから${format(amount)}円送られました！")
        })

        return true
    }
    
        fun showBalance(sender:Player,p:Player){
    
            //時差による表示ずれ対策で、一旦所持金を呼び出す
    
            val loan = ServerLoan.getBorrowingAmount(p)
            val payment = ServerLoan.getPaymentAmount(p)
            val nextDate = ServerLoan.getNextPayTime(p)
            val score = ScoreDatabase.getScore(p.uniqueId)
    
            val bankAmount = Bank.getBalance(p.uniqueId)
    
            val cash: Double = EstateData.getCash(p)
            val estate: Double = EstateData.getEstate(p)
    
            sendMsg(sender,"§e§l==========${p.name}のお金==========")
            sendMsg(sender," §b§l電子マネー:  §e§l${format(vault.getBalance(p.uniqueId))}円")
            sendMsg(sender," §b§l銀行:  §e§l${format(bankAmount)}円")
            if (cash>0.0){ sendMsg(sender," §b§l現金:  §e§l${format(cash)}円") }
            if (estate>0.0){ sendMsg(sender," §b§lその他の資産:  §e§l${format(estate)}円") }
            if(EstateData.getShopTotalBalance(p) > 0.0) sendMsg(sender, " §b§lショップ口座:  §e§l${format(EstateData.getShopTotalBalance(p))}円")
    
            sendMsg(sender," §b§lスコア: §a§l${format(score.toDouble())}")
    
            if (loan!=0.0 && nextDate!=null){
                sendMsg(sender," §b§lまんじゅうリボ:  §c§l${format(loan)}円")
                sendMsg(sender," §b§l支払額:  §c§l${format(payment)}円")
                sendMsg(sender," §b§l次の支払日: §c§l${SimpleDateFormat("yyyy-MM-dd").format(nextDate.first)}")
                if (nextDate.second>0){
                    sendMsg(sender," §c§lMan10リボの支払いに失敗しました(失敗回数:${nextDate.second})。支払いに失敗するとスコアの減少やJailがあります")
                }
            }
    
            sender.sendMessage(text("$prefix §a§l§n[ここをクリックでコマンドをみる]").clickEvent(ClickEvent.runCommand("/bank help")))
    
        }
    
        private fun showBalanceSheet(sender:Player,p:Player){
    
            val serverLoan = ServerLoan.getBorrowingAmount(p)
            val userLoan = LoanData.getTotalLoan(p)
            val bankAmount = Bank.getBalance(p.uniqueId)
            val cash: Double = EstateData.getCash(p)
            val estate: Double = EstateData.getEstate(p)
            val bal = vault.getBalance(p.uniqueId)
    
            val assets = bankAmount+cash+estate+bal
            val liability = serverLoan + userLoan
            val equity = assets-liability
            val symbol = if (equity<0) { "△" } else {""}
    
            sender.sendMessage("§e§l===============${p.name}のバランスシート=================")
            sender.sendMessage(String.format(" §b現金:        %14s §f| §c個人間借金:            §c%14s", format(cash), format(userLoan)))
            sender.sendMessage(String.format(" §b電子マネー:   %14s §f| §cMan10リボ:            §c%14s", format(bal), format(serverLoan)))
            sender.sendMessage(String.format(" §b銀行:        %14s §f| §c合計負債:              §c%14s", format(bankAmount), format(liability)))
            sender.sendMessage(String.format(" §bその他:      %14s §f| §a純資産:                §a%14s", format(estate), "${format(abs(equity))}${symbol}"))
            sender.sendMessage(String.format(" §b合計資産:     %14s §f| §c負債と純資産:          §b%14s", format(assets), format(liability+equity)))
    
        }
    
    
        private fun showCommand(sender:Player){
            val pay = text("$prefix §e[電子マネーを友達に送る]  §n/pay").clickEvent(ClickEvent.suggestCommand("/pay "))
            val atm = text("$prefix §a[電子マネーのチャージ・現金化]  §n/atm").clickEvent(ClickEvent.runCommand("/atm"))
            val deposit = text("$prefix §b[電子マネーを銀行に入れる]  §n/deposit").clickEvent(ClickEvent.suggestCommand("/deposit "))
            val withdraw = text("$prefix §c[電子マネーを銀行から出す]  §n/withdraw").clickEvent(ClickEvent.suggestCommand("/withdraw "))
            val revolving = text("$prefix §e[Man10リボを使う]  §n/mrevo borrow").clickEvent(ClickEvent.suggestCommand("/mrevo borrow "))
            val ranking = text("$prefix §6[お金持ちランキング]  §n/mbaltop").clickEvent(ClickEvent.runCommand("/mbaltop"))
            val log = text("$prefix §7[銀行の履歴]  §n/ballog").clickEvent(ClickEvent.runCommand("/ballog"))
    
            sender.sendMessage(pay)
            sender.sendMessage(atm)
            sender.sendMessage(deposit)
            sender.sendMessage(withdraw)
            sender.sendMessage(revolving)
            sender.sendMessage(ranking)
            sender.sendMessage(log)
    
        }

}
