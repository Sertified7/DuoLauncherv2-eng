package com.yuksel.pli

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddBox
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.ViewDay
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material.icons.rounded.CallSplit
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SwipeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/** Where a long-pressed item lives, which decides the menu's actions. */
sealed interface Origin {
    data class Home(val screen: Screen) : Origin
    data object Dock : Origin
    data object Drawer : Origin
    data class InFolder(val folderId: String, val screen: Screen) : Origin
}

data class MenuRequest(val id: String, val bounds: Rect, val origin: Origin)

/** Dims the screen; a tap outside closes. */
@Composable
fun Scrim(alpha: Float = .45f, onDismiss: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = alpha))
        .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss))
}

/** Places its content below [anchor], or above it when there is no room. */
@Composable
fun Anchored(anchor: Rect, content: @Composable () -> Unit) {
    Layout(content, Modifier.fillMaxSize()) { measurables, constraints ->
        val loose = constraints.copy(minWidth = 0, minHeight = 0)
        val placeable = measurables.first().measure(loose)
        layout(constraints.maxWidth, constraints.maxHeight) {
            val margin = 12.dp.roundToPx()
            val x = (anchor.center.x - placeable.width / 2f).roundToInt().coerceIn(margin, (constraints.maxWidth - placeable.width - margin).coerceAtLeast(margin))
            val below = anchor.bottom.roundToInt() + margin
            val y = if (anchor == Rect.Zero) (constraints.maxHeight - placeable.height) / 2
                else if (below + placeable.height < constraints.maxHeight - margin) below
                else (anchor.top.roundToInt() - margin - placeable.height).coerceAtLeast(margin)
            placeable.place(x, y)
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, text: String, tint: Color = Holo.Ink, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick)
        .padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, color = tint, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Icon(icon, null, tint = tint.copy(alpha = .85f), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun MenuCard(content: @Composable () -> Unit) {
    Column(Modifier.width(290.dp).heightIn(max = 600.dp).halo(Holo.Violet.copy(alpha = .14f), 24.dp).clip(RoundedCornerShape(28.dp))
        .background(Holo.Panel.copy(alpha = .94f)).border(1.dp, Holo.edgeBrush, RoundedCornerShape(28.dp))
        .clickable(remember { MutableInteractionSource() }, null) {}.verticalScroll(rememberScrollState()).padding(8.dp)) { content() }
}

@Composable
private fun Divider() {
    Box(Modifier.padding(vertical = 4.dp, horizontal = 10.dp).fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = .1f)))
}

/** The long-press menu of an app: its shortcuts, then home/dock/folder actions. */
@Composable
fun AppMenu(request: MenuRequest, launcher: Launcher, currentScreen: Screen, onDismiss: () -> Unit,
    onRenameFolder: (String) -> Unit, onUninstall: (AppInfo) -> Unit, onInfo: (AppInfo) -> Unit, onOpenApp: (AppInfo) -> Unit = {},
    onSelect: (String) -> Unit = {}, onRename: (AppInfo) -> Unit = {}, onChangeIcon: (AppInfo) -> Unit = {}) {
    val context = LocalContext.current
    val locked = launcher.settings.lockLayout
    Box(Modifier.fillMaxSize()) {
        Scrim(.35f, onDismiss)
        Anchored(request.bounds) {
            MenuCard {
                val folder = if (HomeLayout.isFolder(request.id)) launcher.layout.folders[request.id] else null
                if (folder != null) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        FolderIcon(launcher.folderApps(folder.id), 40.dp)
                        Text(folder.title, color = Holo.Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp))
                    }
                    Divider()
                    MenuRow(Icons.Rounded.DriveFileRenameOutline, "Rename") { onRenameFolder(folder.id); onDismiss() }
                    if (!locked) {
                        Text("Size on home screen", color = Holo.Muted, fontSize = 12.sp, modifier = Modifier.padding(start = 14.dp, top = 6.dp))
                        Row(Modifier.padding(horizontal = 8.dp, vertical = 8.dp).horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            FOLDER_SIZES.forEach { (w, h, label) ->
                                PillButton(label, highlighted = folder.w == w && folder.h == h) {
                                    launcher.edit("Folder size") { LayoutEdits.setFolderSize(it, folder.id, w, h) }
                                }
                            }
                        }
                    }
                    if (!locked) MenuRow(Icons.Rounded.CallSplit, "Ungroup folder") {
                        launcher.edit("Folder ungrouped") { l -> LayoutEdits.ungroup(l, (request.origin as? Origin.Home)?.screen ?: currentScreen, folder.id) }
                        onDismiss()
                    }
                    if (!locked) MenuRow(Icons.Rounded.RemoveCircleOutline, "Remove from home", Holo.Pink) {
                        val screen = (request.origin as? Origin.Home)?.screen ?: currentScreen
                        launcher.edit("Folder removed") { l -> l.withHome(screen, LayoutEdits.blank(l.home(screen), setOf(folder.id))).copy(folders = l.folders - folder.id) }
                        onDismiss()
                    }
                    return@MenuCard
                }
                val app = launcher.byId[request.id] ?: run { onDismiss(); return@MenuCard }
                Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app, 40.dp, showBadge = false)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(app.label, color = Holo.Ink, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (app.work) Text("Work profile", color = Holo.Muted, fontSize = 12.sp)
                    }
                }
                val previews = BadgeService.previews.collectAsState().value[app.packageName].orEmpty()
                if (previews.isNotEmpty()) {
                    Divider()
                    previews.forEach { n ->
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                            .clickable { if (!BadgeService.open(n)) onOpenApp(app); onDismiss() }
                            .padding(horizontal = 14.dp, vertical = 8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(7.dp).background(Holo.Badge, CircleShape))
                                Text(n.title.ifBlank { app.label }, color = Holo.Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 8.dp))
                                Text(ago(java.time.Instant.ofEpochMilli(n.time)), color = Holo.Muted, fontSize = 11.sp)
                            }
                            if (n.text.isNotBlank()) Text(n.text, color = Holo.Soft, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(start = 15.dp, top = 2.dp))
                        }
                    }
                }
                val shortcuts by produceState(emptyList<AppShortcut>(), app.id) {
                    value = withContext(Dispatchers.IO) { loadAppShortcuts(context, app) }
                }
                if (shortcuts.isNotEmpty()) {
                    Divider()
                    val favourite = launcher.layout.swipeShortcuts[app.id] ?: shortcuts.first().info.id
                    shortcuts.forEach { s ->
                        val isFavourite = s.info.id == favourite
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp))
                            .clickable { startAppShortcut(context, s); onDismiss() }.padding(start = 14.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            if (s.icon != null) Image(s.icon, null, Modifier.padding(end = 10.dp).size(22.dp).clip(CircleShape))
                            Text(s.label, color = Holo.Ink, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            // The arrow marks the shortcut opened by swiping up on the icon; tap it to choose another.
                            Box(Modifier.size(40.dp).clip(CircleShape).clickable {
                                launcher.update { l -> l.copy(swipeShortcuts = l.swipeShortcuts + (app.id to s.info.id)) }
                            }.semantics { contentDescription = "Open with swipe up" }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.SwipeUp, null, tint = if (isFavourite) Holo.Violet else Holo.Muted.copy(alpha = .4f), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                    Text("Swipe up on the icon to open the shortcut marked ↑", color = Holo.Muted, fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
                }
                Divider()
                val layout = launcher.layout
                val screen = when (val o = request.origin) { is Origin.Home -> o.screen; is Origin.InFolder -> o.screen; else -> currentScreen }
                if (!locked) when (val o = request.origin) {
                    is Origin.InFolder -> MenuRow(Icons.Rounded.Logout, "Remove from folder") {
                        launcher.edit("Removed from folder") { LayoutEdits.moveOutOfFolder(it, o.screen, o.folderId, app.id) }; onDismiss()
                    }
                    is Origin.Home -> MenuRow(Icons.Rounded.RemoveCircleOutline, "Remove from home") {
                        launcher.edit("Removed from home") { LayoutEdits.removeFromHome(it, o.screen, app.id) }; onDismiss()
                    }
                    else -> if (app.id !in layout.placedApps(screen)) MenuRow(Icons.Rounded.AddToHomeScreen, "Add to home") {
                        launcher.update { LayoutEdits.addToHome(it, screen, app.id) }; onDismiss()
                    }
                }
                if (request.origin !is Origin.InFolder && !locked) {
                    if (app.id in layout.dockOf(screen)) MenuRow(Icons.Rounded.Dock, "Remove from dock") {
                        launcher.edit("Removed from dock") { LayoutEdits.removeFromDock(it, app.id, screen) }; onDismiss()
                    } else if (layout.dockOf(screen).size < launcher.dockSizes.max(screen)) MenuRow(Icons.Rounded.Dock, "Add to dock") {
                        launcher.update { LayoutEdits.addToDock(it, app.id, screen, launcher.dockSizes) }; onDismiss()
                    }
                }
                if (!locked && (request.origin is Origin.Home || request.origin == Origin.Dock))
                    MenuRow(Icons.Rounded.Checklist, "Select multiple") { onSelect(app.id); onDismiss() }
                MenuRow(Icons.Rounded.DriveFileRenameOutline, "Rename") { onRename(app); onDismiss() }
                MenuRow(Icons.Rounded.Palette, "Change icon") { onChangeIcon(app); onDismiss() }
                val isProtected = app.id in launcher.layout.protectedApps
                MenuRow(if (isProtected) Icons.Rounded.LockOpen else Icons.Rounded.Fingerprint,
                    if (isProtected) "Stop protecting" else "Protect with fingerprint") {
                    launcher.update { l -> l.copy(protectedApps = if (isProtected) l.protectedApps - app.id else l.protectedApps + app.id) }
                    onDismiss()
                }
                val storage by produceState<String?>(null, app.id) { value = withContext(Dispatchers.IO) { appStorage(context, app) } }
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(16.dp)).clickable { onInfo(app); onDismiss() }
                    .padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Storage and cache", color = Holo.Ink, fontSize = 15.sp)
                        Text(storage ?: if (hasUsageAccess(context)) "Calculating…" else "Size shown with usage access",
                            color = Holo.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Icon(Icons.Rounded.Storage, null, tint = Holo.Ink.copy(alpha = .85f), modifier = Modifier.size(20.dp))
                }
                MenuRow(Icons.Rounded.Info, "App info") { onInfo(app); onDismiss() }
                if (!locked) MenuRow(Icons.Rounded.VisibilityOff, "Hide app") { launcher.hide(app); onDismiss() }
                MenuRow(Icons.Rounded.Delete, "Uninstall", Holo.Pink) { onUninstall(app); onDismiss() }
            }
        }
    }
}

