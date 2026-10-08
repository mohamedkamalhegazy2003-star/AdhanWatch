package com.adhan.watch

import android.content.Context

object Prefs {
    private fun sp(c: Context) = c.getSharedPreferences("adhan", Context.MODE_PRIVATE)

    fun city(c: Context): Int = sp(c).getInt("city", 0).coerceIn(Cities.all.indices)
    fun setCity(c: Context, i: Int) = sp(c).edit().putInt("city", i).apply()

    fun voice(c: Context): String? {
        val list = Voices.list(c)
        val s = sp(c).getString("voice", null)
        return if (s != null && s in list) s else list.firstOrNull()
    }
    fun setVoice(c: Context, v: String) = sp(c).edit().putString("voice", v).apply()

    fun enabled(c: Context, p: Prayer) = sp(c).getBoolean("on_${p.name}", true)
    fun setEnabled(c: Context, p: Prayer, v: Boolean) = sp(c).edit().putBoolean("on_${p.name}", v).apply()
}
