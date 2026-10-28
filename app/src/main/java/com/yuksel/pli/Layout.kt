package com.yuksel.pli

import org.json.JSONArray
import org.json.JSONObject

/** The cover screen and the inner screen each keep their own home pages; the dock is shared. */
enum class Screen { COVER, INNER }

data class Folder(val id: String, val title: String, val apps: List<String>)

/** A card in the "Aujourd'hui" column: a built-in widget or an Android widget. */
data class TodayItem(val key: String, val kind: String, val widgetId: Int = -1, val height: Int = 0) {
    val isAndroid get() = kind == KIND_ANDROID

    companion object {
        const val KIND_ANDROID = "android"
        const val WEATHER = "weather"
        const val AGENDA = "agenda"
        const val BATTERY = "battery"
        const val NOTE = "note"
        const val MEDIA = "media"
        val BUILT_IN = listOf(WEATHER, AGENDA, MEDIA, BATTERY, NOTE)
    }
}

/**
 * Home items flow in order (like iOS): pages are cut from the list by how many icons fit,
 * so the same list works on any screen size and orientation.
 * An entry is an app id, or a folder id starting with [FOLDER_PREFIX].
 */
data class HomeLayout(
    val cover: List<String> = emptyList(),
    val inner: List<String> = emptyList(),
    val dock: List<String> = emptyList(),
    /** Extra dock places shown only on the inner screen, after the shared ones. */
    val dockExtra: List<String> = emptyList(),
    val folders: Map<String, Folder> = emptyMap(),
    val today: List<TodayItem> = TodayItem.BUILT_IN.map { TodayItem(it, it) },
    val hidden: Set<String> = emptySet(),
) {
    fun home(screen: Screen) = if (screen == Screen.COVER) cover else inner
    fun withHome(screen: Screen, items: List<String>) = if (screen == Screen.COVER) copy(cover = items) else copy(inner = items)

    /** Every app id placed on this screen, including inside folders and the dock. */
    fun placedApps(screen: Screen): Set<String> = buildSet {
        home(screen).forEach { if (isFolder(it)) folders[it]?.apps?.let(::addAll) else add(it) }
        addAll(dock)
        if (screen == Screen.INNER) addAll(dockExtra)
    }

    fun dockOf(screen: Screen) = if (screen == Screen.INNER) dock + dockExtra else dock

    fun toJson(): JSONObject = JSONObject().apply {
        put("cover", JSONArray(cover)); put("inner", JSONArray(inner)); put("dock", JSONArray(dock)); put("dockExtra", JSONArray(dockExtra))
        put("folders", JSONArray().apply {
            folders.values.forEach { put(JSONObject().put("id", it.id).put("title", it.title).put("apps", JSONArray(it.apps))) }
        })
        put("today", JSONArray().apply {
            today.forEach { put(JSONObject().put("key", it.key).put("kind", it.kind).put("widgetId", it.widgetId).put("height", it.height)) }
        })
        put("hidden", JSONArray(hidden.toList()))
    }

    companion object {
        const val FOLDER_PREFIX = "folder:"
        fun isFolder(id: String) = id.startsWith(FOLDER_PREFIX)

        fun fromJson(json: JSONObject): HomeLayout {
            fun strings(name: String) = json.optJSONArray(name)?.let { a -> List(a.length()) { a.optString(it) } }?.filter { it.isNotEmpty() } ?: emptyList()
            val folders = json.optJSONArray("folders")?.let { a ->
                (0 until a.length()).mapNotNull { i -> a.optJSONObject(i) }.map { o ->
                    val apps = o.optJSONArray("apps")?.let { x -> List(x.length()) { x.optString(it) } } ?: emptyList()
                    Folder(o.optString("id"), o.optString("title", "Dossier"), apps)
                }.associateBy { it.id }
            } ?: emptyMap()
            val today = json.optJSONArray("today")?.let { a ->
                (0 until a.length()).mapNotNull { i -> a.optJSONObject(i) }.map { o ->
                    TodayItem(o.optString("key"), o.optString("kind"), o.optInt("widgetId", -1), o.optInt("height", 0))
                }
            } ?: HomeLayout().today
            return HomeLayout(strings("cover"), strings("inner"), strings("dock"), strings("dockExtra"), folders, today, strings("hidden").toSet())
        }
    }
}

// ---------- Pure layout edits (no Android types, easy to reason about) ----------

