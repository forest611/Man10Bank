package red.man10.man10bank.command

import net.kyori.adventure.text.Component.text
import net.kyori.adventure.text.event.ClickEvent.runCommand
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.NamespacedKey
import red.man10.man10bank.Bank
import red.man10.man10bank.Man10Bank
import red.man10.man10bank.Man10Bank.Companion.format
import red.man10.man10bank.Man10Bank.Companion.loanFee
import red.man10.man10bank.Man10Bank.Companion.plugin
import red.man10.man10bank.Man10Bank.Companion.prefix
import red.man10.man10bank.Man10Bank.Companion.sendMsg
import red.man10.man10bank.loan.CollateralGUI
import red.man10.man10bank.loan.LoanData
import java.text.SimpleDateFormat
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.floor

class LocalLoanCommand : CommandExecutor {

    private val cacheMap = ConcurrentHashMap<Player, Cache>()
    private val USER = "man10lend.user"
    private val OP = "man10lend.op"

    private val sdf = SimpleDateFormat("yyyy-MM-dd")
    
    // 手形IDごとの処理中フラグを管理（排他制御用）
    private val processingNotes = ConcurrentHashMap<Int, Boolean>()

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (label != "mlend") return false
        if (sender !is Player) return true

        if (!sender.hasPermission(OP) &&
            (!Man10Bank.enableLocalLoan || Man10Bank.localLoanDisableWorlds.contains(sender.world.name))) {
            sendMsg(sender, "§c§lこのエリアでは個人間借金の取引を行うことはできません。")
            return true
        }

        if (args.isEmpty()) {
            showUsage(sender)
            return true
        }

