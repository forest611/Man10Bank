package red.man10.man10bank.util

import org.bukkit.Bukkit
import org.bukkit.block.ShulkerBox
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.BlockStateMeta
import org.bukkit.inventory.meta.BundleMeta
import red.man10.man10bank.Config
import java.text.Normalizer

object Utility{

    const val prefix = "§l[§e§lMan10Bank§f§l]§f"

    fun msg(p: Player, msg: String) {
        p.sendMessage(prefix + msg)
    }

    fun msg(sender: CommandSender,msg:String){
        sender.sendMessage(prefix + msg)
    }

    fun format(amount: Double, digit: Int = 0): String {
        return String.format("%,.${digit}f", amount)
    }

    fun ZenkakuToHankaku(number: String): Double? {
        val normalize = Normalizer.normalize(number, Normalizer.Form.NFKC)
        return normalize.toDoubleOrNull()
    }

    fun parse(str:String) : Double? {
        return ZenkakuToHankaku(str.replace(",",""))
    }

    fun loggerInfo(str: String?){
        Bukkit.getLogger().info("[Man10Bank]$str")
    }

    fun loggerWarn(str: String?){
        Bukkit.getLogger().warning("[Man10Bank]$str")
    }

    fun loggerDebug(str: String?){
        if (!Config.debugMode)return
        Bukkit.getLogger().warning("[Man10BankDebug]$str")
    }

    fun getShulkerItem(item: ItemStack?):List<ItemStack>{

        val meta = item?.itemMeta?: emptyList<ItemStack>()

        if (meta is BlockStateMeta && meta.blockState is ShulkerBox && meta.hasBlockState()){

            val shulker = meta.blockState as ShulkerBox
            return shulker.inventory.toList()
        }
        return emptyList()
    }

    fun getBundleItem(item: ItemStack?):List<ItemStack>{

        val meta = item?.itemMeta?: emptyList<ItemStack>()

        if (meta is BundleMeta){
            return meta.items
        }

        return emptyList()
    }

}