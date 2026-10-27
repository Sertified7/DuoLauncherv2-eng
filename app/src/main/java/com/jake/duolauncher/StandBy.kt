package com.jake.duolauncher

import android.app.Activity
import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.BatteryManager
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

private const val STANDBY_PREFS = "standby"
private const val STANDBY_ENABLED = "enabled"

/** StandBy is on by default and can be switched off in the Home settings. */
fun isStandByEnabled(context: Context) =
    context.getSharedPreferences(STANDBY_PREFS, Context.MODE_PRIVATE).getBoolean(STANDBY_ENABLED, true)

fun setStandByEnabled(context: Context, enabled: Boolean) =
    context.getSharedPreferences(STANDBY_PREFS, Context.MODE_PRIVATE).edit().putBoolean(STANDBY_ENABLED, enabled).apply()

private fun isCharging(context: Context): Boolean {
    val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
    return battery.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
}

private fun batteryPercent(context: Context): Int? =
    context.getSystemService(BatteryManager::class.java)?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        ?.takeIf { it in 0..100 }

/**
 * Watches for the iPhone-style StandBy pose while Home is visible on the cover screen: charging and
 * the phone standing on its long edge. When the pose holds for two seconds, StandBy opens.
 */
class StandByTrigger(private val activity: Activity) : SensorEventListener {
    private val sensors = activity.getSystemService(SensorManager::class.java)
    private var standingSince = 0L
    private var suppressedUntilReset = false
    private var listening = false

    fun start() {
        if (listening || !isStandByEnabled(activity)) return
        val gravity = sensors?.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) ?: return
        listening = sensors.registerListener(this, gravity, SensorManager.SENSOR_DELAY_UI)
        standingSince = 0L
    }

    fun stop() {
        if (listening) sensors?.unregisterListener(this)
        listening = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        val (x, y, z) = event.values
        // Standing on a long edge: gravity runs along the device's x axis, not flat on a table.
        val landscapeUpright = abs(x) > 7f && abs(y) < 4f && abs(z) < 6f
        val cover = activity.resources.configuration.screenWidthDp < 650
        if (!landscapeUpright) { standingSince = 0L; suppressedUntilReset = false; return }
        if (suppressedUntilReset || !cover || !isCharging(activity)) return
        val now = SystemClock.elapsedRealtime()
        if (standingSince == 0L) { standingSince = now; return }
        if (now - standingSince >= 2_000L) {
            suppressedUntilReset = true
            activity.startActivity(Intent(activity, StandByActivity::class.java))
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}

/** Full-screen StandBy: big clock, date, battery and next alarm. Shown over the lock screen, never exposing apps. */
class StandByActivity : ComponentActivity() {
    private val unplugged = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { finish() }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        androidx.core.view.WindowCompat.setDecorFitsSystemWindows(window, false)
        androidx.core.view.WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        registerReceiver(unplugged, IntentFilter(Intent.ACTION_POWER_DISCONNECTED))
        setContent { StandByScreen(onExit = ::finish) }
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        // Unfolding the phone ends StandBy, like picking it up.
        if (newConfig.screenWidthDp >= 650) finish()
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(unplugged) }
        super.onDestroy()
    }
}

@Composable
private fun StandByScreen(onExit: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    var battery by remember { mutableStateOf(batteryPercent(context)) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now(); battery = batteryPercent(context)
            delay(1_000L - System.currentTimeMillis() % 1_000L)
        }
    }
    var weather by remember { mutableStateOf<StandByWeather?>(null) }
    var event by remember { mutableStateOf<StandByEvent?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            loadStandByWeather(context)?.let { weather = it }
            delay(30 * 60_000L)
        }
    }
    LaunchedEffect(now.minute / 5) { event = loadNextEvent(context) }
    // At night StandBy dims to red so it doesn't light up the room, as on iPhone.
    val night = now.hour >= 22 || now.hour < 7
    val ink = if (night) Color(0xFFB3261E) else Color.White
    val soft = ink.copy(alpha = if (night) .75f else .7f)
    val is24h = android.text.format.DateFormat.is24HourFormat(context)
    val nextAlarm = remember(now.minute) {
        context.getSystemService(AlarmManager::class.java)?.nextAlarmClock?.triggerTime?.let {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneId.systemDefault())
        }
    }
    Row(Modifier.fillMaxSize().background(Color.Black)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onExit)
        .padding(horizontal = 36.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1.4f)) {
            Text(now.format(DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm")), color = ink,
                fontSize = 120.sp, fontWeight = FontWeight.Thin, lineHeight = 120.sp, maxLines = 1)
            Text(now.format(DateTimeFormatter.ofPattern("EEEE d MMMM")).replaceFirstChar { it.uppercase() },
                color = soft, fontSize = 22.sp)
        }
        Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
            StandByCard(ink) {
                val w = weather
                if (w != null) {
                    Text("Météo", color = soft, fontSize = 14.sp)
                    Text("${w.symbol}  ${w.temperature}°", color = ink, fontSize = 40.sp, fontWeight = FontWeight.Light, maxLines = 1)
                    Text(w.label + (if (w.high != null && w.low != null) " · ${w.low}° / ${w.high}°" else ""), color = soft, fontSize = 14.sp, maxLines = 1)
                } else {
                    Text("Batterie", color = soft, fontSize = 14.sp)
                    Text(battery?.let { "$it %" } ?: "—", color = ink, fontSize = 40.sp, fontWeight = FontWeight.Light)
                    Text("En charge", color = soft, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            StandByCard(ink) {
                val e = event
                if (e != null) {
                    Text(if (e.allDay) "Aujourd’hui" else if (e.start.toLocalDate() == now.toLocalDate()) "Aujourd’hui · ${e.start.format(DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm a"))}"
                        else "Demain · ${e.start.format(DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm a"))}", color = soft, fontSize = 14.sp)
                    Text(e.title, color = ink, fontSize = 24.sp, fontWeight = FontWeight.Light, maxLines = 2)
                } else {
                    Text("Prochain réveil", color = soft, fontSize = 14.sp)
                    Text(nextAlarm?.format(DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm a")) ?: "Aucun",
                        color = ink, fontSize = 40.sp, fontWeight = FontWeight.Light)
                    Text(nextAlarm?.format(DateTimeFormatter.ofPattern("EEEE")) ?: "", color = soft, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(listOfNotNull(battery?.let { "🔋 $it %" },
                nextAlarm?.takeIf { event != null }?.let { "⏰ " + it.format(DateTimeFormatter.ofPattern(if (is24h) "HH:mm" else "h:mm a")) })
                .joinToString("   "), color = soft, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
        }
    }
}

@Composable
private fun StandByCard(ink: Color, content: @Composable () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(28.dp)).background(ink.copy(alpha = .08f)).padding(20.dp)
        .width(220.dp)) {
        Column { content() }
    }
}
