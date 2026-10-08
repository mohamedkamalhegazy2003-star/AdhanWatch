package com.adhan.watch

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

class AdhanService : Service() {
    companion object {
        const val ACTION_STOP = "com.adhan.watch.STOP"
        private const val CH = "adhan"
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            AdhanPlayer.stop()
            stopSelf()
            return START_NOT_STICKY
        }
        val prayer = runCatching { Prayer.valueOf(intent?.getStringExtra("prayer")!!) }.getOrNull()

        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH, "الأذان", NotificationManager.IMPORTANCE_HIGH).apply { setSound(null, null) }
        )
        val stopPi = PendingIntent.getService(
            this, 0, Intent(this, AdhanService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE
        )
        val openPi = PendingIntent.getActivity(
            this, 1, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = NotificationCompat.Builder(this, CH)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("حان موعد أذان ${prayer?.ar ?: ""}")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(openPi)
            .addAction(0, "إيقاف", stopPi)
            .build()
        ServiceCompat.startForeground(this, 1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)

        AdhanPlayer.play(this, Prefs.voice(this)) { stopSelf() }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        AdhanPlayer.stop()
        super.onDestroy()
    }
}