        return when (args[0]) {
            "off" -> { if (sender.hasPermission(OP)) {
                Man10Bank.enableLocalLoan = false
                plugin.config.set("enableLocalLoan",true)
                plugin.saveConfig()
                sendMsg(sender, "§c§l個人間借金の取引を無効化しました")
            } ; true }
            "on" -> { if (sender.hasPermission(OP)) {
                Man10Bank.enableLocalLoan = true
                plugin.config.set("enableLocalLoan",true)
                plugin.saveConfig()
                sendMsg(sender, "§a§l個人間借金の取引を有効化しました")
            }; true }
            "allow" -> { onAllowed(sender); true }
            "deny" -> { onDenied(sender); true }
            "collateral" -> { showCollateral(sender); true }
            "setcollateral" -> { setCollateral(sender); true }
            "confirm" -> { onConfirmed(sender); true }
            "userdata" -> { if (args.size >= 2) userData(sender, args[1]); true }
            "reissue" -> {
                if (args.size >= 2) {
                    args[1].toIntOrNull()?.let { reissue(sender, it) }
                        ?: sendMsg(sender, "数字を入力してください")
                }
                true
            }
            "collect" -> { collectDebt(sender); true }
            "collectcollateral" -> { collectCollateral(sender); true }
            else -> onPropose(sender, args)
        }
    }

    private fun showUsage(p: Player) {
        sendMsg(p, "§a/mlend <プレイヤー> <貸出金額> <返済金額> <期間(日)>")
        sendMsg(p, "§a貸出金額の${loanFee * 100}%を手数料としていただきます")
        sendMsg(p, "§a/mlend collect - 手に持っている手形の借金を回収")
        sendMsg(p, "§a/mlend collectcollateral - 手に持っている手形の担保を回収")
    }

    private fun onPropose(sender: Player, args: Array<out String>): Boolean {
        if (!sender.hasPermission(USER)) {
            sendMsg(sender, "§4お金を貸す権限がありません！")
            return false
        }

        val borrow = Bukkit.getPlayer(args[0]) ?: run {
            sendMsg(sender, "§c相手はオフラインです")
            return false
        }

        if (sender.name == borrow.name && !sender.hasPermission(OP)) {
            sendMsg(sender, "§c自分に借金はできません")
            return false
        }

        if (!borrow.hasPermission(USER)) {
            sendMsg(sender, "§4貸し出す相手に権限がありません")
            return false
        }

        val amount: Double
        val paybackAmount: Double
        val day: Int
        try {
            amount = floor(args[1].toDouble())
            paybackAmount = floor(args[2].toDouble())
            day = args[3].toInt()
            if (day > 365 || day <= 0) {
                sendMsg(sender, "§c返済期限は１日以上、一年以内にしてください！")
                return false
            }
            if (amount > Man10Bank.loanMax || amount < 1) {
                sendMsg(sender, "§c貸出金額は1円以上、${format(Man10Bank.loanMax)}円以下に設定してください！")
                return false
            }
            if (paybackAmount < amount) {
                sendMsg(sender, "§c返済金額は貸出金額以上に設定してください！")
                return false
            }
            if (paybackAmount > amount * 2) {
                sendMsg(sender, "§c返済金額は貸出金額の2倍以下に設定してください！")
                return false
            }
        } catch (e: Exception) {
            sendMsg(sender, "§c入力に問題があります！")
            return false
        }

        sendMsg(sender, "§a§l借金の提案を相手に提示しました")
        sendMsg(borrow, "§e§l＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝")
        sendMsg(borrow, "§e§kXX§b§l借金の提案§e§kXX")
        sendMsg(borrow, "§e貸し出す人:${sender.name}")
        sendMsg(borrow, "§e貸し出される金額:${format(amount)}")
        sendMsg(borrow, "§e返す金額:${format(paybackAmount)}")
        sendMsg(borrow, "§e返す日:${sdf.format(LoanData.calcDate(day))}")

        val setCollateralButton = text("§6§l§n[担保を設定する] ").clickEvent(runCommand("/mlend setcollateral"))
        val allowOrDeny = text("${prefix}§b§l§n[借りる] ").clickEvent(runCommand("/mlend allow"))
            .append(text("§c§l§n[借りない]").clickEvent(runCommand("/mlend deny")))
        borrow.sendMessage(setCollateralButton)
        borrow.sendMessage(allowOrDeny)

        sendMsg(borrow, "§e§l＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝")

        val cache = Cache(
            amount = amount,
            paybackAmount = paybackAmount,
            day = day,
            borrow = borrow,
            lend = sender
        )
        cacheMap[borrow] = cache
        return true
    }

    private fun onAllowed(sender: Player) {
        if (!sender.hasPermission(USER)) return
        val cache = cacheMap[sender] ?: run {
            sendMsg(sender, "§cあなたに借金の提案は来ていません！")
            return
        }
        if (!cache.lend.isOnline) {
            sendMsg(sender, "§c§l提案者がログアウトしました")
            return
        }
        // 借り手が「借りる」をクリックした時点で、貸し手に担保確認を促す
        sendMsg(sender, "§a§l借金の申請を受け付けました。貸し手の承認を待っています...")
        if (!cache.lend.isOnline) {
            sendMsg(sender, "§c§l貸し手がオフラインになりました")
            return
        }

        sendMsg(cache.lend, "§e§l＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝")
        sendMsg(cache.lend, "§b§l${sender.name}が借金を受け入れました！")
        if (cache.collateralItems.isNotEmpty()) {
            sendMsg(cache.lend, "§e担保: ${cache.collateralItems.size}個のアイテム")
            val viewCollateralButton = text("§6§l§n[担保を確認する] ").clickEvent(runCommand("/mlend collateral"))
            cache.lend.sendMessage(viewCollateralButton)
        } else {
            sendMsg(cache.lend, "§c担保: なし")
        }
        val confirmDenyButtons = text("§a§l§n[最終承認] ").clickEvent(runCommand("/mlend confirm"))
            .append(text("§c§l§n[拒否]").clickEvent(runCommand("/mlend deny")))
        cache.lend.sendMessage(confirmDenyButtons)
        sendMsg(cache.lend, "§e§l＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝＝")
    }

    private fun onDenied(sender: Player) {
        val cache = cacheMap[sender] ?: run {
            sendMsg(sender, "§cあなたに借金の提案は来ていません！")
            return
        }
        // 担保が設定されていた場合、借り手に返却
        if (cache.collateralItems.isNotEmpty()) {
            cache.collateralItems.forEach { item ->
                if (cache.borrow.inventory.firstEmpty() == -1) {
                    cache.borrow.world.dropItem(cache.borrow.location, item)
                } else {
                    cache.borrow.inventory.addItem(item)
                }
            }
            sendMsg(cache.borrow, "§e担保アイテムが返却されました。")
        }
        
        sendMsg(sender, "§c借金の提案を断りました！")
        cache.lend.sendMessage("§c相手が借金の提案を拒否しました！")
        cacheMap.remove(sender)
    }

    private fun onConfirmed(sender: Player) {
        // 貸し手として最終承認を行う
        val cache = cacheMap.values.find { it.lend == sender } ?: run {
            sendMsg(sender, "§c承認する借金の提案がありません")
            return
        }
        //　増殖対策で先にキャッシュを削除しておく
        cacheMap.remove(cache.borrow)

        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            sendMsg(sender, "Man10Bankシステムに問い合わせ中・・・§l§kXX")
            val data = LoanData()
            if (!data.create(cache.lend, cache.borrow, cache.amount, cache.paybackAmount, cache.day, cache.collateralItems.takeIf { it.isNotEmpty() })) return@Runnable
            cache.lend.inventory.addItem(data.getNote())
            sendMsg(cache.borrow, "§a§l借金の契約が成立しました！")
            sendMsg(cache.lend, "§a§l借金の契約が成立しました！")
        })
    }

    private fun userData(sender: Player, name: String) {
        if (!sender.hasPermission(OP)) return
        Thread {
            val uuid = Bank.getUUID(name)
            if (uuid == null) {
                sendMsg(sender, "存在しないユーザーです")
                return@Thread
            }
            sendMsg(sender, name)
            sendMsg(sender, "手形の再発行用ID/金額")
            val data = LoanData.getLoanData(uuid)
            data.forEach { sendMsg(sender, "§c§l${it.first}/${format(it.second)}") }
        }.start()
    }

    private fun reissue(sender: Player, id: Int) {
        if (!sender.hasPermission(OP)) return
        Thread {
            val data = LoanData().load(id) ?: run {
                sendMsg(sender, "借金情報が見つかりません")
                return@Thread
            }
            Bukkit.getScheduler().runTask(plugin, Runnable { sender.inventory.addItem(data.getNote()) })
        }.start()
    }

    private fun showCollateral(sender: Player) {
        // 借り手または貸し手として担保を確認
        val cache = cacheMap[sender] ?: cacheMap.values.find { it.lend == sender } ?: run {
            sendMsg(sender, "§c借金の提案がありません")
            return
        }
        if (cache.collateralItems.isEmpty()) {
            sendMsg(sender, "§c担保が設定されていません")
            return
        }
        CollateralGUI.openCollateralViewGUI(sender, cache)
    }

    private fun setCollateral(sender: Player) {
        val cache = cacheMap[sender] ?: run {
            sendMsg(sender, "§c借金の提案がありません")
            return
        }
        if (cache.borrow != sender) {
            sendMsg(sender, "§c借り手のみが担保を設定できます")
            return
        }
        CollateralGUI.openCollateralGUI(sender, cache)
    }

    private fun collectDebt(sender: Player) {
        if (!sender.hasPermission(USER)) return
        
        val item = sender.inventory.itemInMainHand
        if (item.type.isAir) {
            sendMsg(sender, "§c手に手形を持ってください")
            return
        }
        
        val meta = item.itemMeta ?: return
        val id = meta.persistentDataContainer.get(NamespacedKey(plugin, "id"), PersistentDataType.INTEGER) ?: run {
            sendMsg(sender, "§cこれは有効な手形ではありません")
            return
        }

        // 排他制御：同じ手形で複数の処理が同時に実行されないようにする
        if (processingNotes.putIfAbsent(id, true) != null) {
            sendMsg(sender, "§c§lこの手形は現在処理中です。しばらくお待ちください。")
            return
        }
        Thread {
            try {
                val data = LoanData.lendMap[id] ?: LoanData().load(id) ?: run {
                    sendMsg(sender, "§c手形情報が見つかりません")
                    return@Thread
                }
                data.collect(sender, item)
            } finally {
                processingNotes.remove(id)
            }
        }.start()
    }

    private fun collectCollateral(sender: Player) {
        if (!sender.hasPermission(USER)) return
        
        val item = sender.inventory.itemInMainHand
        if (item.type.isAir) {
            sendMsg(sender, "§c手に手形を持ってください")
            return
        }
        
        val meta = item.itemMeta ?: return
        val id = meta.persistentDataContainer.get(NamespacedKey(plugin, "id"), PersistentDataType.INTEGER) ?: run {
            sendMsg(sender, "§cこれは有効な手形ではありません")
            return
        }

        // 排他制御：同じ手形で複数の処理が同時に実行されないようにする
        if (processingNotes.putIfAbsent(id, true) != null) {
            sendMsg(sender, "§c§lこの手形は現在処理中です。しばらくお待ちください。")
            return
        }
        Thread {
            try {
                val data = LoanData.lendMap[id] ?: LoanData().load(id) ?: run {
                    sendMsg(sender, "§c手形情報が見つかりません")
                    return@Thread
                }
                data.collectCollateral(sender, item)
            } finally {
                processingNotes.remove(id)
            }
        }.start()
    }

    data class Cache(
        var day: Int = 0,
        var amount: Double = 0.0,
        var paybackAmount: Double = 0.0,
        var collateralItems: MutableList<ItemStack> = mutableListOf(),  // 担保アイテムリスト
        var lend: Player,
        var borrow: Player
    )
}