/** Long press on an empty spot of the home screen. */
@Composable
fun HomeMenu(onDismiss: () -> Unit, onWidgets: (() -> Unit)?, onSettings: () -> Unit, onApps: () -> Unit, onToday: (() -> Unit)?,
    onAddPage: () -> Unit, onDeletePage: (() -> Unit)?, onSelect: () -> Unit = {}, onAddApps: () -> Unit = {},
    locked: Boolean = false, onLock: (Boolean) -> Unit = {}, copyFrom: String? = null, onCopy: () -> Unit = {}) {
    Box(Modifier.fillMaxSize()) {
        Scrim(.35f, onDismiss)
        Anchored(Rect.Zero) {
            MenuCard {
                Text("Fold Launcher Pro", color = Holo.Muted, fontSize = 12.sp, letterSpacing = 2.sp, modifier = Modifier.padding(10.dp))
                if (!locked) {
                    MenuRow(Icons.Rounded.AddToHomeScreen, "Add apps") { onDismiss(); onAddApps() }
                    MenuRow(Icons.Rounded.Checklist, "Select apps") { onDismiss(); onSelect() }
                    MenuRow(Icons.Rounded.AddBox, "Add page") { onDismiss(); onAddPage() }
                    if (onDeletePage != null) MenuRow(Icons.Rounded.DeleteSweep, "Delete this page", Holo.Pink) { onDismiss(); onDeletePage() }
                    if (copyFrom != null) MenuRow(Icons.Rounded.ContentCopy, "Copy layout from $copyFrom") { onDismiss(); onCopy() }
                }
                MenuRow(if (locked) Icons.Rounded.LockOpen else Icons.Rounded.Lock, if (locked) "Unlock layout" else "Lock layout") {
                    onDismiss(); onLock(!locked)
                }
                if (onWidgets != null) MenuRow(Icons.Rounded.Widgets, "Add a widget") { onDismiss(); onWidgets() }
                if (onToday != null) MenuRow(Icons.Rounded.ViewDay, "Edit “Today”") { onDismiss(); onToday() }
                MenuRow(Icons.Rounded.Apps, "All apps") { onDismiss(); onApps() }
                MenuRow(Icons.Rounded.Tune, "Fold Launcher Pro settings") { onDismiss(); onSettings() }
            }
        }
    }
}

