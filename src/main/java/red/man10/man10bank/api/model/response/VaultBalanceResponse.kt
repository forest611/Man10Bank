package red.man10.man10bank.api.model.response

import kotlinx.serialization.Serializable

/**
 * 電子マネー残高レスポンス（VaultProvider 8.1）。
 * - `GET /api/Vault/{uuid}/balance` および deposit/withdraw/set/ensure が返す
 *   `{ "balance": number, "version": number }`。
 * - version は user_vault.Version（単調増加）。キャッシュ適用順序の判定に使う。
 */
@Serializable
data class VaultBalanceResponse(
    val balance: Double,
    val version: Long,
)

/**
 * 電子マネー ⇄ 銀行移動の結果（VaultProvider 11.2）。
 * - `POST /api/Vault/move` が返す `{ "vaultBalance", "bankBalance", "vaultVersion" }`。
 */
@Serializable
data class VaultMoveResponse(
    val vaultBalance: Double,
    val bankBalance: Double,
    val vaultVersion: Long,
)

/**
 * 電子マネー送金(/pay)の結果（VaultProvider 8.1）。
 * 残高 push を持たないため、送金元・送金先の両残高+version を応答で受け取り両キャッシュを収束させる。
 */
@Serializable
data class VaultTransferResponse(
    val fromBalance: Double,
    val fromVersion: Long,
    val toBalance: Double,
    val toVersion: Long,
)

/**
 * session claim の結果（VaultProvider 8.1）。
 * - sessionId: 以後の全書き込みに必須の識別子。
 * - maxBalance: 残高上限の権威設定値（Vault:MaxBalance）。未取得の間は書き込み不可。
 */
@Serializable
data class VaultSessionClaimResponse(
    val sessionId: String,
    val balance: Double,
    val version: Long,
    val maxBalance: Double,
)
