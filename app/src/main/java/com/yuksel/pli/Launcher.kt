package com.yuksel.pli

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.provider.Telephony
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import java.time.LocalDateTime
import java.time.ZoneId

/** Holds everything the home screen shows, and saves each change. One instance per activity. */
class Launcher(private val context: Context) {
    val repository = AppRepository(context)
    val store = Store(context)

    var apps by mutableStateOf<List<AppInfo>>(emptyList()); private set
    var byId by mutableStateOf<Map<String, AppInfo>>(emptyMap()); private set
    var layout by mutableStateOf(HomeLayout()); private set
    var settings by mutableStateOf(PliSettings()); private set
    var loaded by mutableStateOf(false); private set
    /** Bumped when the icon look changes so the UI reloads the apps. */
    var iconReloads by mutableStateOf(0); private set
    /** The last app opened from Pli, and on which screen, for "Continuer" after folding or unfolding. */
    var lastLaunch by mutableStateOf<Triple<String, Screen, Long>?>(null); private set

    private fun iconStyle() = AppRepository.IconStyle(settings.iconPack, settings.tintedIcons)

    /** Apps the user did not hide, sorted by name. */
    val visibleApps get() = apps.filter { it.id !in layout.hidden }

    suspend fun start() {
        settings = store.loadSettings()
        val firstRun = !store.hasLayout()
        layout = store.loadLayout()
        reloadApps()
        if (firstRun) {
            layout = defaultLayout()
            store.saveLayout(layout)
        }
        loaded = true
    }

    suspend fun reloadApps(removedPackage: String? = null, addedPackage: String? = null) {
        val list = repository.load(iconStyle())
        apps = list
        byId = list.associateBy { it.id }
        if (removedPackage != null) {
            val keep = (layout.cover + layout.inner + layout.dock + layout.dockExtra + layout.folders.values.flatMap { it.apps })
                .filterNot { it.startsWith("$removedPackage/") && it !in byId }.toSet()
            update { LayoutEdits.clean(it, keep + byId.keys) }
        }
        if (addedPackage != null && settings.addNewApps) {
            val added = list.filter { it.packageName == addedPackage && it.id !in layout.hidden }
            if (added.isNotEmpty()) update { l ->
                added.fold(l) { acc, app -> Screen.entries.fold(acc) { a, s -> LayoutEdits.addToHome(a, s, app.id) } }
            }
        }
    }

    fun update(transform: (HomeLayout) -> HomeLayout) {
        val next = transform(layout)
        if (next != layout) { layout = next; store.saveLayout(next) }
    }

    val dockSizes get() = DockSizes(settings.dockSize, settings.innerDockSize)

    fun updateSettings(transform: (PliSettings) -> PliSettings) {
        val next = transform(settings)
        val old = settings
        val resize = next.dockSize != settings.dockSize || next.innerDockSize != settings.innerDockSize
        settings = next; store.saveSettings(next)
        if (resize) update { LayoutEdits.resizeDock(it, dockSizes) }
        if (next.iconPack != old.iconPack || next.tintedIcons != old.tintedIcons) iconReloads++
    }

    /** Home entries that can be shown right now (known, not hidden; folders with visible apps). */
    fun visibleHome(screen: Screen): List<String> = layout.home(screen).filter { id ->
        if (HomeLayout.isFolder(id)) layout.folders[id]?.apps?.any { it in byId && it !in layout.hidden } == true
        else id in byId && id !in layout.hidden
    }

    fun visibleDock(screen: Screen): List<String> = layout.dockOf(screen).filter { it in byId && it !in layout.hidden }

    fun folderApps(folderId: String): List<AppInfo> =
        layout.folders[folderId]?.apps?.mapNotNull { byId[it] }?.filter { it.id !in layout.hidden } ?: emptyList()

    fun hide(app: AppInfo) = update { l ->
        val folders = l.folders.mapValues { (_, f) -> f.copy(apps = f.apps - app.id) }
        LayoutEdits.clean(l.copy(cover = l.cover - app.id, inner = l.inner - app.id, dock = l.dock - app.id, dockExtra = l.dockExtra - app.id,
            folders = folders, hidden = l.hidden + app.id), byId.keys)
    }

    fun unhide(id: String) = update { it.copy(hidden = it.hidden - id) }

