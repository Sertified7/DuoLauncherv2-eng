package com.yuksel.pli

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
    var homeCount = 0
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

    private fun hit(p: Offset): DropSpot? {
        if (dockRect.contains(p)) {
            val index = (0 until dockCount).count { i -> dockCells[i]?.let { it.center.x < p.x } == true }
            return DropSpot.Dock(screen, index)
        }
        if (!gridRect.contains(p)) return null
        val dragged = draggedId
        val folderDrag = dragged != null && HomeLayout.isFolder(dragged)
        for (i in 0 until homeCount) {
            val r = homeCells[i] ?: continue
            if (!r.contains(p)) continue
            val iconCenter = Offset(r.center.x, r.top + iconPx / 2f)
            val draggedHere = (source as? DragSource.Home)?.let { it.screen == screen && it.id == dragged } == true && homeIndexOf(i) == dragged
            return when {
                !folderDrag && !draggedHere && (p - iconCenter).getDistance() < iconPx * .36f -> DropSpot.Into(screen, i)
                p.x > r.center.x -> DropSpot.Home(screen, i + 1)
                else -> DropSpot.Home(screen, i)
            }
        }
        return DropSpot.Home(screen, minOf((currentHomePage + 1) * pageCapacity, homeCount))
    }

    var homeIds: List<String> = emptyList()
    private fun homeIndexOf(i: Int) = homeIds.getOrNull(i)
}

/** Converts a spot on the visible lists into one on the saved lists, then applies the drop. */
fun applyDrop(launcher: Launcher, source: DragSource, spot: DropSpot, visibleHome: List<String>) {
    val layout = launcher.layout
    fun layoutIndex(list: List<String>, visible: List<String>, vi: Int): Int = when {
        vi < visible.size -> list.indexOf(visible[vi]).coerceAtLeast(0)
        visible.isEmpty() -> list.size
        else -> list.indexOf(visible.last()) + 1
    }
    val mapped = when (spot) {
        is DropSpot.Home -> DropSpot.Home(spot.screen, layoutIndex(layout.home(spot.screen), visibleHome, spot.index))
        is DropSpot.Into -> DropSpot.Into(spot.screen, layoutIndex(layout.home(spot.screen), visibleHome, spot.index))
        is DropSpot.Dock -> DropSpot.Dock(spot.screen, layoutIndex(layout.dockOf(spot.screen), launcher.visibleDock(spot.screen), spot.index))
    }
    launcher.update { LayoutEdits.drop(it, source, mapped, launcher.dockSizes) }
}

/** Press-and-hold then drag. A hold without moving opens the menu ([onHold]); moving starts the drag and closes it. */
fun Modifier.dragSource(drag: DragState, source: DragSource, origin: () -> Offset, onHold: () -> Unit,
    onDragStarted: () -> Unit, onDrop: () -> Unit): Modifier = pointerInput(source) {
    detectDragGesturesAfterLongPress(
        onDragStart = { offset -> drag.begin(source, origin() + offset); onHold() },
        onDrag = { change, _ -> change.consume(); if (drag.move(origin() + change.position)) onDragStarted() },
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
    onMenu: (String, Rect, DragSource) -> Unit, onCloseMenu: () -> Unit, onEmptyHold: () -> Unit,
    leadingPage: (@Composable (Int) -> Unit)? = null,
) {
    val homePages = maxOf(1, (items.size + metrics.capacity - 1) / metrics.capacity)
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    drag.screen = screen
    drag.homeCount = items.size
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
        Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
            for (row in 0 until metrics.rows) {
                Row(Modifier.fillMaxWidth().height(metrics.cellHeight)) {
                    for (col in 0 until metrics.columns) {
                        val index = first + row * metrics.columns + col
                        val id = items.getOrNull(index)
                        Box(Modifier.weight(1f).fillMaxHeight()) {
                            if (id == null) Box(Modifier.fillMaxSize().pointerInput(Unit) {
                                detectTapGestures(onLongPress = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onEmptyHold() })
                            }) else HomeCell(id, index, screen, launcher, metrics.icon, labels, drag,
                                onOpenApp, onOpenFolder, onMenu, onCloseMenu,
                                onDrop = { drag.source?.let { s -> drag.spot?.let { sp -> applyDrop(launcher, s, sp, drag.homeIds) } } })
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
    Column(Modifier.fillMaxSize()
        .onGloballyPositioned { bounds = it.boundsInRoot(); drag.homeCells[index] = bounds }
        .semantics { contentDescription = if (folder != null) "Dossier $label" else label }
        .clickable(interaction, null, role = Role.Button) {
            if (folder != null) onOpenFolder(id) else app?.let { onOpenApp(it, bounds) }
        }
        .dragSource(drag, source, origin = { bounds.topLeft },
            onHold = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onMenu(id, bounds, source) },
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
    onSearch: (() -> Unit)?,
) {
    val ids = launcher.visibleDock(screen)
    drag.dockCount = ids.size
    val spot = drag.spot
    val haptic = LocalHapticFeedback.current
    val highlight = drag.active && spot is DropSpot.Dock
    BoxWithConstraints(modifier) {
        val count = (ids.size + if (onSearch != null) 1 else 0).coerceAtLeast(1)
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
                        .clickable(remember { MutableInteractionSource() }, null, role = Role.Button) { onOpenApp(app, bounds) }
                        .dragSource(drag, source, { bounds.topLeft },
                            onHold = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); onMenu(id, bounds, source) },
                            onDragStarted = onCloseMenu,
                            onDrop = { drag.source?.let { s -> drag.spot?.let { sp -> applyDrop(launcher, s, sp, drag.homeIds) } } })
                        .alpha(if (dragged) .25f else 1f)
                        .drawBehind {
                            if (before) drawRoundRect(Brush.verticalGradient(Holo.Iridescent), Offset(-8.dp.toPx(), 4.dp.toPx()),
                                Size(3.dp.toPx(), size.height - 8.dp.toPx()), CornerRadius(2.dp.toPx()))
                        }) { AppIcon(app, icon) }
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
