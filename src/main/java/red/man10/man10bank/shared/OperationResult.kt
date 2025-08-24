package red.man10.man10bank.shared

import java.math.BigDecimal

/**
 * 各サービスで共通利用する結果型。
 * - 結果の区分は ResultCode に集約
 * - 付随情報として新残高などの数値を返す場合は balance を使用
 */
enum class ResultCode(val message: String) {
    SUCCESS("成功"),
    INVALID_AMOUNT("金額が不正です"),
    INSUFFICIENT_FUNDS("残高が不足しています"),
    PROVIDER_UNAVAILABLE("外部プロバイダーが利用できません"),
    OVERFLOW("金額が上限を超えています"),
    FAILURE("処理に失敗しました"),
}

/**
 * 結果の本体。メッセージは呼び出し側で Enum を元に生成してください。
 */
data class OperationResult(
    val code: ResultCode,
    val balance: BigDecimal? = null,
)

