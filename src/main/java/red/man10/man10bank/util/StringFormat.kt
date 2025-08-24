package red.man10.man10bank.util

import java.math.BigDecimal
import java.math.BigInteger
import java.math.RoundingMode
import java.util.Locale

object StringFormat {

    fun money(amount: BigDecimal): String {
        return String.format(Locale.US, "%,f", amount.setScale(0, RoundingMode.DOWN).toDouble())
    }
}