/** An open folder: its name (editable) and its apps. */
@Composable
fun FolderPanel(folderId: String, screen: Screen, launcher: Launcher, startRenaming: Boolean, onDismiss: () -> Unit,
    onOpen: (AppInfo, Rect) -> Unit, onHold: (MenuRequest) -> Unit) {
    val folder = launcher.layout.folders[folderId] ?: run { onDismiss(); return }
    var title by remember(folderId) { mutableStateOf(folder.title) }
    var renaming by remember(folderId) { mutableStateOf(startRenaming) }
    val apps = launcher.folderApps(folderId)
    fun save() { if (title.isNotBlank() && title != folder.title) launcher.update { l ->
        l.copy(folders = l.folders + (folderId to folder.copy(title = title.trim()))) } }
    Box(Modifier.fillMaxSize()) {
        Scrim(.5f) { save(); onDismiss() }
        Column(Modifier.align(Alignment.Center).widthIn(max = 380.dp).fillMaxWidth().padding(24.dp)
            .halo(Holo.Violet.copy(alpha = .12f), 30.dp).clip(RoundedCornerShape(34.dp)).background(Holo.Panel.copy(alpha = .9f))
            .border(1.dp, Holo.edgeBrush, RoundedCornerShape(34.dp))
            .clickable(remember { MutableInteractionSource() }, null) {}.padding(20.dp)) {
            if (renaming) BasicTextField(title, { title = it }, Modifier.fillMaxWidth().padding(bottom = 16.dp),
                singleLine = true, textStyle = TextStyle(color = Holo.Ink, fontSize = 24.sp, fontWeight = FontWeight.Light),
                cursorBrush = SolidColor(Holo.Violet))
            else Box(Modifier.fillMaxWidth().padding(bottom = 16.dp).clickable { renaming = true }) {
                IridescentText(title, 26.sp, letterSpacing = 0.sp, weight = FontWeight.Light)
            }
            apps.chunked(4).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    row.forEach { app ->
                        var bounds by remember { mutableStateOf(Rect.Zero) }
                        Column(Modifier.weight(1f)
                            .then(Modifier.onBounds { bounds = it })
                            .clip(RoundedCornerShape(18.dp))
                            .combinedClick(onClick = { save(); onOpen(app, bounds) },
                                onLongClick = { onHold(MenuRequest(app.id, bounds, Origin.InFolder(folderId, screen))) })
                            .padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            AppIcon(app, 56.dp)
                            IconLabel(app.label)
                        }
                    }
                    repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
            Text("Long-press an app to take it out of the folder.", color = Holo.Muted, fontSize = 12.sp,
                modifier = Modifier.padding(top = 4.dp))
        }
    }
}

