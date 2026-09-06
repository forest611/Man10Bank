package red.man10.man10bank.listener

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import red.man10.man10bank.service.vault.VaultService

/**
 * 電子マネー session のライフサイクル（VaultProvider 5.8）。
 * - join: 台帳を LOADING で確保し、session claim（確定残高+sessionId の取得）を非同期に行う。
 *   claim 完了までの書き込みは fail-closed で拒否される。
 * - quit: 新規書き込みを止め、送信キューのドレイン後に release・退避する（VaultService.beginQuit）。
 */
class VaultLifecycleListener(
    private val scope: CoroutineScope,
    private val service: VaultService,
) : Listener {

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        // providerEnabled=false（電子マネー機能停止中）は claim を試みない。
        if (!service.isProviderActive()) return
        val uuid = event.player.uniqueId
        val name = event.player.name
        service.cache.markLoading(uuid)
        scope.launch { service.claim(uuid, name) }
    }

    @EventHandler
    fun onQuit(event: PlayerQuitEvent) {
        if (!service.isProviderActive()) return
        service.beginQuit(event.player.uniqueId)
    }
}
