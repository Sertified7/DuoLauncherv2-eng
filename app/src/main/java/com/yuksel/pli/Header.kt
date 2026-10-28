package com.yuksel.pli

import android.content.Intent
import android.provider.AlarmClock
import android.text.format.DateFormat
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AcUnit
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.BatteryStd
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Dehaze
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.WbCloudy
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val FR = Locale.FRANCE

@Composable
fun rememberNow(): LocalDateTime {
    val now by produceState(LocalDateTime.now()) {
        while (true) {
            value = LocalDateTime.now()
            delay(1000L * (60 - value.second).coerceAtLeast(1))
        }
    }
    return now
}

fun skyIcon(sky: Sky): ImageVector = when (sky) {
    Sky.CLEAR -> Icons.Rounded.WbSunny
    Sky.PARTLY -> Icons.Rounded.WbCloudy
    Sky.CLOUDY -> Icons.Rounded.Cloud
    Sky.FOG -> Icons.Rounded.Dehaze
    Sky.DRIZZLE, Sky.RAIN -> Icons.Rounded.Umbrella
    Sky.SNOW -> Icons.Rounded.AcUnit
    Sky.STORM -> Icons.Rounded.Thunderstorm
}

fun capitalize(text: String) = text.replaceFirstChar { it.titlecase(FR) }

/** Date, huge iridescent time and the glance chips (weather, next event, battery). */
@Composable
fun ClockHeader(today: TodayState, clockSize: TextUnit, compact: Boolean, modifier: Modifier = Modifier,
    onWeather: () -> Unit, trailing: (@Composable () -> Unit)? = null,
    nowPlaying: NowPlaying? = null, onOpenPlaying: (String) -> Unit = {}) {
    val context = LocalContext.current
    val now = rememberNow()
    val pattern = if (DateFormat.is24HourFormat(context)) "H:mm" else "h:mm"
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text((if (compact) now.format(DateTimeFormatter.ofPattern("EEE d MMM", FR)) else
                    now.format(DateTimeFormatter.ofPattern("EEEE · d MMMM", FR))).uppercase(FR),
                    color = Holo.Muted, fontSize = 12.sp, letterSpacing = 3.sp, maxLines = 1)
                IridescentText(now.format(DateTimeFormatter.ofPattern(pattern)), clockSize,
                    Modifier.clickable { runCatching { context.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
                        .semantics { contentDescription = "Heure ; ouvrir l’horloge" })
            }
            trailing?.invoke()
        }
        // Fixed height: music replaces the chips while playing, without moving the icons below.
        if (!compact) Box(Modifier.padding(top = 10.dp).height(64.dp).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
        if (nowPlaying != null) NowPlayingCard(nowPlaying, compactCard = true) { onOpenPlaying(nowPlaying.packageName) }
        else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            today.weather?.let { w -> Chip("${w.temperature}° ${w.label}", skyIcon(w.sky), onWeather) }
            today.events.firstOrNull { !it.allDay }?.let { e ->
                Chip("${e.title} ${e.start.format(DateTimeFormatter.ofPattern(pattern))}", Icons.Rounded.Event) { openCalendar(context) }
            }
            today.battery?.let { b ->
                Chip("${b.level} %", if (b.charging) Icons.Rounded.BatteryChargingFull else Icons.Rounded.BatteryStd) {
                    runCatching { context.startActivity(Intent(Intent.ACTION_POWER_USAGE_SUMMARY).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
        }
        }
    }
}

fun openCalendar(context: android.content.Context) = runCatching {
    val uri = android.provider.CalendarContract.CONTENT_URI.buildUpon().appendPath("time")
        .appendPath(System.currentTimeMillis().toString()).build()
    context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

@Composable
private fun Chip(text: String, icon: ImageVector, onClick: () -> Unit) {
    Row(Modifier.height(32.dp).widthIn(max = 180.dp).glass(RoundedCornerShape(50)).clickable(onClick = onClick)
        .padding(horizontal = 11.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = Holo.Cyan, modifier = Modifier.size(15.dp))
        Text(text, color = Holo.Soft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp))
    }
}

/** "Lecture en cours": artwork, title, controls. Tapping it opens the playing app. */
@Composable
fun NowPlayingCard(playing: NowPlaying, modifier: Modifier = Modifier, compactCard: Boolean = false, onOpen: () -> Unit) {
    Row(modifier.fillMaxWidth().glass(RoundedCornerShape(26.dp)).clickable(onClick = onOpen).padding(if (compactCard) 8.dp else 12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(Holo.Cyan, Holo.Violet))), contentAlignment = Alignment.Center) {
            if (playing.art != null) Image(playing.art, null, Modifier.size(48.dp))
            else Icon(Icons.Rounded.MusicNote, null, tint = Color(0xFF120A22))
        }
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(playing.title, color = Holo.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (playing.artist.isNotBlank()) Text(playing.artist, color = Holo.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (playing.duration > 0) Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = .14f), CircleShape)) {
                Box(Modifier.fillMaxWidth((playing.position.toFloat() / playing.duration).coerceIn(0f, 1f)).height(3.dp)
                    .background(Holo.Violet, CircleShape))
            }
        }
        IconButton(onClick = MediaWatcher::previous, Modifier.size(40.dp)) { Icon(Icons.Rounded.SkipPrevious, "Précédent", tint = Holo.Ink) }
        IconButton(onClick = MediaWatcher::toggle, Modifier.size(44.dp).glass(CircleShape, .1f)) {
            Icon(if (playing.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing.playing) "Pause" else "Lecture", tint = Holo.Ink)
        }
        IconButton(onClick = MediaWatcher::next, Modifier.size(40.dp)) { Icon(Icons.Rounded.SkipNext, "Suivant", tint = Holo.Ink) }
    }
}
