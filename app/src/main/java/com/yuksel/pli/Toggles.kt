package com.yuksel.pli

import android.Manifest
import android.app.NotificationManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.DoNotDisturbOn
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.Nfc
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

private fun start(context: Context, intent: Intent) = runCatching {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true
}.getOrDefault(false)

private fun torchCamera(context: Context): String? = runCatching {
    val cm = context.getSystemService(CameraManager::class.java)
    cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
}.getOrNull()

/**
 * "Quick toggles" card: quick switches. Android lets an app switch the torch, the ringer, Do not disturb
 * and auto-rotation itself; for Wi-Fi, mobile data and NFC it can only open the system's quick panel.
 */
@Composable
fun TogglesCard(onPermission: (String) -> Unit) {
    val context = LocalContext.current
    val audio = remember { context.getSystemService(AudioManager::class.java) }
    val notifications = remember { context.getSystemService(NotificationManager::class.java) }
    val cameras = remember { context.getSystemService(CameraManager::class.java) }
    val torchId = remember { torchCamera(context) }
    var tick by remember { mutableIntStateOf(0) }
    var torch by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val callback = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) { if (cameraId == torchId) torch = enabled }
        }
        runCatching { cameras.registerTorchCallback(callback, null) }
        onDispose { runCatching { cameras.unregisterTorchCallback(callback) } }
    }
    LaunchedEffect(Unit) { while (true) { delay(1500); tick++ } }
    @Suppress("UNUSED_EXPRESSION") tick
    val wifi = runCatching { context.getSystemService(WifiManager::class.java).isWifiEnabled }.getOrDefault(false)
    val bluetooth = runCatching { context.getSystemService(BluetoothManager::class.java).adapter?.isEnabled == true }.getOrDefault(false)
    val ringer = audio.ringerMode
    val dnd = notifications.currentInterruptionFilter > NotificationManager.INTERRUPTION_FILTER_ALL
    val rotation = Settings.System.getInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, 0) == 1
    fun needPolicy(): Boolean {
        if (notifications.isNotificationPolicyAccessGranted) return false
        start(context, Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)); return true
    }
    val items = listOf(
        Toggle("Wi-Fi", Icons.Rounded.Wifi, wifi) { start(context, Intent(Settings.Panel.ACTION_WIFI)) },
        Toggle("Data", Icons.Rounded.SignalCellularAlt, null) { start(context, Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)) },
        Toggle("Bluetooth", Icons.Rounded.Bluetooth, bluetooth) {
            if (bluetooth) start(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            else if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) onPermission(Manifest.permission.BLUETOOTH_CONNECT)
            else if (!start(context, Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))) start(context, Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        },
        Toggle("Flashlight", Icons.Rounded.FlashlightOn, torch) {
            torchId?.let { id -> runCatching { cameras.setTorchMode(id, !torch) } }
        },
        Toggle(when (ringer) { AudioManager.RINGER_MODE_VIBRATE -> "Vibrate"; AudioManager.RINGER_MODE_SILENT -> "Silent"; else -> "Ring" },
            when (ringer) { AudioManager.RINGER_MODE_VIBRATE -> Icons.Rounded.Vibration; AudioManager.RINGER_MODE_SILENT -> Icons.Rounded.NotificationsOff
                else -> Icons.Rounded.NotificationsActive }, ringer != AudioManager.RINGER_MODE_NORMAL) {
            val next = when (ringer) { AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
                AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT; else -> AudioManager.RINGER_MODE_NORMAL }
            if (runCatching { audio.ringerMode = next }.isFailure && !needPolicy()) Unit
            tick++
        },
        Toggle("Do not disturb", Icons.Rounded.DoNotDisturbOn, dnd) {
            if (!needPolicy()) notifications.setInterruptionFilter(if (dnd) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY)
            tick++
        },
        Toggle("Rotate", Icons.Rounded.ScreenRotation, rotation) {
            if (Settings.System.canWrite(context)) Settings.System.putInt(context.contentResolver, Settings.System.ACCELEROMETER_ROTATION, if (rotation) 0 else 1)
            else start(context, Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + context.packageName)))
            tick++
        },
        Toggle("NFC", Icons.Rounded.Nfc, null) { start(context, Intent(Settings.Panel.ACTION_NFC)) },
    )
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(28.dp), .05f).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("QUICK TOGGLES", color = Holo.Muted, fontSize = 11.sp, letterSpacing = 2.5.sp)
        items.chunked(4).forEach { line ->
            Row(Modifier.fillMaxWidth()) {
                line.forEach { t -> Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { ToggleButton(t) } }
                repeat(4 - line.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private class Toggle(val label: String, val icon: ImageVector, val on: Boolean?, val action: () -> Unit)

@Composable
private fun ToggleButton(t: Toggle) {
    val on = t.on == true
    Column(Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = t.action)
        .semantics { contentDescription = t.label; if (t.on != null) stateDescription = if (on) "on" else "off" }
        .padding(4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(52.dp).then(
            if (on) Modifier.halo(Holo.Violet.copy(alpha = .35f), 8.dp).background(Brush.linearGradient(Holo.Iridescent), CircleShape)
            else Modifier.glass(CircleShape, .07f)), contentAlignment = Alignment.Center) {
            Icon(t.icon, null, tint = if (on) Color(0xFF120A22) else Holo.Ink, modifier = Modifier.size(22.dp))
        }
        Text(t.label, color = Holo.Soft, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
    }
}
