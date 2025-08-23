package red.man10.man10bank.shared

import java.math.BigDecimal

/**
 * 各サービスで共通利用する結果型。
 * - 結果の区分は ResultCode に集約
 * - 付随情報として新残高などの数値を返す場合は balance を使用
 */
enum class ResultCode {
    SUCCESS,
    INVALID_AMOUNT,
    INSUFFICIENT_FUNDS,
    PROVIDER_UNAVAILABLE,
    OVERFLOW,
    FAILURE,
}

/**
 * 結果の本体。メッセージは呼び出し側で Enum を元に生成してください。
 */
data class OperationResult(
    val code: ResultCode,
    val balance: BigDecimal? = null,
)
