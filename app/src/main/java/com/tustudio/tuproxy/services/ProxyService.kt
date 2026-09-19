package com.tustudio.tuproxy.services

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.tustudio.tuproxy.MainActivity
import com.tustudio.tuproxy.engine.ConnectionLog
import com.tustudio.tuproxy.engine.ProxyEngine
import com.tustudio.tuproxy.engine.TrafficStats
import com.tustudio.tuproxy.utils.formatBytes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ProxyUiState(
    val running: Set<String> = emptySet(),
    val rxRate: Long = 0L,
    val txRate: Long = 0L,
    val rxTotal: Long = 0L,
    val txTotal: Long = 0L,
    /** Last 60 (rx, tx) bytes/sec points for the chart. */
    val history: List<Pair<Long, Long>> = emptyList(),
)

/**
 * Foreground service owning [ProxyEngine]. Lives while >= 1 proxy is ON,
 * stops + resets stats when every toggle is OFF.
 *
 * Notifications:
 * - RUNNING: ongoing foreground notification with live rates + Stop action.
 * - STOPPED: one dismissible status notification so users can always tell
 *   whether the proxy is running, even after the service stops.
 */
class ProxyService : Service() {

    companion object {
        const val ACTION_UPDATE = "com.tustudio.tuproxy.action.UPDATE"
        const val ACTION_STOP_ALL = "com.tustudio.tuproxy.action.STOP_ALL"
        const val EXTRA_TYPE = "type"
        const val EXTRA_ENABLED = "enabled"

        private const val CHANNEL_ID = "tuproxy_channel"
        private const val NOTIFICATION_ID = 101
        private const val STOPPED_NOTIFICATION_ID = 102
        private const val HISTORY_MAX = 60

        private val _uiState = MutableStateFlow(ProxyUiState())
        val uiState: StateFlow<ProxyUiState> = _uiState.asStateFlow()

        fun update(context: Context, type: String, enabled: Boolean) {
            val intent = Intent(context, ProxyService::class.java).apply {
                action = ACTION_UPDATE
                putExtra(EXTRA_TYPE, type)
                putExtra(EXTRA_ENABLED, enabled)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopAll(context: Context) {
            val intent = Intent(context, ProxyService::class.java).apply {
                action = ACTION_STOP_ALL
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var ticker: Job? = null
    private var lastRx = 0L
    private var lastTx = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP_ALL -> {
                ProxyEngine.stopAll()
            }
            ACTION_UPDATE -> {
                val type = intent.getStringExtra(EXTRA_TYPE) ?: return START_STICKY
                val enabled = intent.getBooleanExtra(EXTRA_ENABLED, false)
                if (enabled) ProxyEngine.start(type) else ProxyEngine.stop(type)
            }
        }

        val running = ProxyEngine.runningTypes()
        if (running.isEmpty()) {
            cancelNotification(NOTIFICATION_ID)
            showStoppedNotification()
            stopSelf()
            return START_NOT_STICKY
        }

        cancelNotification(STOPPED_NOTIFICATION_ID)
        startForegroundCompat(buildRunningNotification(running, 0L, 0L))
        ensureTicker(running)
        return START_STICKY
    }

    private fun ensureTicker(running: Set<String>) {
        if (ticker?.isActive == true) {
            _uiState.value = _uiState.value.copy(running = running)
            return
        }
        val snap = TrafficStats.snapshot()
        lastRx = snap.first
        lastTx = snap.second
        _uiState.value = _uiState.value.copy(
            running = running,
            rxRate = 0L, txRate = 0L,
            rxTotal = snap.first, txTotal = snap.second,
            history = emptyList(),
        )
        ticker = scope.launch {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            while (isActive) {
                delay(1_000)
                val cur = ProxyEngine.runningTypes()
                if (cur.isEmpty()) {
                    cancelNotification(NOTIFICATION_ID)
                    showStoppedNotification()
                    stopSelf()
                    break
                }
                val (rx, tx) = TrafficStats.snapshot()
                val rxRate = (rx - lastRx).coerceAtLeast(0L)
                val txRate = (tx - lastTx).coerceAtLeast(0L)
                lastRx = rx
                lastTx = tx
                _uiState.value = _uiState.value.copy(
                    running = cur,
                    rxRate = rxRate, txRate = txRate,
                    rxTotal = rx, txTotal = tx,
                    history = (_uiState.value.history + (rxRate to txRate)).takeLast(HISTORY_MAX),
                )
                nm.notify(NOTIFICATION_ID, buildRunningNotification(cur, rxRate, txRate))
            }
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "TuProxy status", NotificationManager.IMPORTANCE_LOW
                ).apply { description = "Local proxy status and live traffic" }
            )
        }
    }

    private fun contentIntent(): PendingIntent =
        PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private fun stopAllIntent(): PendingIntent {
        val intent = Intent(this, ProxyService::class.java).apply { action = ACTION_STOP_ALL }
        return PendingIntent.getService(
            this, 1, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun buildRunningNotification(
        running: Set<String>,
        rxRate: Long,
        txRate: Long,
    ): Notification {
        val names = running.sorted().joinToString("+") { it.uppercase() }
        val text = "↓ ${formatBytes(rxRate)}/s  ↑ ${formatBytes(txRate)}/s — tap to manage"
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TuProxy [$names] is running")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(contentIntent())
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Stop", stopAllIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun showStoppedNotification() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("TuProxy is stopped")
            .setContentText("All proxies are off — tap to start")
            .setSmallIcon(android.R.drawable.ic_menu_share)
            .setContentIntent(contentIntent())
            .setOngoing(false)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        nm.notify(STOPPED_NOTIFICATION_ID, notification)
    }

    private fun cancelNotification(id: Int) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(id)
    }

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID, notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        ticker?.cancel()
        scope.cancel()
        ProxyEngine.stopAll()
        TrafficStats.reset()
        ConnectionLog.clear()
        _uiState.value = ProxyUiState()
        super.onDestroy()
    }
}
