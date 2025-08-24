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

/**
 * ResultCode に対応するデフォルトのエラーメッセージを返します。
 * - 成功時 (SUCCESS) は null を返します（エラーなし）。
 * - ユーザー向けの自然な日本語で統一しています。
 */
fun ResultCode.errorMessage(): String? = when (this) {
    ResultCode.SUCCESS -> null
    ResultCode.INVALID_AMOUNT -> "金額が不正です。"
    ResultCode.INSUFFICIENT_FUNDS -> "残高が不足しています。"
    ResultCode.PROVIDER_UNAVAILABLE -> "外部プロバイダーが利用できません。"
    ResultCode.OVERFLOW -> "金額が上限を超えています。"
    ResultCode.FAILURE -> "処理に失敗しました。"
}