fun Modifier.onBounds(block: (Rect) -> Unit): Modifier = onGloballyPositioned { block(it.boundsInRoot()) }

fun Modifier.combinedClick(onClick: () -> Unit, onLongClick: () -> Unit): Modifier =
    combinedClickable(onClick = onClick, onLongClick = onLongClick)

/** Folder sizes on the home page: columns × rows (0 columns = the whole width). */
private val FOLDER_SIZES = listOf(Triple(1, 1, "Icon"), Triple(2, 1, "2×1"), Triple(2, 2, "2×2"), Triple(3, 2, "3×2"),
    Triple(0, 1, "Full width ×1"), Triple(0, 2, "Full width ×2"), Triple(0, 3, "Full width ×3"), Triple(0, 4, "Full width ×4"))

/** "App 120 MB · data 40 MB · cache 12 MB" (needs usage access). Clearing the cache is done in the system page. */
fun appStorage(context: android.content.Context, app: AppInfo): String? = runCatching {
    if (!hasUsageAccess(context)) return null
    val manager = context.getSystemService(android.app.usage.StorageStatsManager::class.java)
    val stats = manager.queryStatsForPackage(android.os.storage.StorageManager.UUID_DEFAULT, app.packageName, app.user)
    fun mb(bytes: Long) = if (bytes >= 1_000_000_000) "%.1f GB".format(java.util.Locale.US, bytes / 1e9) else "${bytes / 1_000_000} MB"
    "App ${mb(stats.appBytes)} · data ${mb(stats.dataBytes - stats.cacheBytes)} · cache ${mb(stats.cacheBytes)}"
}.getOrNull()
