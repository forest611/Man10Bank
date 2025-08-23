package red.man10.man10bank.util

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.Locale

/**
 * 金額など数値の表示用フォーマッタ。
 * 表示仕様: 3桁カンマ区切り + 整数のみ（小数部は切り捨て）。
 */
object StringFormat {
    /**
     * 金額の表示文字列を返す。
     */
    fun money(amount: BigDecimal): String {
        return String.format(Locale.US, "%,d", amount.setScale(0, RoundingMode.DOWN).toBigInteger())
    }
}
