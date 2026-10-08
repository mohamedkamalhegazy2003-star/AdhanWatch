package com.adhan.watch

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.*

enum class Prayer(val ar: String) {
    FAJR("الفجر"), DHUHR("الظهر"), ASR("العصر"), MAGHRIB("المغرب"), ISHA("العشاء")
}

data class City(val ar: String, val lat: Double, val lng: Double)

object Cities {
    val all = listOf(
        City("القاهرة", 30.0444, 31.2357),
        City("الجيزة", 30.0131, 31.2089),
        City("الإسكندرية", 31.2001, 29.9187),
        City("بورسعيد", 31.2653, 32.3019),
        City("السويس", 29.9668, 32.5498),
        City("الإسماعيلية", 30.5965, 32.2715),
        City("دمياط", 31.4165, 31.8133),
        City("المنصورة", 31.0409, 31.3785),
        City("الزقازيق", 30.5877, 31.5020),
        City("طنطا", 30.7865, 31.0004),
        City("الفيوم", 29.3084, 30.8428),
        City("بني سويف", 29.0661, 31.0994),
        City("المنيا", 28.1099, 30.7503),
        City("أسيوط", 27.1783, 31.1859),
        City("سوهاج", 26.5591, 31.6948),
        City("قنا", 26.1551, 32.7160),
        City("الأقصر", 25.6872, 32.6396),
        City("أسوان", 24.0889, 32.8998),
        City("الغردقة", 27.2579, 33.8116),
        City("شرم الشيخ", 27.9158, 34.3300),
        City("مرسى مطروح", 31.3543, 27.2373),
        City("العريش", 31.1316, 33.7984),
    )
}

/** حساب مواقيت الصلاة - طريقة الهيئة المصرية العامة للمساحة (فجر 19.5° / عشاء 17.5°) - العصر شافعي */
object PrayerCalc {
    val ZONE: ZoneId = ZoneId.of("Africa/Cairo")
    private const val FAJR_ANGLE = 19.5
    private const val ISHA_ANGLE = 17.5

    private fun fixAngle(a: Double) = ((a % 360.0) + 360.0) % 360.0
    private fun fixHour(h: Double) = ((h % 24.0) + 24.0) % 24.0
    private fun sinD(d: Double) = sin(Math.toRadians(d))
    private fun cosD(d: Double) = cos(Math.toRadians(d))
    private fun tanD(d: Double) = tan(Math.toRadians(d))
    private fun asinD(x: Double) = Math.toDegrees(asin(x))
    private fun acosD(x: Double) = Math.toDegrees(acos(x.coerceIn(-1.0, 1.0)))
    private fun atan2D(y: Double, x: Double) = Math.toDegrees(atan2(y, x))
    private fun acotD(x: Double) = Math.toDegrees(atan(1.0 / x))

    private fun julian(date: LocalDate): Double {
        var y = date.year
        var m = date.monthValue
        val d = date.dayOfMonth
        if (m <= 2) { y -= 1; m += 12 }
        val a = floor(y / 100.0)
        val b = 2 - a + floor(a / 4.0)
        return floor(365.25 * (y + 4716)) + floor(30.6001 * (m + 1)) + d + b - 1524.5
    }

    /** returns (declination, equation of time) */
    private fun sunPosition(jd: Double): Pair<Double, Double> {
        val d = jd - 2451545.0
        val g = fixAngle(357.529 + 0.98560028 * d)
        val q = fixAngle(280.459 + 0.98564736 * d)
        val l = fixAngle(q + 1.915 * sinD(g) + 0.020 * sinD(2 * g))
        val e = 23.439 - 0.00000036 * d
        val ra = fixHour(atan2D(cosD(e) * sinD(l), cosD(l)) / 15.0)
        val eqt = q / 15.0 - ra
        val decl = asinD(sinD(e) * sinD(l))
        return decl to eqt
    }

    fun times(date: LocalDate, city: City): Map<Prayer, ZonedDateTime> {
        val tz = ZONE.rules.getOffset(date.atTime(12, 0)).totalSeconds / 3600.0
        val jd = julian(date) - city.lng / 360.0

        fun midDay(t: Double) = fixHour(12 - sunPosition(jd + t).second)
        fun angleTime(angle: Double, t: Double, ccw: Boolean): Double {
            val decl = sunPosition(jd + t).first
            val noon = midDay(t)
            val v = acosD((-sinD(angle) - sinD(decl) * sinD(city.lat)) / (cosD(decl) * cosD(city.lat))) / 15.0
            return noon + if (ccw) -v else v
        }
        fun asrTime(t: Double): Double {
            val decl = sunPosition(jd + t).first
            val angle = -acotD(1.0 + tanD(abs(city.lat - decl)))
            return angleTime(angle, t, false)
        }

        val raw = mapOf(
            Prayer.FAJR to angleTime(FAJR_ANGLE, 5.0 / 24, true),
            Prayer.DHUHR to midDay(12.0 / 24),
            Prayer.ASR to asrTime(13.0 / 24),
            Prayer.MAGHRIB to angleTime(0.833, 18.0 / 24, false),
            Prayer.ISHA to angleTime(ISHA_ANGLE, 18.0 / 24, false),
        )
        return raw.mapValues { (_, h) ->
            val local = h + tz - city.lng / 15.0
            date.atStartOfDay().plusMinutes(ceil(local * 60).toLong()).atZone(ZONE)
        }
    }

    /** أقرب صلاة قادمة مفعّلة */
    fun next(now: ZonedDateTime, city: City, isEnabled: (Prayer) -> Boolean): Pair<Prayer, ZonedDateTime>? {
        for (day in 0..2) {
            val t = times(now.toLocalDate().plusDays(day.toLong()), city)
            for (p in Prayer.values()) {
                val z = t[p]!!
                if (isEnabled(p) && z.isAfter(now)) return p to z
            }
        }
        return null
    }
}
