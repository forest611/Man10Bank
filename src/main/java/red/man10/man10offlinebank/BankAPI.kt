package red.man10.man10offlinebank

import org.bukkit.plugin.java.JavaPlugin
import red.man10.man10offlinebank.Man10OfflineBank.Companion.bank
import java.util.*

class BankAPI(val plugin : JavaPlugin) {


    /**
     * ユーザーのオフライン口座から出金する
     *
     * @param uuid 出金するユーザーのuuid
     * @param amount 出金する金額(数値は0以上にしてください)
     * @param note 出金の内容(64文字以内で必ず書き込む)
     *
     * @return 出金が成功したらtrue
     *
     */
    fun withdraw(uuid:UUID,amount:Double,note:String):Boolean{
        return bank.withdraw(uuid,amount, plugin, note)
    }

    /**
     * ユーザーのオフライン口座に入金する
     *
     * @param uuid 入金するユーザーのuuid
     * @param amount 入金する金額(数値は0以上にしてください)
     * @param note 入金の内容(64文字以内で必ず書き込む)
     */
    fun deposit(uuid: UUID,amount: Double,note: String){
        bank.deposit(uuid,amount, plugin, note)
    }

    /**
     * @return オフライン口座の残高を返す
     */
    fun getBalance(uuid: UUID): Double {
        return bank.getBalance(uuid)
    }

}