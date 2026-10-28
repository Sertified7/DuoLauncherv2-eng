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

/** Columns × rows a screen's home slots were arranged for. */
data class Grid(val columns: Int, val rows: Int) {
    val capacity get() = columns * rows
}

/**
 * Each screen's home is a list of slots: slot i is page i / capacity, cell i % capacity.
 * A slot holds an app id, a folder id (starting with [FOLDER_PREFIX]) or [EMPTY], so icons stay
 * exactly where they were put. The dock is shared (plus inner-only extra places).
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
    val coverPages: Int = 1,
    val innerPages: Int = 1,
    val coverGrid: Grid? = null,
    val innerGrid: Grid? = null,
) {
    fun home(screen: Screen) = if (screen == Screen.COVER) cover else inner
    fun withHome(screen: Screen, items: List<String>): HomeLayout {
        val trimmed = items.dropLastWhile { it == EMPTY }
        return if (screen == Screen.COVER) copy(cover = trimmed) else copy(inner = trimmed)
    }
    fun pages(screen: Screen) = if (screen == Screen.COVER) coverPages else innerPages
    fun withPages(screen: Screen, n: Int) = if (screen == Screen.COVER) copy(coverPages = n.coerceAtLeast(1)) else copy(innerPages = n.coerceAtLeast(1))
    fun grid(screen: Screen) = if (screen == Screen.COVER) coverGrid else innerGrid

    /** Every app id placed on this screen, including inside folders and the dock. */
    fun placedApps(screen: Screen): Set<String> = buildSet {
        home(screen).forEach { if (isFolder(it)) folders[it]?.apps?.let(::addAll) else if (it != EMPTY) add(it) }
        addAll(dock)
        if (screen == Screen.INNER) addAll(dockExtra)
    }

    fun dockOf(screen: Screen) = if (screen == Screen.INNER) dock + dockExtra else dock

    fun toJson(): JSONObject = JSONObject().apply {
        put("cover", JSONArray(cover)); put("inner", JSONArray(inner)); put("dock", JSONArray(dock)); put("dockExtra", JSONArray(dockExtra))
        put("slots", 1)
        put("coverPages", coverPages); put("innerPages", innerPages)
        coverGrid?.let { put("coverGrid", "${it.columns}x${it.rows}") }
        innerGrid?.let { put("innerGrid", "${it.columns}x${it.rows}") }
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
        const val EMPTY = ""
        fun isFolder(id: String) = id.startsWith(FOLDER_PREFIX)

        fun fromJson(json: JSONObject): HomeLayout {
            fun strings(name: String, keepEmpty: Boolean = false) = json.optJSONArray(name)?.let { a -> List(a.length()) { a.optString(it) } }
                ?.filter { keepEmpty || it.isNotEmpty() } ?: emptyList()
            fun grid(name: String) = json.optString(name, "").split('x').mapNotNull { it.toIntOrNull() }
                .takeIf { it.size == 2 && it[0] > 0 && it[1] > 0 }?.let { Grid(it[0], it[1]) }
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
            return HomeLayout(strings("cover", true), strings("inner", true), strings("dock"), strings("dockExtra"), folders, today,
                strings("hidden").toSet(), json.optInt("coverPages", 1), json.optInt("innerPages", 1), grid("coverGrid"), grid("innerGrid"))
        }
    }
}

// ---------- Pure layout edits (no Android types, easy to reason about) ----------

/** Where a dragged item lands. */
sealed interface DropSpot {
    /** In slot [index]: an empty slot takes the item; a full one makes room by pushing icons forward. */
    data class Home(val screen: Screen, val index: Int) : DropSpot
    /** Onto the item at [index] (an app or a folder): makes or fills a folder. */
    data class Into(val screen: Screen, val index: Int) : DropSpot
    data class Dock(val screen: Screen, val index: Int) : DropSpot
    /** On the bin: taken off the home screen or dock (the app stays in the app list). */
    data object Remove : DropSpot
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
    private const val EMPTY = HomeLayout.EMPTY

    /** Empties the slots holding [ids] (other icons do not move). */
    fun blank(list: List<String>, ids: Set<String>) = list.map { if (it in ids) EMPTY else it }.dropLastWhile { it == EMPTY }

    /** Puts [id] in slot [at]: takes it if empty, else pushes icons forward up to the next empty slot. */
    fun place(list: List<String>, at: Int, id: String): List<String> {
        val out = list.toMutableList()
        while (out.size < at) out += EMPTY
        if (at == out.size) { out += id; return out }
        if (out[at] == EMPTY) { out[at] = id; return out }
        out.add(at, id)
        val hole = (at + 1 until out.size).firstOrNull { out[it] == EMPTY }
        if (hole != null) out.removeAt(hole)
        return out
    }

    /** First empty slot at or after [from], else the end. */
    fun firstFree(list: List<String>, from: Int = 0): Int = (from until list.size).firstOrNull { list[it] == EMPTY } ?: maxOf(from, list.size)

    fun removeFromHome(layout: HomeLayout, screen: Screen, id: String) =
        layout.withHome(screen, blank(layout.home(screen), setOf(id)))

