package red.man10.man10bank.api.model.request

import kotlinx.serialization.Serializable

/**
 * 電子マネー(Vault Provider)系のリクエストモデル。
 * - 金額は Bank 系と同様に Double（境界で整数化済み）で送る。
 * - すべての書き込みは対象 UUID の session claim と一致する sessionId を必須とする
 *   （単一書き込み者。VaultProvider 8.2）。
 * - operationId は Provider 送信キューの冪等キー（deposit/withdraw のみ。再送に同一結果が返る）。
 */
@Serializable
data class VaultDepositRequest(
    val uuid: String,
    val amount: Double,
    val pluginName: String,
    val note: String,
    val displayNote: String,
    val server: String,
    val sessionId: String,
    val operationId: String? = null,
    val source: String? = null,
)

@Serializable
data class VaultWithdrawRequest(
    val uuid: String,
    val amount: Double,
    val pluginName: String,
    val note: String,
    val displayNote: String,
    val server: String,
    val sessionId: String,
    val operationId: String? = null,
    val source: String? = null,
)

/**
 * 電子マネー送金(/pay)。fromUuid から toUuid へ amount を単一トランザクションで移動する。
 * 送金元・送金先の両方が自サーバーに在席（claim 保持）している場合のみ実行する（VaultProvider 11.3）。
 */
@Serializable
data class VaultTransferRequest(
    val fromUuid: String,
    val toUuid: String,
    val amount: Double,
    val pluginName: String,
    val note: String,
    val displayNote: String,
    val server: String,
    val fromSessionId: String,
    val toSessionId: String,
)

/**
 * 管理用: 電子マネー残高を絶対値で設定する。
 * 他の書き込みと同様、対象が自サーバーに在席（claim 保持）している場合のみ実行できる（VaultProvider 5.4）。
 */
@Serializable
data class VaultSetRequest(
    val uuid: String,
    val amount: Double,
    val pluginName: String,
    val note: String,
    val displayNote: String,
    val server: String,
    val sessionId: String,
)

/** 電子マネー ⇄ 銀行残高の移動方向（サーバーの enum 名と一致させる）。 */
enum class VaultMoveDirection { VaultToBank, BankToVault }

/**
 * 電子マネー ⇄ 銀行残高を 1 トランザクションで移動する（ATM/`/deposit`/`/withdraw` 用。VaultProvider 11.2）。
 * direction はサーバーの enum 名（"VaultToBank" / "BankToVault"）を文字列で送る。
 */
@Serializable
data class VaultMoveRequest(
    val uuid: String,
    val amount: Double,
    val direction: String,
    val pluginName: String,
    val note: String,
    val displayNote: String,
    val server: String,
    val sessionId: String,
) {
    constructor(
        uuid: String,
        amount: Double,
        direction: VaultMoveDirection,
        pluginName: String,
        note: String,
        displayNote: String,
        server: String,
        sessionId: String,
    ) : this(uuid, amount, direction.name, pluginName, note, displayNote, server, sessionId)
}

/**
 * join 時の session claim（後勝ち。VaultProvider 5.8）。
 * 応答で sessionId・確定残高・version・残高上限（権威設定値）を受け取る。
 */
@Serializable
data class VaultSessionClaimRequest(
    val uuid: String,
    val player: String,
    val server: String,
)

/** quit 時の session release（sessionId 一致時のみ解除。不一致でも 204）。 */
@Serializable
data class VaultSessionReleaseRequest(
    val uuid: String,
    val sessionId: String,
    val server: String,
)
