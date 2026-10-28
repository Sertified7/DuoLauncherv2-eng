package com.yuksel.pli

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Several icons picked at once (to remove them together). */
class Selection {
    var active by mutableStateOf(false); private set
    val ids = mutableStateListOf<String>()
    fun start(first: String?) { ids.clear(); first?.let(ids::add); active = true }
    fun toggle(id: String) { if (id in ids) ids.remove(id) else ids.add(id) }
    fun clear() { ids.clear(); active = false }
}

val LocalSelection = staticCompositionLocalOf { Selection() }

/** The round check mark shown on icons in selection mode. */
@Composable
fun SelectMark(selected: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.size(24.dp).then(
        if (selected) Modifier.background(Brush.linearGradient(Holo.Iridescent), CircleShape)
        else Modifier.background(Color.Black.copy(alpha = .45f), CircleShape).border(1.5.dp, Color.White.copy(alpha = .8f), CircleShape)),
        contentAlignment = Alignment.Center) {
        if (selected) Icon(Icons.Rounded.Check, null, tint = Color(0xFF120A22), modifier = Modifier.size(16.dp))
    }
}

/** A press-and-drag in progress, with what the screens registered for hit testing. */
class DragState {
    var source by mutableStateOf<DragSource?>(null); private set
    var active by mutableStateOf(false); private set
    var pointer by mutableStateOf(Offset.Zero)
    var spot by mutableStateOf<DropSpot?>(null); private set
    private var start = Offset.Zero
    var slopPx = 24f

    // Registered by the visible home grid and dock (root coordinates).
    var screen = Screen.COVER
    val homeCells = HashMap<Int, Rect>()
    var homeSlots = 0
    var gridRect = Rect.Zero
    var pageCapacity = 1
    var currentHomePage = 0
    var iconPx = 100f
    val dockCells = HashMap<Int, Rect>()
    var dockCount = 0
    var dockRect = Rect.Zero

    val draggedId get() = source?.itemId

    fun begin(from: DragSource, at: Offset) { source = from; pointer = at; start = at; active = false; spot = null }

    /** Returns true the moment the press turns into a real drag. */
    fun move(to: Offset): Boolean {
        pointer = to
        val becameActive = !active && (to - start).getDistance() > slopPx
        if (becameActive) active = true
        if (active) spot = hit(to)
        return becameActive
    }

    fun clear() { source = null; active = false; spot = null }

    var trashRect = Rect.Zero
    var splitRect = Rect.Zero
    var onSplit: (String) -> Unit = {}
    var bigFolders: Set<String> = emptySet()
    /** True: icons can't be moved (layout locked). */
    var locked = false
    /** The app under the finger right now (for "swipe up on an icon"). */
    var pressedId: String? = null
    var onIconSwipeUp: (String) -> Unit = {}

    private fun hit(p: Offset): DropSpot? {
        if (trashRect != Rect.Zero && trashRect.inflate(12f).contains(p)) return DropSpot.Remove
        if (splitRect != Rect.Zero && splitRect.inflate(12f).contains(p) && draggedId?.let { !HomeLayout.isFolder(it) } == true) return DropSpot.Split
        if (dockRect.contains(p)) {
            val index = (0 until dockCount).count { i -> dockCells[i]?.let { it.center.x < p.x } == true }
            return DropSpot.Dock(screen, index)
        }
        if (!gridRect.contains(p)) return null
        val dragged = draggedId
        val folderDrag = dragged != null && HomeLayout.isFolder(dragged)
        for ((i, r) in homeCells) {
            if (i >= homeSlots || !r.contains(p)) continue
            val id = homeIndexOf(i).orEmpty()
            if (id.isEmpty() || id == dragged) return DropSpot.Home(screen, i)
            if (id in bigFolders) return if (folderDrag) DropSpot.Home(screen, i) else DropSpot.Into(screen, i)
            val iconCenter = Offset(r.center.x, r.top + iconPx / 2f)
            return if (!folderDrag && (p - iconCenter).getDistance() < iconPx * .38f) DropSpot.Into(screen, i)
            else DropSpot.Home(screen, i)
        }
        return null
    }

    var homeIds: List<String> = emptyList()
    private fun homeIndexOf(i: Int) = homeIds.getOrNull(i)
}