/** Where a dragged item lands. */
sealed interface DropSpot {
    /** Before the item at [index] of a screen's home list ([index] may be the list size: at the end). */
    data class Home(val screen: Screen, val index: Int) : DropSpot
    /** Onto the item at [index] (an app or a folder): makes or fills a folder. */
    data class Into(val screen: Screen, val index: Int) : DropSpot
    data class Dock(val screen: Screen, val index: Int) : DropSpot
}

/** Where a dragged item came from. */
sealed interface DragSource {
    data class Home(val screen: Screen, val id: String) : DragSource
    data class Dock(val id: String) : DragSource
    data class InFolder(val folderId: String, val id: String) : DragSource
    val itemId: String get() = when (this) { is Home -> id; is Dock -> id; is InFolder -> id }
}

/** How many dock places each screen shows (the inner screen can show more). */
data class DockSizes(val cover: Int, val inner: Int) {
    fun max(screen: Screen) = if (screen == Screen.INNER) maxOf(cover, inner) else cover
}

object LayoutEdits {
    fun removeFromHome(layout: HomeLayout, screen: Screen, id: String) =
        layout.withHome(screen, layout.home(screen) - id)

    fun addToHome(layout: HomeLayout, screen: Screen, id: String): HomeLayout =
        if (id in layout.placedApps(screen)) layout else layout.withHome(screen, layout.home(screen) + id)

    /** Puts [list] in the dock as seen from [screen]: on the inner screen the places past the shared ones are inner-only. */
    fun withDock(layout: HomeLayout, list: List<String>, screen: Screen, sizes: DockSizes): HomeLayout {
        val (dock, extra) = if (screen == Screen.INNER) list.take(sizes.cover) to list.drop(sizes.cover)
            else list to (layout.dockExtra - list.toSet())
        // An app sits either in the dock or on a home page, never both.
        var out = layout.copy(dock = dock, dockExtra = extra, cover = layout.cover - dock.toSet(),
            inner = layout.inner - dock.toSet() - extra.toSet())
        // Apps pushed out of the shared places stay reachable on the cover screen.
        layout.dock.filter { it in extra }.forEach { if (it !in out.placedApps(Screen.COVER)) out = out.copy(cover = out.cover + it) }
        return out
    }

    fun addToDock(layout: HomeLayout, id: String, screen: Screen, sizes: DockSizes): HomeLayout {
        val list = layout.dockOf(screen)
        if (id in list || list.size >= sizes.max(screen)) return layout
        return withDock(layout, list + id, screen, sizes)
    }

    fun removeFromDock(layout: HomeLayout, id: String, screen: Screen?): HomeLayout {
        val removed = layout.copy(dock = layout.dock - id, dockExtra = layout.dockExtra - id)
        return if (screen == null) removed else addToHome(removed, screen, id)
    }

    /** Applies new dock sizes: apps that no longer fit go back to the home pages. */
    fun resizeDock(layout: HomeLayout, sizes: DockSizes): HomeLayout {
        val all = layout.dock + layout.dockExtra
        val dock = all.take(sizes.cover)
        val extra = all.drop(sizes.cover).take((sizes.inner - sizes.cover).coerceAtLeast(0))
        val leftover = all - dock.toSet() - extra.toSet()
        var out = layout.copy(dock = dock, dockExtra = extra, cover = layout.cover - dock.toSet(), inner = layout.inner - dock.toSet() - extra.toSet())
        (extra + leftover).forEach { if (it !in out.placedApps(Screen.COVER)) out = out.copy(cover = out.cover + it) }
        leftover.forEach { if (it !in out.placedApps(Screen.INNER)) out = out.copy(inner = out.inner + it) }
        return out
    }

