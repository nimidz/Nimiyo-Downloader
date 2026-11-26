package com.nimiyo.nativeapp.download

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicBoolean

class DownloadForegroundService : Service() {

    companion object {
        private const val EXTRA_COMPLETED = "extra_completed"
        private const val EXTRA_TOTAL = "extra_total"
        private const val EXTRA_PERCENT = "extra_percent"
        private const val EXTRA_QUICKSAVE_ONLY = "extra_quicksave_only"

        private val isRunning = AtomicBoolean(false)
        private val desiredRunning = AtomicBoolean(false)

        fun startOrUpdate(
            context: Context,
            completed: Int,
            total: Int,
            percent: Int,
            quickSaveOnly: Boolean,
        ) {
            val appCtx = context.applicationContext
            desiredRunning.set(true)
            if (isRunning.get()) {
                runCatching {
                    val helper = NotificationHelper(appCtx)
                    val notif = helper.buildDownloadForegroundNotification(
                        completed = completed,
                        total = total,
                        currentPercent = percent,
                        quickSaveOnly = quickSaveOnly,
                    )
                    val nm = appCtx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                    nm.notify(NotificationHelper.NOTIF_ID_MAIN_DOWNLOAD, notif)
                }
                return
            }
            val intent = Intent(appCtx, DownloadForegroundService::class.java).apply {
                putExtra(EXTRA_COMPLETED, completed)
                putExtra(EXTRA_TOTAL, total)
                putExtra(EXTRA_PERCENT, percent)
                putExtra(EXTRA_QUICKSAVE_ONLY, quickSaveOnly)
            }
            runCatching {
                ContextCompat.startForegroundService(appCtx, intent)
            }
        }

        fun stop(context: Context) {
            desiredRunning.set(false)
            if (!isRunning.get()) return
            runCatching {
                context.applicationContext.stopService(Intent(context.applicationContext, DownloadForegroundService::class.java))
            }
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var notifHelper: NotificationHelper

    override fun onCreate() {
        super.onCreate()
        notifHelper = NotificationHelper(applicationContext)
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Nimiyo:DownloadWakeLock").apply {
            setReferenceCounted(false)
            acquire(30 * 60 * 1000L) // 30 minutes max safety timeout
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val completed = intent?.getIntExtra(EXTRA_COMPLETED, 0) ?: 0
        val total = intent?.getIntExtra(EXTRA_TOTAL, 1)?.coerceAtLeast(1) ?: 1
        val percent = intent?.getIntExtra(EXTRA_PERCENT, 0) ?: 0
        val quickSaveOnly = intent?.getBooleanExtra(EXTRA_QUICKSAVE_ONLY, false) ?: false

        val notif = notifHelper.buildDownloadForegroundNotification(
            completed = completed,
            total = total,
            currentPercent = percent,
            quickSaveOnly = quickSaveOnly,
        )

        val fgType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, NotificationHelper.NOTIF_ID_MAIN_DOWNLOAD, notif, fgType)
        if (intent == null || !desiredRunning.get()) {
            isRunning.set(false)
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        } else {
            isRunning.set(true)
        }
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep service running while downloads are active even if user swipes app from Recents.
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        isRunning.set(false)
        runCatching {
            if (wakeLock?.isHeld == true) wakeLock?.release()
        }
        wakeLock = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