/** Converts a spot on the visible dock into one on the saved dock (home slots match 1:1), then applies the drop. */
fun applyDrop(launcher: Launcher, source: DragSource, spot: DropSpot, drag: DragState? = null) {
    if (spot == DropSpot.Split) { drag?.onSplit?.invoke(source.itemId); return }
    val layout = launcher.layout
    val mapped = if (spot is DropSpot.Dock) {
        val visible = launcher.visibleDock(spot.screen)
        val list = layout.dockOf(spot.screen)
        val index = when {
            spot.index < visible.size -> list.indexOf(visible[spot.index]).coerceAtLeast(0)
            visible.isEmpty() -> list.size
            else -> list.indexOf(visible.last()) + 1
        }
        DropSpot.Dock(spot.screen, index)
    } else spot
    launcher.edit(if (spot == DropSpot.Remove) "Retiré de l’accueil" else "Déplacé") { LayoutEdits.drop(it, source, mapped, launcher.dockSizes) }
}

/** Press-and-hold then drag. A hold without moving opens the menu ([onHold]); moving starts the drag and closes it. */
fun Modifier.dragSource(drag: DragState, source: DragSource, origin: () -> Offset, onHold: () -> Unit,
    onDragStarted: () -> Unit, onDrop: () -> Unit): Modifier = pointerInput(source) {
    // Remember which icon the finger is on (the home swipe reads it to open the icon's shortcut).
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        drag.pressedId = source.itemId
        do { val e = awaitPointerEvent(PointerEventPass.Initial) } while (e.changes.any { it.pressed })
        if (drag.pressedId == source.itemId) drag.pressedId = null
    }
}.pointerInput(source) {
    detectDragGesturesAfterLongPress(
        onDragStart = { offset -> if (!drag.locked) drag.begin(source, origin() + offset); onHold() },
        onDrag = { change, _ -> change.consume(); if (drag.source != null && drag.move(origin() + change.position)) onDragStarted() },
        onDragEnd = { if (drag.active) onDrop(); drag.clear() },
        onDragCancel = { drag.clear() },
    )
}

/** Shared metrics for one screen's home grid. */
data class GridMetrics(val columns: Int, val rows: Int, val icon: Dp, val cellHeight: Dp) {
    val capacity get() = columns * rows
}

fun gridMetrics(width: Dp, height: Dp, labels: Boolean, wide: Boolean, columns: Int = 0, rows: Int = 0): GridMetrics {
    val label = if (labels) 24.dp else 8.dp
    val gap = 14.dp
    val autoIcon = if (wide) 62.dp else 60.dp
    val cols = if (columns > 0) columns else if (wide) ((width - 16.dp) / 100.dp).toInt().coerceIn(4, 6) else 4
    val autoCell = autoIcon + label + gap
    val rowCount = if (rows > 0) rows else (height / autoCell).toInt().coerceIn(1, 8)
    // Shrink icons when the chosen grid is denser than the default.
    val byWidth = (width - 16.dp) / cols * .72f
    val byHeight = height / rowCount - label - gap
    val icon = minOf(autoIcon, byWidth, byHeight).coerceAtLeast(30.dp)
    val cell = maxOf(icon + label + gap, height / rowCount).coerceAtMost(height / rowCount)
    return GridMetrics(cols, rowCount, icon, cell)
}

