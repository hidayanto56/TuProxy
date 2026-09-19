package com.tustudio.tuproxy.tile

import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.tustudio.tuproxy.engine.ProxyEngine
import com.tustudio.tuproxy.services.ProxyService

/**
 * Quick Settings tile: one tap toggles all three proxies.
 * Tile state mirrors whether any proxy is running.
 */
class ProxyTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        refresh()
    }

    override fun onClick() {
        super.onClick()
        val running = ProxyEngine.runningTypes()
        try {
            if (running.isEmpty()) {
                // Turn everything ON.
                ProxyService.update(this, ProxyEngine.TYPE_HTTP, true)
                ProxyService.update(this, ProxyEngine.TYPE_HTTPS, true)
                ProxyService.update(this, ProxyEngine.TYPE_SOCKS, true)
            } else {
                ProxyService.stopAll(this)
            }
        } catch (_: SecurityException) {
            // Android 14+ can refuse an FGS start from background; user can
            // toggle from the app instead. Tile refreshes on next listen.
        } finally {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Give the service a moment, then reflect the new state.
                android.os.Handler(mainLooper).postDelayed({ refresh() }, 800)
            } else {
                refresh()
            }
        }
    }

    private fun refresh() {
        val tile = qsTile ?: return
        tile.state = if (ProxyEngine.runningTypes().isEmpty()) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.updateTile()
    }
}
