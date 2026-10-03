package com.pocketcli.runtime.local.service

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
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import com.pocketcli.runtime.local.supervisor.LocalRuntimeState
import com.pocketcli.runtime.local.supervisor.LocalRuntimeSupervisor
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class LocalRuntimeService : Service() {

    companion object {
        const val CHANNEL_ID = "pocketcli_local_runtime"
        const val NOTIFICATION_ID = 2001

        const val ACTION_START = "com.pocketcli.runtime.local.ACTION_START"
        const val ACTION_STOP = "com.pocketcli.runtime.local.ACTION_STOP"
        const val ACTION_KEEP_ALIVE = "com.pocketcli.runtime.local.ACTION_KEEP_ALIVE"
        const val ACTION_ACQUIRE_WAKELOCK = "com.pocketcli.runtime.local.ACTION_ACQUIRE_WAKELOCK"
        const val ACTION_RELEASE_WAKELOCK = "com.pocketcli.runtime.local.ACTION_RELEASE_WAKELOCK"

        const val IDLE_TIMEOUT_MS = 10 * 60 * 1000L // 10 minutes

        fun start(context: Context) {
            val intent = Intent(context, LocalRuntimeService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, LocalRuntimeService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    @Inject
    lateinit var supervisor: LocalRuntimeSupervisor

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var wakeLock: PowerManager.WakeLock? = null
    private var idleJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "PocketCLI:LocalRuntimeWakeLock")

        // Start listening to supervisor state
        serviceScope.launch {
            supervisor.state.collectLatest { state ->
                when (state) {
                    is LocalRuntimeState.Running -> {
                        updateNotification("Сервер запущен на 127.0.0.1:${state.port}")
                        resetIdleTimer()
                    }
                    is LocalRuntimeState.Starting -> {
                        updateNotification("Запуск локального агента...")
                    }
                    is LocalRuntimeState.Stopping -> {
                        updateNotification("Остановка локального агента...")
                    }
                    is LocalRuntimeState.Failed -> {
                        updateNotification("Ошибка: ${state.error}")
                        releaseWakeLock()
                    }
                    is LocalRuntimeState.Stopped -> {
                        releaseWakeLock()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification("Инициализация локального агента...")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }

        when (intent?.action) {
            ACTION_START -> {
                serviceScope.launch {
                    supervisor.startServer()
                }
            }
            ACTION_STOP -> {
                serviceScope.launch {
                    supervisor.stopServer()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
            ACTION_KEEP_ALIVE -> {
                resetIdleTimer()
            }
            ACTION_ACQUIRE_WAKELOCK -> {
                acquireWakeLock()
                resetIdleTimer()
            }
            ACTION_RELEASE_WAKELOCK -> {
                releaseWakeLock()
                resetIdleTimer()
            }
        }

        return START_STICKY
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(30 * 60 * 1000L) // Safety max 30 min
        }
    }

    private fun releaseWakeLock() {
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
    }

    private fun resetIdleTimer() {
        idleJob?.cancel()
        idleJob = serviceScope.launch {
            delay(IDLE_TIMEOUT_MS)
            supervisor.stopServer()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Локальный рантайм PocketCLI",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Фоновая работа локального агента OpenCode"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, LocalRuntimeService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("PocketCLI Local Agent")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Остановить", stopPendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, buildNotification(text))
    }

    override fun onDestroy() {
        serviceScope.cancel()
        releaseWakeLock()
        super.onDestroy()
    }
}