    fun addToHome(layout: HomeLayout, screen: Screen, id: String): HomeLayout {
        if (id in layout.placedApps(screen)) return layout
        val list = layout.home(screen)
        return layout.withHome(screen, place(list, firstFree(list), id))
    }

    private fun withPagesFor(layout: HomeLayout, screen: Screen): HomeLayout {
        val cap = layout.grid(screen)?.capacity ?: return layout
        val needed = (layout.home(screen).size + cap - 1) / cap
        return if (needed > layout.pages(screen)) layout.withPages(screen, needed) else layout
    }

    /** Puts [list] in the dock as seen from [screen]: on the inner screen the places past the shared ones are inner-only. */
    fun withDock(layout: HomeLayout, list: List<String>, screen: Screen, sizes: DockSizes): HomeLayout {
        val (dock, extra) = if (screen == Screen.INNER) list.take(sizes.cover) to list.drop(sizes.cover)
            else list to (layout.dockExtra - list.toSet())
        // An app sits either in the dock or on a home page, never both.
        var out = layout.copy(dock = dock, dockExtra = extra)
            .withHome(Screen.COVER, blank(layout.cover, dock.toSet()))
            .withHome(Screen.INNER, blank(layout.inner, (dock + extra).toSet()))
        // Apps pushed out of the shared places stay reachable on the cover screen.
        layout.dock.filter { it in extra }.forEach { out = addToHome(out, Screen.COVER, it) }
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
        var out = layout.copy(dock = dock, dockExtra = extra)
            .withHome(Screen.COVER, blank(layout.cover, dock.toSet()))
            .withHome(Screen.INNER, blank(layout.inner, (dock + extra).toSet()))
        (extra + leftover).forEach { out = addToHome(out, Screen.COVER, it) }
        leftover.forEach { out = addToHome(out, Screen.INNER, it) }
        return out
    }

