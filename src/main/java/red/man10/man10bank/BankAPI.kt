package red.man10.man10bank

import org.bukkit.plugin.java.JavaPlugin
import java.util.*

class BankAPI(private val plugin : JavaPlugin) {


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
    fun withdraw(uuid:UUID,amount:Double,note:String,displayNote:String):Boolean{
        return Bank.withdraw(uuid,amount, plugin, note,displayNote).first == 0
    }

    @Deprecated("displayNoteが設定できない", ReplaceWith("DisplayNoteつき"),DeprecationLevel.WARNING)
    fun withdraw(uuid:UUID,amount:Double,note:String):Boolean{
        return Bank.withdraw(uuid,amount, plugin, note,note).first == 0
    }

    /**
     * ユーザーのオフライン口座に入金する
     *
     * @param uuid 入金するユーザーのuuid
     * @param amount 入金する金額(数値は0以上にしてください)
     * @param note 入金の内容(64文字以内で必ず書き込む)
     * @param displayNote 入金の内容(表示用)
     */
    fun deposit(uuid: UUID,amount: Double,note: String,displayNote: String){
        Bank.deposit(uuid,amount, plugin, note,displayNote)
    }

    //アップデートに対応してない場合のための処理
    @Deprecated("displayNoteが設定できない", ReplaceWith("DisplayNoteつき"),DeprecationLevel.WARNING)
    fun deposit(uuid: UUID,amount: Double,note: String){
        Bank.deposit(uuid,amount, plugin, note,note)
    }

    fun asyncDeposit(uuid: UUID,amount: Double,note: String,displayNote: String,callback:Bank.BankTransaction){
        Bank.asyncDeposit(uuid, amount, plugin, note, displayNote, callback)
    }

    fun asyncWithdraw(uuid: UUID,amount: Double,note: String,displayNote: String,callback:Bank.BankTransaction){
        Bank.asyncWithdraw(uuid, amount, plugin, note, displayNote, callback)
    }

    fun asyncGetBalance(uuid:UUID,callback: Bank.BankTransaction){
        Bank.asyncGetBalance(uuid, callback)
    }

    /**
     * @return オフライン口座の残高を返す
     */
    fun getBalance(uuid: UUID): Double {
        return Bank.getBalance(uuid)
    }

}