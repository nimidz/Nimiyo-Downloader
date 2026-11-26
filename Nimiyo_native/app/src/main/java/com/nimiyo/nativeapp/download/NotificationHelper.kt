package com.nimiyo.nativeapp.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import com.nimiyo.nativeapp.MainActivity
import com.nimiyo.nativeapp.R
import com.nimiyo.nativeapp.core.AppLanguage
import com.nimiyo.nativeapp.core.Strings
import com.nimiyo.nativeapp.core.StringsI18n

class NotificationHelper(private val context: Context) {
    companion object {
        const val CHANNEL_DOWNLOAD = "nimiyo_native_downloads"
        const val CHANNEL_MUSIC = "nimiyo_native_music"
        const val NOTIF_ID_MAIN_DOWNLOAD = 8801
        const val NOTIF_ID_QUICKSAVE = 8802
        const val NOTIF_ID_MUSIC = 8803
        const val EXTRA_OPEN_BALLOON = "open_download_balloon"
        const val EXTRA_OPEN_PLAYER = "open_music_player"
    }

    private val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val largeNotifIcon by lazy {
        BitmapFactory.decodeResource(context.resources, R.drawable.icon_untukdi_aboutthisapp)
    }

    private fun currentStrings(): Strings {
        val code = runCatching {
            context.applicationContext
                .getSharedPreferences("nimiyo_native_settings", Context.MODE_PRIVATE)
                .getString("language", "system")
        }.getOrNull() ?: "system"
        return StringsI18n.get(AppLanguage.fromCode(code))
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val dlChan = NotificationChannel(
                CHANNEL_DOWNLOAD,
                "Nimiyo Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            val musicChan = NotificationChannel(
                CHANNEL_MUSIC,
                "Nimiyo Music Player",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setShowBadge(false)
                setSound(null, null)
                enableVibration(false)
            }
            nm.createNotificationChannel(dlChan)
            nm.createNotificationChannel(musicChan)
        }
    }

    fun buildDownloadForegroundNotification(
        completed: Int,
        total: Int,
        currentPercent: Int,
        quickSaveOnly: Boolean = false,
    ): android.app.Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_BALLOON, true)
        }
        val pi = PendingIntent.getActivity(
            context,
            8801,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val strings = currentStrings()
        val title = if (quickSaveOnly) {
            if (total > 1) "${strings.ui.notifQuicksaveTitle} ($completed/$total)" else strings.ui.notifQuicksaveTitle
        } else {
            "($completed/$total) ${strings.downloadingOngoingBody}"
        }
        return NotificationCompat.Builder(context, CHANNEL_DOWNLOAD)
            .setSmallIcon(R.drawable.ic_stat_nimiyo)
            .setLargeIcon(largeNotifIcon)
            .setContentTitle(title)
            .setContentText("$currentPercent%")
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .setProgress(100, currentPercent.coerceIn(0, 100), currentPercent <= 0)
            .setContentIntent(pi)
            .build()
    }

    fun updateMainDownloadProgress(completed: Int, total: Int, currentPercent: Int, isOngoing: Boolean) {
        if (total <= 0 || !isOngoing) {
            nm.cancel(NOTIF_ID_MAIN_DOWNLOAD)
            return
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_BALLOON, true)
        }
        val pi = PendingIntent.getActivity(
            context,
            8801,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val strings = currentStrings()
        val title = "($completed/$total) ${strings.downloadingOngoingBody}"
        val notif = NotificationCompat.Builder(context, CHANNEL_DOWNLOAD)
            .setSmallIcon(R.drawable.ic_stat_nimiyo)
            .setLargeIcon(largeNotifIcon)
            .setContentTitle(title)
            .setContentText("$currentPercent%")
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .setProgress(100, currentPercent.coerceIn(0, 100), currentPercent <= 0)
            .setContentIntent(pi)
            .build()

        nm.notify(NOTIF_ID_MAIN_DOWNLOAD, notif)
    }

    fun updateQuickSaveProgress(completed: Int, total: Int, currentPercent: Int, isOngoing: Boolean) {
        if (total <= 0 || !isOngoing) {
            nm.cancel(NOTIF_ID_QUICKSAVE)
            return
        }
        val strings = currentStrings()
        val title = if (total > 1) {
            "${strings.ui.notifQuicksaveTitle} ($completed/$total)"
        } else {
            strings.ui.notifQuicksaveTitle
        }
        val notif = NotificationCompat.Builder(context, CHANNEL_DOWNLOAD)
            .setSmallIcon(R.drawable.ic_stat_nimiyo)
            .setLargeIcon(largeNotifIcon)
            .setContentTitle(title)
            .setContentText("${strings.downloadingOngoingBody} ($currentPercent%)")
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setOngoing(true)
            .setProgress(100, currentPercent.coerceIn(0, 100), currentPercent <= 0)
            .build()

        nm.notify(NOTIF_ID_QUICKSAVE, notif)
    }

    fun cancelMain() = nm.cancel(NOTIF_ID_MAIN_DOWNLOAD)
    fun cancelQuickSave() = nm.cancel(NOTIF_ID_QUICKSAVE)
}

