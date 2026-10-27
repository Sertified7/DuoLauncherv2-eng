package com.jake.duolauncher

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ---------- Notification badges ----------

/** Counts visible notifications per package, iPhone-style, while notification access is granted. */
class BadgeService : NotificationListenerService() {
    override fun onListenerConnected() = recount()
    override fun onListenerDisconnected() { counts.value = emptyMap() }
    override fun onNotificationPosted(sbn: StatusBarNotification?) = recount()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = recount()

    private fun recount() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        counts.value = active.asSequence()
            .filter { it.isClearable && !it.isOngoing && it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
            .groupingBy { it.packageName }.eachCount()
    }

    companion object {
        private val counts = MutableStateFlow<Map<String, Int>>(emptyMap())
        val badges: StateFlow<Map<String, Int>> = counts.asStateFlow()

        fun hasAccess(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            val me = ComponentName(context, BadgeService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}

/** Notification counts by package name, provided at the top of the Home screen. */
internal val LocalBadges = staticCompositionLocalOf<Map<String, Int>> { emptyMap() }

/** The red count bubble on an icon's top-right corner. */
@Composable
internal fun BadgeDot(count: Int, modifier: Modifier = Modifier) {
    if (count <= 0) return
    Box(modifier.defaultMinSize(minWidth = 20.dp, minHeight = 20.dp).shadow(2.dp, CircleShape)
        .background(Color(0xFFFF3B30), CircleShape).padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center) {
        Text(if (count > 99) "99+" else count.toString(), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

// ---------- App shortcuts (long-press quick actions) ----------

internal data class AppShortcut(val info: ShortcutInfo, val label: String, val icon: Bitmap?)

/** Static, dynamic and pinned shortcuts an app offers. Only available while Duo is the default Home app. */
internal fun loadAppShortcuts(context: Context, app: AppEntry): List<AppShortcut> = runCatching {
    val launcherApps = context.getSystemService(LauncherApps::class.java)
    if (!launcherApps.hasShortcutHostPermission()) return emptyList()
    val query = LauncherApps.ShortcutQuery().setPackage(app.packageName).setQueryFlags(
        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
            LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
    val density = context.resources.displayMetrics.densityDpi
    (launcherApps.getShortcuts(query, app.user) ?: emptyList())
        .filter { it.isEnabled && (it.activity == null || it.activity == app.component) }
        .sortedWith(compareBy<ShortcutInfo>({ !it.isDeclaredInManifest }, { it.rank }))
        .take(4)
        .map { info ->
            val icon = runCatching { launcherApps.getShortcutIconDrawable(info, density)?.toBitmap(96, 96) }.getOrNull()
            AppShortcut(info, (info.shortLabel ?: info.longLabel ?: "").toString(), icon)
        }.filter { it.label.isNotBlank() }
}.getOrDefault(emptyList())

internal fun startAppShortcut(context: Context, shortcut: AppShortcut) = runCatching {
    context.getSystemService(LauncherApps::class.java).startShortcut(shortcut.info, null, null)
}.isSuccess