    /** Applies a finished drag. Returns the layout unchanged when the drop makes no sense. */
    fun drop(layout: HomeLayout, source: DragSource, spot: DropSpot, sizes: DockSizes): HomeLayout {
        val id = source.itemId
        // Take the item out of where it was. On a home page its slot just becomes empty.
        var oldDockIndex = -1
        val base = when (source) {
            is DragSource.Home -> layout.withHome(source.screen, layout.home(source.screen).map { if (it == id) EMPTY else it })
            is DragSource.Dock -> {
                oldDockIndex = layout.dockOf((spot as? DropSpot.Dock)?.screen ?: Screen.INNER).indexOf(id)
                layout.copy(dock = layout.dock - id, dockExtra = layout.dockExtra - id)
            }
            is DragSource.InFolder -> takeOutOfFolder(layout, source.folderId, id)
        }
        when (spot) {
            DropSpot.Remove -> return if (HomeLayout.isFolder(id)) base.copy(folders = base.folders - id) else base
            is DropSpot.Home -> {
                // Keep untrimmed slots while placing (the source slot may be just before the target).
                val list = if (source is DragSource.Home && source.screen == spot.screen)
                    layout.home(spot.screen).map { if (it == id) EMPTY else it } else base.home(spot.screen)
                return withPagesFor(base.withHome(spot.screen, place(list, spot.index.coerceAtLeast(0), id)), spot.screen)
            }
            is DropSpot.Dock -> {
                if (HomeLayout.isFolder(id)) return layout
                val list = base.dockOf(spot.screen)
                if (list.size >= sizes.max(spot.screen)) return layout
                val at = (if (source is DragSource.Dock && oldDockIndex in 0 until spot.index) spot.index - 1 else spot.index).coerceIn(0, list.size)
                return withDock(base, list.toMutableList().apply { add(at, id) }, spot.screen, sizes)
            }
            is DropSpot.Into -> {
                if (HomeLayout.isFolder(id)) return layout
                val targetId = layout.home(spot.screen).getOrNull(spot.index)?.takeIf { it != EMPTY } ?: return layout
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

    /** Removes [id] from a folder; a folder left with one app turns back into that app (in the same slot). */
    fun takeOutOfFolder(layout: HomeLayout, folderId: String, id: String): HomeLayout {
        val folder = layout.folders[folderId] ?: return layout
        val rest = folder.apps - id
        if (rest.size >= 2) return layout.copy(folders = layout.folders + (folderId to folder.copy(apps = rest)))
        fun swap(list: List<String>) = list.map { if (it == folderId) rest.firstOrNull() ?: EMPTY else it }
        return layout.copy(folders = layout.folders - folderId).withHome(Screen.COVER, swap(layout.cover)).withHome(Screen.INNER, swap(layout.inner))
    }

    /** Moves an app out of its folder into the next free slot after the folder. */
    fun moveOutOfFolder(layout: HomeLayout, screen: Screen, folderId: String, id: String): HomeLayout {
        val index = layout.home(screen).indexOf(folderId)
        val taken = takeOutOfFolder(layout, folderId, id)
        val list = taken.home(screen)
        return taken.withHome(screen, place(list, firstFree(list, if (index < 0) 0 else index + 1), id))
    }

    /** Turns a folder back into its apps: the first takes the folder's slot, the others the next free slots. */
    fun ungroup(layout: HomeLayout, screen: Screen, folderId: String): HomeLayout {
        val apps = layout.folders[folderId]?.apps ?: return layout
        val index = layout.home(screen).indexOf(folderId)
        if (index < 0) return layout
        var list = layout.home(screen).toMutableList().also { it[index] = apps.firstOrNull() ?: EMPTY }.toList()
        apps.drop(1).forEach { list = place(list, firstFree(list, index + 1), it) }
        return withPagesFor(layout.copy(folders = layout.folders - folderId).withHome(screen, list), screen)
    }

    /** Takes several apps or folders off a screen's home and the dock at once. */
    fun removeMany(layout: HomeLayout, screen: Screen, ids: Set<String>): HomeLayout =
        layout.copy(dock = layout.dock - ids, dockExtra = layout.dockExtra - ids, folders = layout.folders - ids)
            .withHome(screen, blank(layout.home(screen), ids))

    /** Adds several apps at once, filling free slots from [fromSlot] (the page the user was on). */
    fun addMany(layout: HomeLayout, screen: Screen, ids: List<String>, fromSlot: Int): HomeLayout {
        val placed = layout.placedApps(screen)
        var list = layout.home(screen)
        ids.filter { it !in placed }.distinct().forEach { id -> list = place(list, firstFree(list, fromSlot), id) }
        return withPagesFor(layout.withHome(screen, list), screen)
    }

    /** Adds an empty page at the end. */
    fun addPage(layout: HomeLayout, screen: Screen) = layout.withPages(screen, layout.pages(screen) + 1)

    /** Removes [page] if it is empty; the pages after it move back one. */
    fun deletePage(layout: HomeLayout, screen: Screen, page: Int, capacity: Int): HomeLayout {
        val list = layout.home(screen)
        val range = page * capacity until (page + 1) * capacity
        if (range.any { list.getOrElse(it) { EMPTY } != EMPTY } || layout.pages(screen) <= 1) return layout
        val out = list.filterIndexed { i, _ -> i !in range }
        return layout.withHome(screen, out).withPages(screen, layout.pages(screen) - 1)
    }

    /** Re-arranges slots for a new grid size, keeping each icon's page, row and column when it still fits. */
    fun regrid(list: List<String>, old: Grid?, new: Grid): List<String> {
        if (old == null || old == new) return list
        val out = ArrayList<String>()
        val overflow = ArrayList<String>()
        list.forEachIndexed { i, id ->
            if (id == EMPTY) return@forEachIndexed
            val page = i / old.capacity; val cell = i % old.capacity
            val row = cell / old.columns; val col = cell % old.columns
            if (row < new.rows && col < new.columns) {
                val at = page * new.capacity + row * new.columns + col
                while (out.size <= at) out += EMPTY
                out[at] = id
            } else overflow += id
        }
        var result: List<String> = out
        overflow.forEach { result = place(result, firstFree(result), it) }
        return result.dropLastWhile { it == EMPTY }
    }

    fun withGrid(layout: HomeLayout, screen: Screen, grid: Grid): HomeLayout {
        if (layout.grid(screen) == grid) return layout
        val list = regrid(layout.home(screen), layout.grid(screen), grid)
        val updated = (if (screen == Screen.COVER) layout.copy(coverGrid = grid) else layout.copy(innerGrid = grid)).withHome(screen, list)
        return withPagesFor(updated, screen)
    }

    /** Empties slots of apps that no longer exist and drops folders that became empty. */
    fun clean(layout: HomeLayout, installed: Set<String>): HomeLayout {
        val folders = layout.folders.mapValues { (_, f) -> f.copy(apps = f.apps.filter { it in installed }.distinct()) }
        val dock = layout.dock.filter { it in installed }.distinct()
        val extra = layout.dockExtra.filter { it in installed && it !in dock }.distinct()
        fun cleanList(list: List<String>, docked: Set<String>): List<String> {
            val seen = HashSet<String>()
            return list.map { id ->
                val kept = when {
                    id == EMPTY -> EMPTY
                    HomeLayout.isFolder(id) -> {
                        val f = folders[id]
                        when {
                            f == null || f.apps.isEmpty() -> EMPTY
                            f.apps.size == 1 -> f.apps[0]
                            else -> id
                        }
                    }
                    id in installed -> id
                    else -> EMPTY
                }
                if (kept == EMPTY || kept in docked || !seen.add(kept)) EMPTY else kept
            }
        }
        val cover = cleanList(layout.cover, dock.toSet())
        val inner = cleanList(layout.inner, (dock + extra).toSet())
        val used = (cover + inner).filter(HomeLayout::isFolder).toSet()
        return layout.copy(dock = dock, dockExtra = extra, folders = folders.filterKeys { it in used }.filterValues { it.apps.size >= 2 })
            .withHome(Screen.COVER, cover).withHome(Screen.INNER, inner)
    }
}
