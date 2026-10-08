package com.adhan.watch

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.content.ContextCompat
import androidx.wear.compose.material.*
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Scheduler.scheduleNext(this)
        setContent { App() }
    }
}

private val fmt = DateTimeFormatter.ofPattern("h:mm a", Locale("ar", "EG"))

@Composable
fun App() {
    val ctx = LocalContext.current
    var screen by remember { mutableStateOf("home") }
    var cityIdx by remember { mutableStateOf(Prefs.city(ctx)) }
    var voice by remember { mutableStateOf(Prefs.voice(ctx)) }
    var previewing by remember { mutableStateOf(false) }
    val enabled = remember {
        mutableStateMapOf<Prayer, Boolean>().apply { Prayer.values().forEach { put(it, Prefs.enabled(ctx, it)) } }
    }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    BackHandler(enabled = screen != "home") { screen = "home" }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme {
            Scaffold(timeText = { TimeText() }) {
                when (screen) {
                    "city" -> ScalingLazyColumn(Modifier.fillMaxWidth()) {
                        item { ListHeader { Text("اختر المدينة") } }
                        items(Cities.all.size) { i ->
                            Chip(
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text((if (i == cityIdx) "✓ " else "") + Cities.all[i].ar) },
                                onClick = {
                                    cityIdx = i
                                    Prefs.setCity(ctx, i)
                                    Scheduler.scheduleNext(ctx)
                                    screen = "home"
                                }
                            )
                        }
                    }
                    "voice" -> {
                        val voices = remember { Voices.list(ctx) }
                        ScalingLazyColumn(Modifier.fillMaxWidth()) {
                            item { ListHeader { Text("اختر المؤذن") } }
                            if (voices.isEmpty()) {
                                item {
                                    Text(
                                        "لا توجد ملفات صوتية. أضف ملفات mp3 في app/src/main/assets/adhan ثم أعد البناء. حالياً يُستخدم صوت المنبه.",
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                            item {
                                Chip(
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text(if (previewing) "إيقاف التجربة" else "تجربة الصوت") },
                                    onClick = {
                                        if (previewing) { AdhanPlayer.stop(); previewing = false }
                                        else { previewing = true; AdhanPlayer.play(ctx, voice) { previewing = false } }
                                    }
                                )
                            }
                            items(voices.size) { i ->
                                val v = voices[i]
                                Chip(
                                    modifier = Modifier.fillMaxWidth(),
                                    label = { Text((if (v == voice) "✓ " else "") + Voices.label(v)) },
                                    onClick = {
                                        AdhanPlayer.stop(); previewing = false
                                        voice = v
                                        Prefs.setVoice(ctx, v)
                                    }
                                )
                            }
                        }
                    }
                    else -> Home(
                        cityIdx, voice, enabled,
                        onToggle = { p, v ->
                            enabled[p] = v
                            Prefs.setEnabled(ctx, p, v)
                            Scheduler.scheduleNext(ctx)
                        },
                        onCity = { screen = "city" },
                        onVoice = { screen = "voice" }
                    )
                }
            }
        }
    }
}

@Composable
private fun Home(
    cityIdx: Int,
    voice: String?,
    enabled: Map<Prayer, Boolean>,
    onToggle: (Prayer, Boolean) -> Unit,
    onCity: () -> Unit,
    onVoice: () -> Unit
) {
    val city = Cities.all[cityIdx]
    val now = ZonedDateTime.now(PrayerCalc.ZONE)
    val times = PrayerCalc.times(now.toLocalDate(), city)
    val next = PrayerCalc.next(now, city) { enabled[it] == true }

    ScalingLazyColumn(Modifier.fillMaxWidth()) {
        item { ListHeader { Text("الأذان") } }
        item {
            Text(
                if (next != null) "القادم: ${next.first.ar} ${fmt.format(next.second)}" else "كل الأذانات متوقفة",
                textAlign = TextAlign.Center
            )
        }
        Prayer.values().forEach { p ->
            item {
                val on = enabled[p] == true
                ToggleChip(
                    modifier = Modifier.fillMaxWidth(),
                    checked = on,
                    onCheckedChange = { onToggle(p, it) },
                    label = { Text(p.ar) },
                    secondaryLabel = { Text(fmt.format(times[p]!!)) },
                    toggleControl = {
                        Icon(
                            imageVector = ToggleChipDefaults.switchIcon(checked = on),
                            contentDescription = null
                        )
                    }
                )
            }
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("المدينة") },
                secondaryLabel = { Text(city.ar) },
                onClick = onCity
            )
        }
        item {
            Chip(
                modifier = Modifier.fillMaxWidth(),
                label = { Text("المؤذن") },
                secondaryLabel = { Text(voice?.let { Voices.label(it) } ?: "صوت المنبه الافتراضي") },
                onClick = onVoice
            )
        }
    }
}
