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
import java.util.Locale

/** Holds everything the home screen shows, and saves each change. One instance per activity. */
class Launcher(private val context: Context) {
    val repository = AppRepository(context)
    val store = Store(context)

    var apps by mutableStateOf<List<AppInfo>>(emptyList()); private set
    var byId by mutableStateOf<Map<String, AppInfo>>(emptyMap()); private set
    var layout by mutableStateOf(HomeLayout()); private set
    var settings by mutableStateOf(PliSettings()); private set
    var loaded by mutableStateOf(false); private set
    /** True while the battery saver is active (battery low and not charging). */
    var lowPower by mutableStateOf(false)
    /** Bumped when the icon look changes so the UI reloads the apps. */
    var iconReloads by mutableStateOf(0); private set
    /** The last app opened from Pli, and on which screen, for "Continue" after folding or unfolding. */
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

    private var rawApps: List<AppInfo> = emptyList()
    private val customIcons = HashMap<String, Pair<String, androidx.compose.ui.graphics.ImageBitmap>>()

    fun originalLabel(id: String): String? = rawApps.firstOrNull { it.id == id }?.label

    /** Applies the user's names and icons to the system list. */
    private fun publishApps() {
        val names = layout.labels
        val collator = java.text.Collator.getInstance(Locale.US).apply { strength = java.text.Collator.PRIMARY }
        val list = rawApps.map { app ->
            val label = names[app.id]
            val icon = customIcons[app.id]?.takeIf { it.first == layout.icons[app.id] }?.second
            if (label == null && icon == null) app else app.copy(label = label ?: app.label, icon = icon ?: app.icon)
        }.sortedWith { a, b -> collator.compare(a.label, b.label) }
        apps = list
        byId = list.associateBy { it.id }
    }

    /** Loads custom icons that are not loaded yet (pack drawables or saved images). */
    private suspend fun loadCustomIcons() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val size = (context.resources.displayMetrics.density * 72).toInt().coerceIn(96, 256)
        layout.icons.forEach { (id, spec) ->
            if (customIcons[id]?.first == spec) return@forEach
            CustomIcons.load(context, spec, size)?.let { customIcons[id] = spec to it }
        }
    }

    suspend fun reloadApps(removedPackage: String? = null, addedPackage: String? = null) {
        val list = repository.load(iconStyle())
        rawApps = list
        loadCustomIcons()
        publishApps()
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

    /** Keeps big folders' covered cells in step with the folders. */
    private fun withSpans(l: HomeLayout): HomeLayout = Screen.entries.fold(l) { acc, screen ->
        val grid = acc.grid(screen) ?: return@fold acc
        val list = LayoutEdits.spans(acc.home(screen), grid, acc.folders)
        if (list == acc.home(screen)) acc else acc.withHome(screen, list)
    }

    fun update(transform: (HomeLayout) -> HomeLayout) {
        val next = withSpans(transform(layout))
        if (next != layout) {
            val namesChanged = next.labels != layout.labels || next.icons != layout.icons
            layout = next; store.saveLayout(next)
            if (namesChanged) { publishApps(); if (next.icons.any { (id, spec) -> customIcons[id]?.first != spec }) iconReloads++ }
        }
    }

    /** The last change the user can take back, with a short description. */
    var undo by mutableStateOf<Pair<String, HomeLayout>?>(null); private set
    var undoStamp by mutableStateOf(0L); private set

    /** A user edit that offers "Undo" for a few seconds. */
    fun edit(label: String, transform: (HomeLayout) -> HomeLayout) {
        val before = layout
        update(transform)
        if (layout != before) { undo = label to before; undoStamp = System.currentTimeMillis() }
    }

    fun undoLast() {
        val (_, before) = undo ?: return
        undo = null
        update { before }
    }

    fun dismissUndo() { undo = null }

    val dockSizes get() = DockSizes(settings.dockSize, settings.innerDockSize)

    fun updateSettings(transform: (PliSettings) -> PliSettings) {
        val next = transform(settings)
        val old = settings
        if (next.coverColumns != old.coverColumns || next.coverRows != old.coverRows) regridPending += Screen.COVER
        if (next.innerColumns != old.innerColumns || next.innerRows != old.innerRows) regridPending += Screen.INNER
        val resize = next.dockSize != settings.dockSize || next.innerDockSize != settings.innerDockSize
        settings = next; store.saveSettings(next)
        if (resize) update { LayoutEdits.resizeDock(it, dockSizes) }
        if (next.iconPack != old.iconPack || next.tintedIcons != old.tintedIcons) iconReloads++
    }

    /** The home slots of a screen; slots whose app can't be shown (hidden, or work profile paused) read as empty. */
    fun visibleHome(screen: Screen): List<String> = layout.home(screen).map { id ->
        if (id == HomeLayout.COVERED) return@map id
        val shown = when {
            id == HomeLayout.EMPTY -> false
            HomeLayout.isFolder(id) -> layout.folders[id]?.apps?.any { it in byId && it !in layout.hidden } == true
            else -> id in byId && id !in layout.hidden
        }
        if (shown) id else HomeLayout.EMPTY
    }

    private val regridPending = HashSet<Screen>()

    /**
     * Remembers the grid a screen shows. Icons keep their row and column only when the user changed the grid
     * in the settings; a size change from the screen itself (rotation, keyboard) never moves them.
     */
    fun useGrid(screen: Screen, grid: Grid) {
        if (layout.grid(screen) == grid) { regridPending -= screen; return }
        val move = screen in regridPending
        regridPending -= screen
        update { l ->
            if (move) LayoutEdits.withGrid(l, screen, grid)
            else if (screen == Screen.COVER) l.copy(coverGrid = grid) else l.copy(innerGrid = grid)
        }
    }

    fun visibleDock(screen: Screen): List<String> = layout.dockOf(screen).filter { it in byId && it !in layout.hidden }

    fun folderApps(folderId: String): List<AppInfo> =
        layout.folders[folderId]?.apps?.mapNotNull { byId[it] }?.filter { it.id !in layout.hidden } ?: emptyList()

    fun hide(app: AppInfo) = edit("${app.label} hidden") { l ->
        val folders = l.folders.mapValues { (_, f) -> f.copy(apps = f.apps - app.id) }
        LayoutEdits.clean(l.copy(dock = l.dock - app.id, dockExtra = l.dockExtra - app.id, folders = folders, hidden = l.hidden + app.id)
            .withHome(Screen.COVER, LayoutEdits.blank(l.cover, setOf(app.id))).withHome(Screen.INNER, LayoutEdits.blank(l.inner, setOf(app.id))),
            byId.keys + l.cover + l.inner + l.dock + l.dockExtra + l.folders.values.flatMap { it.apps } - app.id)
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

    /** The last apps opened, newest first, without duplicates. */
    fun recent(context: Context, apps: Map<String, AppInfo>, count: Int, exclude: Set<String>): List<AppInfo> {
        if (count <= 0) return emptyList()
        val array = runCatching {
            JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("launches", "[]"))
        }.getOrDefault(JSONArray())
        val out = LinkedHashSet<String>()
        for (i in array.length() - 1 downTo 0) {
            val id = array.optJSONArray(i)?.optString(0) ?: continue
            if (id in exclude || id !in apps) continue
            out += id
            if (out.size >= count) break
        }
        return out.mapNotNull { apps[it] }
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
