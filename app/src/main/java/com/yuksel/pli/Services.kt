package com.yuksel.pli

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ---------- Notification badges ----------

/** Counts notifications per app while notification access is granted; also unlocks the media card. */
class BadgeService : NotificationListenerService() {
    override fun onListenerConnected() { recount(); MediaWatcher.refresh(this) }
    override fun onListenerDisconnected() { counts.value = emptyMap() }
    override fun onNotificationPosted(sbn: StatusBarNotification?) = recount()
    override fun onNotificationRemoved(sbn: StatusBarNotification?) = recount()

    private fun recount() {
        val active = runCatching { activeNotifications }.getOrNull() ?: return
        val shown = active.filter { it.isClearable && !it.isOngoing && it.notification.flags and Notification.FLAG_GROUP_SUMMARY == 0 }
        counts.value = shown.groupingBy { it.packageName }.eachCount()
        previewMap.value = shown.groupBy { it.packageName }.mapValues { (_, list) ->
            list.sortedByDescending { it.postTime }.take(3).mapNotNull { sbn ->
                val extras = sbn.notification.extras
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
                val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
                if (title.isBlank() && text.isBlank()) null
                else NotificationPreview(sbn.key, title, text, sbn.postTime, sbn.notification.contentIntent)
            }
        }.filterValues { it.isNotEmpty() }
    }

    companion object {
        private val counts = MutableStateFlow<Map<String, Int>>(emptyMap())
        val badges: StateFlow<Map<String, Int>> = counts.asStateFlow()
        private val previewMap = MutableStateFlow<Map<String, List<NotificationPreview>>>(emptyMap())
        /** The latest notifications of each app, for the long-press preview. */
        val previews: StateFlow<Map<String, List<NotificationPreview>>> = previewMap.asStateFlow()

        /** Opens what the notification points to. */
        fun open(preview: NotificationPreview): Boolean = runCatching {
            val intent = preview.intent ?: return false
            val options = android.app.ActivityOptions.makeBasic()
            if (android.os.Build.VERSION.SDK_INT >= 34)
                options.setPendingIntentBackgroundActivityStartMode(android.app.ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
            intent.send(null, 0, null, null, null, null, options.toBundle())
            true
        }.getOrDefault(false)

        fun hasAccess(context: Context): Boolean {
            val enabled = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners") ?: return false
            val me = ComponentName(context, BadgeService::class.java)
            return enabled.split(':').any { ComponentName.unflattenFromString(it) == me }
        }
    }
}

data class NotificationPreview(val key: String, val title: String, val text: String, val time: Long, val intent: android.app.PendingIntent?)

// ---------- Now playing ----------

data class NowPlaying(val title: String, val artist: String, val art: ImageBitmap?, val playing: Boolean,
    val packageName: String, val duration: Long, val position: Long)

/** Follows the active media session (needs notification access) for the "Now playing" card. */
object MediaWatcher {
    private val state = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = state.asStateFlow()
    private var controller: MediaController? = null
    private var manager: MediaSessionManager? = null
    private val handler = Handler(Looper.getMainLooper())

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(playbackState: PlaybackState?) = publish()
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onSessionDestroyed() { controller = null; state.value = null }
    }

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list -> attach(list?.firstOrNull()) }

    fun refresh(context: Context) {
        if (!BadgeService.hasAccess(context)) return
        val m = manager ?: context.getSystemService(MediaSessionManager::class.java).also { manager = it } ?: return
        val component = ComponentName(context, BadgeService::class.java)
        runCatching {
            m.removeOnActiveSessionsChangedListener(sessionsListener)
            m.addOnActiveSessionsChangedListener(sessionsListener, component, handler)
            attach(m.getActiveSessions(component).firstOrNull())
        }
    }

    private fun attach(next: MediaController?) {
        if (next?.sessionToken == controller?.sessionToken) { publish(); return }
        controller?.unregisterCallback(callback)
        controller = next
        next?.registerCallback(callback, handler)
        publish()
    }

    private fun publish() {
        val c = controller ?: run { state.value = null; return }
        val meta = c.metadata
        val title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE)
        if (title.isNullOrBlank()) { state.value = null; return }
        val art = (meta.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART) ?: meta.getBitmap(MediaMetadata.METADATA_KEY_ART))
            ?.let { runCatching { Bitmap.createScaledBitmap(it, 160, 160, true).asImageBitmap() }.getOrNull() }
        val playback = c.playbackState
        state.value = NowPlaying(title, meta.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: "", art,
            playback?.state == PlaybackState.STATE_PLAYING, c.packageName,
            meta.getLong(MediaMetadata.METADATA_KEY_DURATION), playback?.position ?: 0L)
    }

    fun toggle() { val c = controller ?: return; if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause() else c.transportControls.play() }
    fun next() { controller?.transportControls?.skipToNext() }
    fun previous() { controller?.transportControls?.skipToPrevious() }
}

// ---------- App shortcuts (long-press actions) ----------

data class AppShortcut(val info: ShortcutInfo, val label: String, val icon: ImageBitmap?)

/** Static, dynamic, and pinned shortcuts an app offers. Only available while Pli is the default home app. */
fun loadAppShortcuts(context: Context, app: AppInfo): List<AppShortcut> = runCatching {
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
            val icon = runCatching { launcherApps.getShortcutIconDrawable(info, density)?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull()
            AppShortcut(info, (info.shortLabel ?: info.longLabel ?: "").toString(), icon)
        }.filter { it.label.isNotBlank() }
}.getOrDefault(emptyList())

fun startAppShortcut(context: Context, shortcut: AppShortcut) = runCatching {
    context.getSystemService(LauncherApps::class.java).startShortcut(shortcut.info, null, null)
}.isSuccess
