package com.adhan.watch

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import java.time.ZonedDateTime

object Scheduler {
    const val ACTION = "com.adhan.watch.ADHAN"

    private fun pending(c: Context, prayer: String) = PendingIntent.getBroadcast(
        c, 0,
        Intent(c, AdhanReceiver::class.java).setAction(ACTION).putExtra("prayer", prayer),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    /** يجدول أقرب أذان قادم (منبه دقيق يعمل حتى في وضع توفير الطاقة) */
    fun scheduleNext(c: Context) {
        val am = c.getSystemService(AlarmManager::class.java)
        val city = Cities.all[Prefs.city(c)]
        val next = PrayerCalc.next(ZonedDateTime.now(PrayerCalc.ZONE), city) { Prefs.enabled(c, it) }
        if (next == null) {
            am.cancel(pending(c, ""))
            return
        }
        val show = PendingIntent.getActivity(c, 2, Intent(c, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        am.setAlarmClock(
            AlarmManager.AlarmClockInfo(next.second.toInstant().toEpochMilli(), show),
            pending(c, next.first.name)
        )
    }
}

class AdhanReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Scheduler.scheduleNext(c) // جدولة الأذان التالي
        val prayer = i.getStringExtra("prayer") ?: return
        ContextCompat.startForegroundService(
            c, Intent(c, AdhanService::class.java).putExtra("prayer", prayer)
        )
    }
}

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        Scheduler.scheduleNext(c)
    }
}