/** The swipeable home pages of one screen: icons flow in order and fill each page. */
@Composable
fun HomePages(
    screen: Screen, launcher: Launcher, items: List<String>, metrics: GridMetrics, pager: PagerState, pageOffset: Int,
    drag: DragState, labels: Boolean, modifier: Modifier,
    onOpenApp: (AppInfo, Rect) -> Unit, onOpenFolder: (String) -> Unit,
    onMenu: (String, Rect, DragSource) -> Unit, onCloseMenu: () -> Unit, onEmptyHold: (Int) -> Unit,
    onEmptyDoubleTap: () -> Unit = {}, homePages: Int, topPadding: Dp = 0.dp,
    leadingPage: (@Composable (Int) -> Unit)? = null,
) {
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    drag.screen = screen
    drag.homeSlots = (homePages + 1) * metrics.capacity
    drag.bigFolders = launcher.layout.folders.values.filter { it.big }.map { it.id }.toSet()
    remember(metrics, homePages, screen) { drag.homeCells.clear(); 0 }
    drag.homeIds = items
    drag.pageCapacity = metrics.capacity
    drag.currentHomePage = (pager.currentPage - pageOffset).coerceAtLeast(0)
    drag.iconPx = with(LocalDensity.current) { metrics.icon.toPx() }
    drag.slopPx = with(LocalDensity.current) { 14.dp.toPx() }

    // Holding an icon near the left or right edge turns the page.
    val edgePx = with(LocalDensity.current) { 28.dp.toPx() }
    val edge = when {
        !drag.active || drag.gridRect == Rect.Zero -> 0
        drag.pointer.x < drag.gridRect.left + edgePx && drag.pointer.y in drag.gridRect.top..drag.gridRect.bottom -> -1
        drag.pointer.x > drag.gridRect.right - edgePx && drag.pointer.y in drag.gridRect.top..drag.gridRect.bottom -> 1
        else -> 0
    }
    LaunchedEffect(edge) {
        while (edge != 0) {
            delay(600)
            val next = (pager.currentPage + edge).coerceIn(pageOffset, pager.pageCount - 1)
            if (next == pager.currentPage) break
            pager.animateScrollToPage(next)
        }
    }

    HorizontalPager(pager, modifier.onGloballyPositioned { drag.gridRect = it.boundsInRoot() },
        beyondViewportPageCount = 8, key = { it }) { page ->
        if (page < pageOffset) { leadingPage?.invoke(page); return@HorizontalPager }
        val homePage = page - pageOffset
        val first = homePage * metrics.capacity
        BoxWithConstraints(Modifier.fillMaxSize().padding(start = 8.dp, end = 8.dp, top = topPadding)) {
            val cw = maxWidth / metrics.columns
            val ch = metrics.cellHeight
            for (row in 0 until metrics.rows) for (col in 0 until metrics.columns) {
                val index = first + row * metrics.columns + col
                val raw = items.getOrNull(index)
                if (raw == HomeLayout.COVERED) continue
                val id = raw?.takeIf { it.isNotEmpty() }
                val folder = id?.let { launcher.layout.folders[it] }?.takeIf { it.big }
                val w = if (folder == null) 1 else if (folder.w <= 0) metrics.columns - col else folder.w.coerceIn(1, metrics.columns - col)
                val h = if (folder == null) 1 else folder.h.coerceIn(1, metrics.rows - row)
                key(index) {
                    Box(Modifier.offset(x = cw * col, y = ch * row).size(cw * w, ch * h)) {
                        when {
                            id == null -> {
                                val target = drag.active && drag.spot.let { it is DropSpot.Home && it.screen == screen && it.index == index }
                                Box(Modifier.fillMaxSize().onGloballyPositioned { drag.homeCells[index] = it.boundsInRoot() }
                                    .pointerInput(homePage) {
                                        detectTapGestures(onDoubleTap = { onEmptyDoubleTap() },
                                            onLongPress = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onEmptyHold(homePage) })
                                    }, contentAlignment = Alignment.TopCenter) {
                                    if (target) Box(Modifier.padding(top = 4.dp).size(metrics.icon).halo(Holo.Violet.copy(alpha = .3f), 10.dp)
                                        .drawBehind { drawCircle(Brush.linearGradient(Holo.Iridescent), style = Stroke(2.dp.toPx())) })
                                }
                            }
                            folder != null -> BigFolder(folder, index, screen, launcher, metrics.icon, drag, onOpenApp, onOpenFolder, onMenu, onCloseMenu,
                                onDrop = { drag.source?.let { s -> drag.spot?.let { sp -> applyDrop(launcher, s, sp, drag) } } })
                            else -> HomeCell(id, index, screen, launcher, metrics.icon, labels, drag,
                                onOpenApp, onOpenFolder, onMenu, onCloseMenu,
                                onDrop = { drag.source?.let { s -> drag.spot?.let { sp -> applyDrop(launcher, s, sp, drag) } } })
                        }
                    }
                }
            }
        }
    }
    // Keep the pager in range after icons are removed.
    LaunchedEffect(homePages, pager.pageCount) {
        if (pager.currentPage >= pager.pageCount) scope.launch { pager.scrollToPage(pager.pageCount - 1) }
    }
}