    /** Applies a finished drag. Returns the layout unchanged when the drop makes no sense. */
    fun drop(layout: HomeLayout, source: DragSource, spot: DropSpot, sizes: DockSizes): HomeLayout {
        val id = source.itemId
        // Take the item out of where it was, remembering the old index to fix up targets on the same list.
        var oldIndex = -1
        var base = when (source) {
            is DragSource.Home -> { oldIndex = layout.home(source.screen).indexOf(id); removeFromHome(layout, source.screen, id) }
            is DragSource.Dock -> {
                oldIndex = layout.dockOf((spot as? DropSpot.Dock)?.screen ?: Screen.INNER).indexOf(id)
                layout.copy(dock = layout.dock - id, dockExtra = layout.dockExtra - id)
            }
            is DragSource.InFolder -> takeOutOfFolder(layout, source.folderId, id)
        }
        fun shifted(screenOrDock: Any, index: Int): Int {
            val same = when {
                source is DragSource.Home && screenOrDock == source.screen -> true
                source is DragSource.Dock && screenOrDock == "dock" -> true
                else -> false
            }
            return if (same && oldIndex in 0 until index) index - 1 else index
        }
        when (spot) {
            is DropSpot.Home -> {
                val list = base.home(spot.screen)
                val at = shifted(spot.screen, spot.index).coerceIn(0, list.size)
                return base.withHome(spot.screen, list.toMutableList().apply { add(at, id) })
            }
            is DropSpot.Dock -> {
                if (HomeLayout.isFolder(id)) return layout
                val list = base.dockOf(spot.screen)
                if (list.size >= sizes.max(spot.screen)) return layout
                val at = shifted("dock", spot.index).coerceIn(0, list.size)
                return withDock(base, list.toMutableList().apply { add(at, id) }, spot.screen, sizes)
            }
            is DropSpot.Into -> {
                if (HomeLayout.isFolder(id)) return layout
                val targetId = layout.home(spot.screen).getOrNull(spot.index) ?: return layout
                if (targetId == id) return layout
                val list = base.home(spot.screen)
                val at = list.indexOf(targetId)
                if (at < 0) return layout
                return if (HomeLayout.isFolder(targetId)) {
                    val folder = base.folders[targetId] ?: return layout
                    base.copy(folders = base.folders + (targetId to folder.copy(apps = folder.apps + id)))
                } else {
                    val folderId = HomeLayout.FOLDER_PREFIX + System.nanoTime().toString(36)
                    base.withHome(spot.screen, list.toMutableList().apply { set(at, folderId) })
                        .let { it.copy(folders = it.folders + (folderId to Folder(folderId, "Dossier", listOf(targetId, id)))) }
                }
            }
        }
    }

    /** Removes [id] from a folder; a folder left with one app turns back into that app. */
    fun takeOutOfFolder(layout: HomeLayout, folderId: String, id: String): HomeLayout {
        val folder = layout.folders[folderId] ?: return layout
        val rest = folder.apps - id
        if (rest.size >= 2) return layout.copy(folders = layout.folders + (folderId to folder.copy(apps = rest)))
        fun swap(list: List<String>) = list.flatMap { if (it == folderId) rest else listOf(it) }
        return layout.copy(cover = swap(layout.cover), inner = swap(layout.inner), folders = layout.folders - folderId)
    }

    /** Moves an app out of its folder onto the home page right after the folder. */
    fun moveOutOfFolder(layout: HomeLayout, screen: Screen, folderId: String, id: String): HomeLayout {
        val index = layout.home(screen).indexOf(folderId)
        val taken = takeOutOfFolder(layout, folderId, id)
        val list = taken.home(screen)
        val at = if (index < 0) list.size else (list.indexOf(folderId).takeIf { it >= 0 }?.plus(1) ?: (index + 1)).coerceAtMost(list.size)
        return taken.withHome(screen, list.toMutableList().apply { add(at, id) })
    }

    /** Drops apps that no longer exist and folders that became empty. */
    fun clean(layout: HomeLayout, installed: Set<String>): HomeLayout {
        val folders = layout.folders.mapValues { (_, f) -> f.copy(apps = f.apps.filter { it in installed }.distinct()) }
        fun cleanList(list: List<String>): List<String> {
            val seen = HashSet<String>()
            return list.flatMap { id ->
                when {
                    HomeLayout.isFolder(id) -> {
                        val f = folders[id]
                        when {
                            f == null || f.apps.isEmpty() -> emptyList()
                            f.apps.size == 1 -> listOf(f.apps[0])
                            else -> listOf(id)
                        }
                    }
                    id in installed -> listOf(id)
                    else -> emptyList()
                }
            }.filter { seen.add(it) }
        }
        val dock = layout.dock.filter { it in installed }.distinct()
        val extra = layout.dockExtra.filter { it in installed && it !in dock }.distinct()
        val cover = cleanList(layout.cover).filter { it !in dock }
        val inner = cleanList(layout.inner).filter { it !in dock && it !in extra }
        val used = (cover + inner).filter(HomeLayout::isFolder).toSet()
        return layout.copy(cover = cover, inner = inner, dock = dock, dockExtra = extra,
            folders = folders.filterKeys { it in used }.filterValues { it.apps.size >= 2 })
    }
}