    fun recordLaunch(app: AppInfo, screen: Screen) {
        UsageLog.record(context, app.id)
        lastLaunch = Triple(app.id, screen, System.currentTimeMillis())
    }

    fun forgetLastLaunch() { lastLaunch = null }

    /** Reloads everything after a backup was imported. */
    suspend fun reloadAll() {
        settings = store.loadSettings()
        layout = store.loadLayout()
        reloadApps()
    }

    // ---------- First run ----------

    private fun resolvePackage(intent: Intent): String? = runCatching {
        context.packageManager.resolveActivity(intent, 0)?.activityInfo?.packageName
    }.getOrNull()?.takeIf { it != "android" && !it.contains("resolver") }

    private fun defaultLayout(): HomeLayout {
        val byPackage = apps.groupBy { it.packageName }
        fun first(pkg: String?) = pkg?.let { byPackage[it]?.firstOrNull { app -> !app.work } }?.id
        val dockPackages = listOfNotNull(
            resolvePackage(Intent(Intent.ACTION_DIAL)),
            runCatching { Telephony.Sms.getDefaultSmsPackage(context) }.getOrNull(),
            resolvePackage(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.example.com")).addCategory(Intent.CATEGORY_BROWSABLE)),
            resolvePackage(Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)),
        )
        val dock = dockPackages.mapNotNull(::first).distinct().toMutableList()
        val preferred = listOf(
            "com.google.android.gm", "com.samsung.android.calendar", "com.google.android.calendar",
            "com.google.android.apps.photos", "com.sec.android.gallery3d", "com.google.android.apps.maps",
            "com.whatsapp", "com.spotify.music", "com.google.android.youtube", "com.instagram.android",
            "com.samsung.android.app.notes", "com.google.android.keep", "com.sec.android.app.clockpackage",
            "com.google.android.deskclock", "com.sec.android.app.myfiles", "com.google.android.apps.docs",
            "com.android.vending", "com.android.settings", "com.snapchat.android", "com.netflix.mediaclient",
            "com.samsung.android.app.watchmanager", "com.google.android.apps.messaging", "com.sec.android.app.samsungapps",
        )
        if (dock.size < 5) preferred.mapNotNull(::first).firstOrNull { it !in dock }?.let(dock::add)
        val home = (preferred.mapNotNull(::first) + apps.filter { !it.work }.map { it.id })
            .distinct().filter { it !in dock }.take(20)
        return HomeLayout(cover = home, inner = home, dock = dock.take(settings.dockSize))
    }
}

/** Remembers recent launches (on this device only) to suggest the apps usually opened at this time of day. */
object UsageLog {
    private const val PREFS = "usage"
    private const val MAX = 400

    fun record(context: Context, appId: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val array = runCatching { JSONArray(prefs.getString("launches", "[]")) }.getOrDefault(JSONArray())
        array.put(JSONArray().put(appId).put(System.currentTimeMillis()))
        val trimmed = JSONArray().also { out -> for (i in maxOf(0, array.length() - MAX) until array.length()) out.put(array.get(i)) }
        prefs.edit().putString("launches", trimmed.toString()).apply()
    }

    fun suggestions(context: Context, apps: Map<String, AppInfo>, count: Int): List<AppInfo> {
        val array = runCatching {
            JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("launches", "[]"))
        }.getOrDefault(JSONArray())
        val now = System.currentTimeMillis()
        val hour = LocalDateTime.now().hour
        val scores = HashMap<String, Double>()
        for (i in 0 until array.length()) {
            val entry = array.optJSONArray(i) ?: continue
            val id = entry.optString(0); val time = entry.optLong(1)
            val launchHour = java.time.Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).hour
            val distance = minOf(kotlin.math.abs(launchHour - hour), 24 - kotlin.math.abs(launchHour - hour))
            val timeOfDay = when (distance) { 0 -> 3.0; 1 -> 2.0; 2 -> 1.0; else -> .25 }
            val ageDays = (now - time) / 86_400_000.0
            scores[id] = (scores[id] ?: 0.0) + timeOfDay / (1.0 + ageDays / 7.0)
        }
        return scores.entries.sortedByDescending { it.value }.mapNotNull { apps[it.key] }.take(count)
    }
}