/** A folder shown open on the home page (2×2, full width…): its apps open with one tap. */
@Composable
private fun BigFolder(
    folder: Folder, index: Int, screen: Screen, launcher: Launcher, icon: Dp, drag: DragState,
    onOpenApp: (AppInfo, Rect) -> Unit, onOpenFolder: (String) -> Unit,
    onMenu: (String, Rect, DragSource) -> Unit, onCloseMenu: () -> Unit, onDrop: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val source = DragSource.Home(screen, folder.id)
    val selection = LocalSelection.current
    val targeted = drag.active && drag.spot.let { it is DropSpot.Into && it.screen == screen && it.index == index }
    val apps = launcher.folderApps(folder.id)
    val shape = RoundedCornerShape(28.dp)
    BoxWithConstraints(Modifier.fillMaxSize().padding(4.dp)
        .onGloballyPositioned { bounds = it.boundsInRoot(); drag.homeCells[index] = bounds }
        .halo(Holo.Violet.copy(alpha = if (targeted) .3f else .1f), 14.dp)
        .glass(shape, if (targeted) .12f else .07f)
        .dragSource(drag, source, { bounds.topLeft },
            onHold = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (selection.active) selection.toggle(folder.id) else onMenu(folder.id, bounds, source)
            }, onDragStarted = onCloseMenu, onDrop = onDrop)
        .alpha(if (drag.active && drag.draggedId == folder.id) .25f else 1f)
        .padding(horizontal = 10.dp, vertical = 8.dp)) {
        val small = (icon * .78f).coerceAtMost(54.dp)
        val cols = ((maxWidth) / (small + 22.dp)).toInt().coerceAtLeast(1)
        val rows = ((maxHeight - 22.dp) / (small + 22.dp)).toInt().coerceAtLeast(1)
        val capacity = cols * rows
        val shown = if (apps.size > capacity) apps.take(capacity - 1) else apps
        Column(Modifier.fillMaxSize()) {
            Text(folder.title, color = Holo.Soft, fontSize = 12.sp, letterSpacing = 1.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable { if (selection.active) selection.toggle(folder.id) else onOpenFolder(folder.id) }
                    .padding(horizontal = 4.dp, vertical = 2.dp))
            Spacer(Modifier.height(4.dp))
            (shown.chunked(cols)).forEach { line ->
                Row(Modifier.fillMaxWidth().weight(1f, fill = false)) {
                    line.forEach { app ->
                        var appBounds by remember { mutableStateOf(Rect.Zero) }
                        Column(Modifier.weight(1f).onGloballyPositioned { appBounds = it.boundsInRoot() }
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { if (selection.active) selection.toggle(folder.id) else onOpenApp(app, appBounds) }
                            .padding(vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            AppIcon(app, small)
                            Text(app.label, color = Holo.Soft, fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                    repeat(cols - line.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            if (apps.size > capacity) Text("+${apps.size - shown.size}", color = Holo.Violet, fontSize = 13.sp,
                modifier = Modifier.align(Alignment.End).clip(RoundedCornerShape(10.dp)).clickable { onOpenFolder(folder.id) }.padding(6.dp))
        }
        if (selection.active) SelectMark(folder.id in selection.ids, Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun HomeCell(
    id: String, index: Int, screen: Screen, launcher: Launcher, icon: Dp, labels: Boolean, drag: DragState,
    onOpenApp: (AppInfo, Rect) -> Unit, onOpenFolder: (String) -> Unit,
    onMenu: (String, Rect, DragSource) -> Unit, onCloseMenu: () -> Unit, onDrop: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val source = DragSource.Home(screen, id)
    val dragged = drag.active && drag.draggedId == id
    val spot = drag.spot
    val intoHere = spot is DropSpot.Into && spot.screen == screen && spot.index == index
    val beforeHere = spot is DropSpot.Home && spot.screen == screen && spot.index == index && !dragged
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .9f else if (intoHere) 1.12f else 1f, label = "press")
    val folder = if (HomeLayout.isFolder(id)) launcher.layout.folders[id] else null
    val app = if (folder == null) launcher.byId[id] else null
    val label = folder?.title ?: app?.label ?: ""
    val selection = LocalSelection.current
    Column(Modifier.fillMaxSize()
        .onGloballyPositioned { bounds = it.boundsInRoot(); drag.homeCells[index] = bounds }
        .semantics { contentDescription = if (folder != null) "Dossier $label" else label }
        .clickable(interaction, null, role = Role.Button) {
            if (selection.active) selection.toggle(id)
            else if (folder != null) onOpenFolder(id) else app?.let { onOpenApp(it, bounds) }
        }
        .dragSource(drag, source, origin = { bounds.topLeft },
            onHold = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                if (selection.active) selection.toggle(id) else onMenu(id, bounds, source)
            },
            onDragStarted = onCloseMenu, onDrop = onDrop)
        .alpha(if (dragged) .25f else 1f)
        .drawBehind {
            if (beforeHere) drawRoundRect(Brush.verticalGradient(Holo.Iridescent), Offset(-2.dp.toPx(), 6.dp.toPx()),
                Size(3.dp.toPx(), icon.toPx() - 6.dp.toPx()), CornerRadius(2.dp.toPx()))
        },
        horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(4.dp))
        Box(Modifier.graphicsLayer { scaleX = scale; scaleY = scale }) {
            if (folder != null) FolderIcon(launcher.folderApps(id), icon, intoHere)
            else if (app != null) {
                AppIcon(app, icon)
                if (intoHere) Box(Modifier.size(icon).drawBehind {
                    drawCircle(Brush.linearGradient(Holo.Iridescent), style = Stroke(2.5.dp.toPx()))
                })
            }
            if (selection.active) SelectMark(id in selection.ids, Modifier.align(Alignment.TopStart).offset(x = (-4).dp, y = (-4).dp))
        }
        if (labels) IconLabel(label)
    }
}

@Composable
fun IconLabel(text: String, color: Color = Holo.Soft) {
    Text(text, color = color, fontSize = 11.sp, lineHeight = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center, style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = .6f), Offset(0f, 1f), 4f)),
        modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp))
}

@Composable
fun FolderIcon(apps: List<AppInfo>, size: Dp, highlighted: Boolean = false) {
    val shape = RoundedCornerShape(size * .3f)
    Box(Modifier.size(size).halo(Holo.Cyan.copy(alpha = if (highlighted) .35f else .12f), size * .2f).glass(shape, .09f)
        .then(if (highlighted) Modifier.drawBehind { drawRoundRect(Brush.linearGradient(Holo.Iridescent),
            cornerRadius = CornerRadius((size * .3f).toPx()), style = Stroke(2.5.dp.toPx())) } else Modifier)
        .padding(size * .14f)) {
        val mini = size * .3f
        apps.take(4).forEachIndexed { i, app ->
            Image(app.icon, null, Modifier.align(when (i) {
                0 -> Alignment.TopStart; 1 -> Alignment.TopEnd; 2 -> Alignment.BottomStart; else -> Alignment.BottomEnd
            }).size(mini).clip(CircleShape))
        }
    }
}

/** Page dots. The first [leading] dots (Discover, Aujourd'hui) are tinted cyan. */
@Composable
fun PageDots(pager: PagerState, count: Int, leading: Int, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    if (count <= 1) { Spacer(modifier.height(18.dp)); return }
    Row(modifier.height(18.dp), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(count) { i ->
            val current = pager.currentPage == i
            Box(Modifier.size(18.dp).clip(CircleShape).clickable { scope.launch { pager.animateScrollToPage(i) } }
                .semantics { contentDescription = if (i < leading) "Page spéciale ${i + 1}" else "Page ${i - leading + 1}" },
                contentAlignment = Alignment.Center) {
                val color = when { current -> null; i < leading -> Holo.Cyan.copy(alpha = .55f); else -> Color.White.copy(alpha = .35f) }
                Box(Modifier.size(if (current) 7.dp else 5.dp).background(
                    if (color == null) Brush.linearGradient(Holo.Iridescent) else Brush.linearGradient(listOf(color, color)), CircleShape))
            }
        }
    }
}

/** The glass dock pill, shared by both screens. [onSearch] adds the search orb (cover screen). */
@Composable
fun Dock(
    launcher: Launcher, screen: Screen, drag: DragState, maxIcon: Dp, modifier: Modifier,
    onOpenApp: (AppInfo, Rect) -> Unit, onMenu: (String, Rect, DragSource) -> Unit, onCloseMenu: () -> Unit,
    onSearch: (() -> Unit)?, recents: List<AppInfo> = emptyList(),
) {
    val ids = launcher.visibleDock(screen)
    val selection = LocalSelection.current
    drag.dockCount = ids.size
    val spot = drag.spot
    val haptic = LocalHapticFeedback.current
    val highlight = drag.active && spot is DropSpot.Dock
    BoxWithConstraints(modifier) {
        val count = (ids.size + recents.size + if (onSearch != null) 1 else 0).coerceAtLeast(1)
        val icon = minOf(maxIcon, (maxWidth - 24.dp - 10.dp * count) / count)
        Row(Modifier.fillMaxWidth().height(icon + 24.dp).halo(Holo.Violet.copy(alpha = if (highlight) .22f else 0f), 8.dp)
            .glass(RoundedCornerShape(50), if (highlight) .12f else .07f)
            .onGloballyPositioned { drag.dockRect = it.boundsInRoot() }
            .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            if (ids.isEmpty()) Text("Glisse une appli ici", color = Holo.Muted, fontSize = 13.sp)
            ids.forEachIndexed { index, id ->
                val app = launcher.byId[id] ?: return@forEachIndexed
                key(id) {
                    var bounds by remember { mutableStateOf(Rect.Zero) }
                    val source = DragSource.Dock(id)
                    val dragged = drag.active && drag.draggedId == id
                    val before = spot is DropSpot.Dock && spot.index == index && drag.active
                    Box(Modifier.size(icon)
                        .onGloballyPositioned { bounds = it.boundsInRoot(); drag.dockCells[index] = bounds }
                        .semantics { contentDescription = app.label }
                        .clickable(remember { MutableInteractionSource() }, null, role = Role.Button) {
                            if (selection.active) selection.toggle(id) else onOpenApp(app, bounds)
                        }
                        .dragSource(drag, source, { bounds.topLeft },
                            onHold = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (selection.active) selection.toggle(id) else onMenu(id, bounds, source)
                            },
                            onDragStarted = onCloseMenu,
                            onDrop = { drag.source?.let { s -> drag.spot?.let { sp -> applyDrop(launcher, s, sp, drag) } } })
                        .alpha(if (dragged) .25f else 1f)
                        .drawBehind {
                            if (before) drawRoundRect(Brush.verticalGradient(Holo.Iridescent), Offset(-8.dp.toPx(), 4.dp.toPx()),
                                Size(3.dp.toPx(), size.height - 8.dp.toPx()), CornerRadius(2.dp.toPx()))
                        }) {
                        AppIcon(app, icon)
                        if (selection.active) SelectMark(id in selection.ids, Modifier.align(Alignment.TopStart).offset(x = (-4).dp, y = (-4).dp))
                    }
                }
            }
            if (recents.isNotEmpty()) {
                // Recently used apps, after a thin line (inner screen).
                Box(Modifier.width(1.dp).height(icon * .7f).background(Color.White.copy(alpha = .18f)))
                recents.forEach { app ->
                    var bounds by remember { mutableStateOf(Rect.Zero) }
                    Box(Modifier.size(icon).onGloballyPositioned { bounds = it.boundsInRoot() }
                        .semantics { contentDescription = "Récente : ${app.label}" }
                        .clickable(remember { MutableInteractionSource() }, null, role = Role.Button) { onOpenApp(app, bounds) }
                        .alpha(.85f)) { AppIcon(app, icon) }
                }
            }
            if (onSearch != null) SearchOrb(icon, onSearch)
        }
    }
}

@Composable
fun SearchOrb(size: Dp, onClick: () -> Unit) {
    Box(Modifier.size(size).halo(Holo.Violet.copy(alpha = .45f), size * .3f)
        .background(Brush.linearGradient(listOf(Holo.Cyan, Holo.Violet, Holo.Pink)), CircleShape)
        .clip(CircleShape).clickable(onClick = onClick).semantics { contentDescription = "Rechercher" },
        contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.Search, null, tint = Color(0xFF120A22), modifier = Modifier.size(size * .42f))
    }
}

/** The icon that follows the finger while dragging. */
@Composable
fun DragGhost(launcher: Launcher, drag: DragState, icon: Dp) {
    val id = drag.draggedId ?: return
    if (!drag.active) return
    val half = with(LocalDensity.current) { (icon / 2).toPx() }
    Box(Modifier.offset { IntOffset((drag.pointer.x - half).roundToInt(), (drag.pointer.y - half * 1.3f).roundToInt()) }
        .graphicsLayer { scaleX = 1.12f; scaleY = 1.12f; alpha = .95f }) {
        if (HomeLayout.isFolder(id)) FolderIcon(launcher.folderApps(id), icon)
        else launcher.byId[id]?.let { AppIcon(it, icon, showBadge = false) }
    }
}
