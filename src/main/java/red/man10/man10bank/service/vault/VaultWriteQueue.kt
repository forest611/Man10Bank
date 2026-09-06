package red.man10.man10bank.service.vault

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID
import java.util.logging.Logger

/**
 * Provider 送信待ちキュー（VaultProvider 6.4）。
 *
 * - Provider が SUCCESS を返した操作を、冪等キー（operationId）付きで保持し登録順に送る。
 * - 通常時はメモリのみ。サービス不調の検知時・shutdown 時に未送信分をデータフォルダ配下へ退避し、
 *   復旧/再起動後に同じ operationId で再送する（サービス側が冪等リプレイで二重適用を防ぐ）。
 * - 業務失敗（4xx）の操作は failed/ へ隔離し、管理者確認対象として残す（VaultProvider 5.6）。
 * - Paper の強制終了でメモリ上の未退避操作が失われ得ることは既知の許容リスク（VaultProvider 15.1）。
 */
class VaultWriteQueue(dataFolder: File, private val logger: Logger) {

    enum class Type { DEPOSIT, WITHDRAW }

    /** キュー1件分の操作。sessionId は保持せず、送信時点の claim から解決する（再接続で claim が更新されるため）。 */
    @Serializable
    data class Op(
        val operationId: String,
        val uuid: String,
        val type: Type,
        val amount: Long,
        val note: String,
        val displayNote: String,
        val createdAtMillis: Long,
    ) {
        fun playerUuid(): UUID = UUID.fromString(uuid)
    }

    private val json = Json { ignoreUnknownKeys = true }
    private val pendingDir = File(dataFolder, "vault-queue/pending")
    private val failedDir = File(dataFolder, "vault-queue/failed")

    private val lock = Any()
    private val queue = ArrayDeque<Op>()

    /** 起動時: 前回退避された未送信操作を作成時刻順に読み込み、キューへ復元する。読めたら件数を返す。 */
    fun loadPersisted(): Int {
        val files = pendingDir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: return 0
        var loaded = 0
        val ops = files.mapNotNull { file ->
            runCatching { json.decodeFromString(Op.serializer(), file.readText()) }
                .onFailure { logger.severe("退避済みキュー操作の読込に失敗しました file=${file.name}: ${it.message}") }
                .getOrNull()
        }.sortedBy { it.createdAtMillis }
        synchronized(lock) {
            for (op in ops) {
                queue.addLast(op)
                loaded++
            }
        }
        return loaded
    }

    /** メモリキューへ登録する（Provider の SUCCESS 前提条件。VaultProvider 5.6）。 */
    fun enqueue(op: Op): Boolean {
        synchronized(lock) { queue.addLast(op) }
        return true
    }

    fun size(): Int = synchronized(lock) { queue.size }

    /** 対象 UUID の未送信操作が残っているか（quit のドレイン待ちに使う）。 */
    fun hasOpsFor(uuid: UUID): Boolean {
        val s = uuid.toString()
        return synchronized(lock) { queue.any { it.uuid == s } }
    }

    /** 先頭の操作（送信対象）。取り出さない。 */
    fun peek(): Op? = synchronized(lock) { queue.firstOrNull() }

    /** 送信成功: 先頭から外し、退避ファイルがあれば削除する。 */
    fun complete(op: Op) {
        synchronized(lock) { queue.remove(op) }
        pendingFile(op).delete()
    }

    /**
     * 業務失敗: キューから外し、failed/ へ隔離する（再送しない。管理者確認対象）。
     */
    fun markFailed(op: Op, reason: String) {
        synchronized(lock) { queue.remove(op) }
        runCatching {
            failedDir.mkdirs()
            failedFile(op).writeText(json.encodeToString(Op.serializer(), op))
            pendingFile(op).delete()
        }.onFailure {
            logger.severe("失敗操作の隔離保存に失敗しました operationId=${op.operationId}: ${it.message}")
        }
        logger.severe(
            "Provider送信キュー操作が業務失敗しました operationId=${op.operationId} uuid=${op.uuid} " +
                "type=${op.type} 金額=${op.amount} 理由=$reason 詳細=vault-queue/failed へ隔離した。手動確認が必要"
        )
    }

    /**
     * 未送信の全操作をディスクへ退避する（サービス不調検知時・shutdown 時。VaultProvider 5.6）。
     * 冪等（既存ファイルは上書き）。退避できなかった操作は severe ログに残す。
     */
    fun persistUnsent() {
        val ops = synchronized(lock) { queue.toList() }
        if (ops.isEmpty()) return
        runCatching { pendingDir.mkdirs() }
        for (op in ops) {
            runCatching {
                pendingFile(op).writeText(json.encodeToString(Op.serializer(), op))
            }.onFailure {
                logger.severe(
                    "キュー操作の退避に失敗しました operationId=${op.operationId} uuid=${op.uuid} " +
                        "type=${op.type} 金額=${op.amount}: ${it.message}"
                )
            }
        }
    }

    private fun pendingFile(op: Op) = File(pendingDir, "${op.createdAtMillis}-${op.operationId}.json")
    private fun failedFile(op: Op) = File(failedDir, "${op.createdAtMillis}-${op.operationId}.json")
}
