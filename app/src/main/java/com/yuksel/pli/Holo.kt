package com.yuksel.pli

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.compose.animation.core.animateOffsetAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** The holographic palette from the Pli mockups. */
object Holo {
    val Base = Color(0xFF05060A)
    val Ink = Color(0xFFF4F2FF)
    val Soft = Color(0xFFE6E2FA)
    val Muted = Color(0xFFB9B4D6)
    val Cyan = Color(0xFF9BE7FF)
    val Violet = Color(0xFFC7A6FF)
    val Pink = Color(0xFFFFB3D9)
    val Mint = Color(0xFF9BFFD8)
    val Gold = Color(0xFFFFE59B)
    val Badge = Color(0xFFFF5FA8)
    val Panel = Color(0xFF0D0E16)
    val Iridescent = listOf(Cyan, Violet, Pink)
    val iridescentBrush get() = Brush.linearGradient(Iridescent)
    val edgeBrush = Brush.linearGradient(listOf(Color.White.copy(alpha = .30f), Color.White.copy(alpha = .08f), Color.White.copy(alpha = .18f)))
    val Font = FontFamily.SansSerif
}

/** Frosted glass surface: faint fill and a light edge. */
fun Modifier.glass(shape: Shape, fill: Float = .06f, edge: Boolean = true): Modifier =
    clip(shape).background(Color.White.copy(alpha = fill), shape)
        .then(if (edge) Modifier.border(1.dp, Holo.edgeBrush, shape) else Modifier)

/** A soft colored halo behind an element. */
fun Modifier.halo(color: Color, radius: Dp = 28.dp): Modifier = drawBehind {
    val r = size.minDimension / 2f + radius.toPx()
    drawCircle(Brush.radialGradient(listOf(color, Color.Transparent), center = center, radius = r), radius = r, center = center)
}

@Composable
fun IridescentText(text: String, size: TextUnit, modifier: Modifier = Modifier, weight: FontWeight = FontWeight.ExtraLight,
    letterSpacing: TextUnit = (-1.5).sp) {
    Text(text, modifier, maxLines = 1, softWrap = false, style = TextStyle(brush = Brush.linearGradient(Holo.Iridescent),
        fontSize = size, fontWeight = weight, fontFamily = Holo.Font, letterSpacing = letterSpacing, lineHeight = size))
}

/** Sensor tilt, -1..1 on each axis, used to drift the halos (parallax). */
@Composable
fun rememberTilt(enabled: Boolean): Offset {
    val context = LocalContext.current
    var tilt by remember { mutableStateOf(Offset.Zero) }
    DisposableEffect(enabled) {
        val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = manager.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: manager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = (-event.values[0] / 9.81f).coerceIn(-1f, 1f)
                val y = ((event.values[1] - 6.5f) / 9.81f).coerceIn(-1f, 1f)
                if ((Offset(x, y) - tilt).getDistance() > .02f) tilt = Offset(x, y)
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        if (enabled && sensor != null) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        else tilt = Offset.Zero
        onDispose { manager.unregisterListener(listener) }
    }
    val smooth by animateOffsetAsState(tilt, spring(stiffness = 40f), label = "tilt")
    return smooth
}

/** The black holographic backdrop with three drifting color halos. Over the phone wallpaper it only darkens. */
@Composable
fun HoloBackground(phoneWallpaper: Boolean, parallax: Boolean, modifier: Modifier = Modifier.fillMaxSize()) {
    val tilt = rememberTilt(parallax)
    Canvas(modifier) {
        if (phoneWallpaper) drawRect(Color.Black.copy(alpha = .38f)) else drawRect(Holo.Base)
        val w = size.width; val h = size.height
        val shift = Offset(tilt.x * w * .08f, tilt.y * h * .05f)
        val strength = if (phoneWallpaper) .55f else 1f
        fun glow(center: Offset, radius: Float, color: Color, alpha: Float) =
            drawCircle(Brush.radialGradient(listOf(color.copy(alpha = alpha * strength), Color.Transparent), center, radius), radius, center)
        val r = maxOf(w, h)
        glow(Offset(w * .05f, h * .06f) + shift, r * .42f, Color(0xFF6EBEFF), .30f)
        glow(Offset(w * 1.0f, h * .48f) - shift * 1.4f, r * .46f, Color(0xFFC882FF), .24f)
        glow(Offset(w * .18f, h * 1.02f) + shift * .7f, r * .40f, Color(0xFFFF8CC8), .18f)
    }
}

/** True: real app icons sit in glass frames. False: just the icon. */
val LocalBubbles = staticCompositionLocalOf { true }
/** The icon shape chosen in the settings (see [ICON_SHAPES]). */
val LocalIconShape = staticCompositionLocalOf { "circle" }
val LocalBadges = staticCompositionLocalOf<Map<String, Int>> { emptyMap() }

/** Icon shapes offered in the settings: key to display name. */
val ICON_SHAPES = listOf("circle" to "Circle", "squircle" to "Squircle", "rounded" to "Rounded", "square" to "Square",
    "teardrop" to "Teardrop", "leaf" to "Leaf", "original" to "Original")

fun iconShape(name: String): Shape = when (name) {
    "squircle" -> RoundedCornerShape(36)
    "rounded" -> RoundedCornerShape(22)
    "square" -> RoundedCornerShape(6)
    "teardrop" -> RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomEndPercent = 12, bottomStartPercent = 50)
    "leaf" -> RoundedCornerShape(topStartPercent = 50, topEndPercent = 8, bottomEndPercent = 50, bottomStartPercent = 8)
    "original" -> RoundedCornerShape(0)
    else -> CircleShape
}

@Composable
fun AppIcon(app: AppInfo, size: Dp, modifier: Modifier = Modifier, showBadge: Boolean = true) {
    val bubbles = LocalBubbles.current
    val shapeName = LocalIconShape.current
    val shape = iconShape(shapeName)
    val count = if (showBadge) LocalBadges.current[app.packageName] ?: 0 else 0
    Box(modifier.size(size)) {
        if (bubbles) {
            Box(Modifier.fillMaxSize().halo(Holo.Violet.copy(alpha = .16f), size * .2f)
                .glass(if (shapeName == "original") RoundedCornerShape(26) else shape, .06f), contentAlignment = Alignment.Center) {
                Image(app.icon, null, Modifier.size(size * .78f).then(if (shapeName == "original") Modifier else Modifier.clip(shape)))
            }
        } else Image(app.icon, null, Modifier.fillMaxSize().then(if (shapeName == "original") Modifier else Modifier.clip(shape)))
        if (count > 0) BadgeDot(count, Modifier.align(Alignment.TopEnd).offset(x = 4.dp, y = (-4).dp))
    }
}

@Composable
fun BadgeDot(count: Int, modifier: Modifier = Modifier) {
    Box(modifier.halo(Holo.Badge.copy(alpha = .55f), 6.dp).defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
        .background(Holo.Badge, CircleShape).padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
        Text(if (count > 99) "99+" else count.toString(), color = Color(0xFF1A0512), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
