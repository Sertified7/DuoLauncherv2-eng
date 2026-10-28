package com.yuksel.pli

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.abs

/** What the activity provides to the UI. */
interface PliHost {
    val launcher: Launcher
    val widgets: Widgets
    fun launch(app: AppInfo, bounds: Rect?)
    fun openPackage(packageName: String)
    fun appInfo(app: AppInfo)
    fun uninstall(app: AppInfo)
    fun requestDefaultHome()
    fun requestPermission(permission: String)
    fun openNotificationAccess()
    fun expandNotifications()
    /** True when the Google app is installed (its feed is Discover). */
    val hasGoogleApp: Boolean
    fun openDiscover()
    fun openAccessibilitySettings()
    fun exportBackup()
    fun importBackup()
    /** Asks for the fingerprint (or the screen lock), then runs [onSuccess]. */
    fun authenticate(title: String, onSuccess: () -> Unit)
    /** Lets the user pick a picture; [onPicked] gets its address. */
    fun pickImage(onPicked: (android.net.Uri) -> Unit)
}

/** Counters the activity bumps so the UI can react. */
class HostSignals {
    var homePresses by mutableIntStateOf(0)
    var resumes by mutableIntStateOf(0)
    var isDefaultHome by mutableStateOf(true)
}

private enum class Panel { NONE, SEARCH, DRAWER, SETTINGS, WIDGETS, HOME_MENU, ADD_APPS }

/**
 * Swipe up / down on the home screen. It watches the touch before the icons and pages do
 * (initial pass), so it works over icons too; a horizontal move is left to the pager.
 */
fun Modifier.homeSwipes(enabled: Boolean, blocked: () -> Boolean, onUp: () -> Unit, onDown: (Float) -> Unit,
    onTwoFinger: (up: Boolean) -> Unit = {}): Modifier = pointerInput(enabled) {
    if (!enabled) return@pointerInput
    val threshold = 44.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var total = Offset.Zero
        var twoFingers = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.filter { it.pressed }
            if (pressed.isEmpty() || blocked()) break
            if (pressed.size >= 2) twoFingers = true
            val change = pressed.firstOrNull { it.id == down.id } ?: pressed.first()
            total += if (twoFingers) pressed.map { it.positionChange() }.reduce { a, b -> a + b } / pressed.size.toFloat()
                else change.positionChange()
            if (abs(total.x) > threshold && abs(total.x) > abs(total.y)) break
            if (abs(total.y) > threshold && abs(total.y) > abs(total.x) * 1.3f) {
                pressed.forEach { it.consume() }
                when {
                    twoFingers -> onTwoFinger(total.y < 0)
                    total.y < 0 -> onUp()
                    else -> onDown(down.position.y / size.height.coerceAtLeast(1))
                }
                break
            }
        }
    }
}

@Composable
fun PliApp(host: PliHost, signals: HostSignals) {
    val launcher = host.launcher
    val context = LocalContext.current
    val badges by BadgeService.badges.collectAsState()
    val today = remember { TodayState() }
    var weatherTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(signals.resumes, weatherTick, launcher.loaded) {
        if (!launcher.loaded) return@LaunchedEffect
        var force = weatherTick > 0
        while (true) {
            today.refresh(context, launcher.settings, force)
            force = false
            delay(60_000)
        }
    }
    var panel by remember { mutableStateOf(Panel.NONE) }
    var menu by remember { mutableStateOf<MenuRequest?>(null) }
    var folder by remember { mutableStateOf<Pair<String, Screen>?>(null) }
    var renameFolder by remember { mutableStateOf(false) }
    var todayEditing by remember { mutableStateOf(false) }
    var showToday by remember { mutableIntStateOf(0) }
    var screen by remember { mutableStateOf(Screen.COVER) }
    val drag = remember { DragState() }
    var article by remember { mutableStateOf<Article?>(null) }
    var homeMenu by remember { mutableStateOf(Triple(Screen.COVER, 0, 1)) }
    LaunchedEffect(launcher.iconReloads) { if (launcher.iconReloads > 0) launcher.reloadApps() }


    val selection = remember { Selection() }
    fun closeAll() { panel = Panel.NONE; menu = null; folder = null; todayEditing = false; drag.clear(); selection.clear() }
    LaunchedEffect(signals.homePresses) { if (signals.homePresses > 0) { closeAll(); article = null } }
    BackHandler(panel != Panel.NONE || menu != null || folder != null || todayEditing) {
        when {
            menu != null -> menu = null
            folder != null -> folder = null
            panel != Panel.NONE -> panel = Panel.NONE
            else -> todayEditing = false
        }
    }

    val open: (AppInfo, Rect?) -> Unit = { app, bounds ->
        val go = {
            launcher.recordLaunch(app, screen)
            host.launch(app, bounds?.takeIf { it != Rect.Zero })
            if (panel == Panel.SEARCH || panel == Panel.DRAWER) panel = Panel.NONE
            folder = null
        }
        if (app.id in launcher.layout.protectedApps) host.authenticate("Ouvrir ${app.label}", go) else go()
    }
    var renameApp by remember { mutableStateOf<AppInfo?>(null) }
    var iconApp by remember { mutableStateOf<AppInfo?>(null) }
    drag.locked = launcher.settings.lockLayout
    // Swipe up on an app icon opens its favourite shortcut.
    drag.onIconSwipeUp = { id ->
        launcher.byId[id]?.let { app ->
            val shortcuts = loadAppShortcuts(context, app)
            val chosen = launcher.layout.swipeShortcuts[id]?.let { fav -> shortcuts.firstOrNull { it.info.id == fav } } ?: shortcuts.firstOrNull()
            if (chosen != null && !(app.id in launcher.layout.protectedApps)) startAppShortcut(context, chosen) else open(app, null)
        }
    }
    val showMenu: (String, Rect, DragSource) -> Unit = { id, bounds, source ->
        menu = MenuRequest(id, bounds, when (source) {
            is DragSource.Home -> Origin.Home(source.screen)
            is DragSource.Dock -> Origin.Dock
            is DragSource.InFolder -> Origin.InFolder(source.folderId, screen)
        })
    }
    fun runGesture(action: String) {
        when {
            action == "lock" -> if (!PliAccessibility.lock()) host.openAccessibilitySettings()
            action == "notifications" -> host.expandNotifications()
            action == "search" -> panel = Panel.SEARCH
            action == "drawer" -> panel = Panel.DRAWER
            action.startsWith("app:") -> launcher.byId[action.removePrefix("app:")]?.let { open(it, null) }
        }
    }
    val actions = HomeActions(
        open = open, openFolder = { folder = it to screen; renameFolder = false },
        menu = showMenu, closeMenu = { menu = null },
        emptyHold = { sc, page, cap -> homeMenu = Triple(sc, page, cap); panel = Panel.HOME_MENU }, search = { panel = Panel.SEARCH }, drawer = { panel = Panel.DRAWER },
        notifications = { host.expandNotifications() },
        todayEditing = { todayEditing }, setTodayEditing = { todayEditing = it },
        addWidget = { panel = Panel.WIDGETS }, settings = { panel = Panel.SETTINGS },
        gesture = ::runGesture, read = { article = it },
    )

    BackHandler(selection.active) { selection.clear() }
    LaunchedEffect(launcher.settings.lockLayout) { if (launcher.settings.lockLayout) selection.clear() }
    CompositionLocalProvider(LocalBubbles provides launcher.settings.bubbles, LocalIconShape provides launcher.settings.iconShape, LocalBadges provides badges, LocalSelection provides selection) {
        Box(Modifier.fillMaxSize()) {
            HoloBackground(launcher.settings.phoneWallpaper, launcher.settings.parallax)
            // Home ignores the keyboard so the grid never changes size while typing.
            if (launcher.loaded) BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.displayCutout))) {
                val wide = maxWidth >= 600.dp
                val current = if (wide) Screen.INNER else Screen.COVER
                SideEffect { if (screen != current) { screen = current; drag.clear() } }
                val swipesOn = panel == Panel.NONE && menu == null && folder == null && !drag.active && !selection.active
                if (wide) key("inner", launcher.settings.discover) { InnerHome(host, signals, today, drag, actions, swipesOn, showToday) }
                else key("cover", launcher.settings.discover) { CoverHome(host, signals, today, drag, actions, swipesOn, showToday) }
            }
            // Bin at the top while dragging: a full-width bar over the clock, drop an icon on it to take it off the home screen.
            if (drag.active) TopBar(Modifier.onGloballyPositioned { drag.trashRect = it.boundsInRoot() },
                highlight = drag.spot == DropSpot.Remove) {
                val over = drag.spot == DropSpot.Remove
                Icon(Icons.Rounded.Delete, null, tint = if (over) Holo.Badge else Holo.Ink)
                Text("Retirer de l’accueil", color = if (over) Holo.Badge else Holo.Ink, fontSize = 15.sp, modifier = Modifier.padding(start = 10.dp))
            } else SideEffect { drag.trashRect = Rect.Zero }
            DragGhost(launcher, drag, if (screen == Screen.INNER) 62.dp else 60.dp)
            // Selection mode: pick several icons, then remove them together.
            if (selection.active) TopBar(Modifier, highlight = false) {
                val n = selection.ids.size
                Text(if (n == 0) "Touche les applis à retirer" else "$n sélectionnée" + if (n > 1) "s" else "",
                    color = Holo.Ink, fontSize = 15.sp, modifier = Modifier.padding(end = 16.dp))
                if (n > 0) Row(Modifier.padding(end = 10.dp).clip(RoundedCornerShape(50)).background(Holo.Badge.copy(alpha = .9f))
                    .clickable {
                        val ids = selection.ids.toSet()
                        launcher.edit("${ids.size} retirée" + if (ids.size > 1) "s" else "") { LayoutEdits.removeMany(it, screen, ids) }
                        selection.clear()
                    }.padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Delete, null, tint = Color(0xFF1A0512), modifier = Modifier.size(18.dp))
                    Text("Retirer", color = Color(0xFF1A0512), fontSize = 14.sp, modifier = Modifier.padding(start = 6.dp))
                }
                PillButton("Terminé", onClick = selection::clear)
            }

            FullPanel(article != null) { m -> article?.let { ArticleReader(it, m) { article = null } } }
            FullPanel(panel == Panel.SEARCH) {
                key(signals.resumes) {
                    SearchPanel(launcher, it, open, onHold = { app, r -> menu = MenuRequest(app.id, r, Origin.Drawer) },
                        onClose = { panel = Panel.NONE }, onRequestContacts = { host.requestPermission(android.Manifest.permission.READ_CONTACTS) },
                        onPliSetting = { panel = Panel.SETTINGS })
                }
            }
            FullPanel(panel == Panel.ADD_APPS) { m ->
                val (addScreen, page, cap) = homeMenu
                AddAppsPanel(launcher, addScreen, m, onAdd = { ids ->
                    launcher.edit("${ids.size} ajoutée" + if (ids.size > 1) "s" else "") { LayoutEdits.addMany(it, addScreen, ids, page * cap) }
                    panel = Panel.NONE
                }, onClose = { panel = Panel.NONE })
            }
            FullPanel(panel == Panel.DRAWER) {
                AppDrawer(launcher, it, { app, r -> open(app, r) }, onHold = { app, r -> menu = MenuRequest(app.id, r, Origin.Drawer) },
                    onClose = { panel = Panel.NONE })
            }
            FullPanel(panel == Panel.SETTINGS) {
                run {
                    SettingsPanel(launcher, signals.isDefaultHome, today.notificationsAllowed, it,
                        onClose = { panel = Panel.NONE }, onMakeDefault = host::requestDefaultHome,
                        onNotificationAccess = host::openNotificationAccess,
                        onLocation = { host.requestPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION); weatherTick++ },
                        onWeatherChanged = { weatherTick++ },
                        onExport = host::exportBackup, onImport = host::importBackup,
                        accessibilityOn = PliAccessibility.enabled(context), onAccessibility = host::openAccessibilitySettings)
                }
            }
            FullPanel(panel == Panel.WIDGETS) {
                WidgetPicker(launcher, host.widgets, it,
                    onPickBuiltIn = { kind ->
                        launcher.update { l -> l.copy(today = l.today + TodayItem(kind, kind)) }
                        panel = Panel.NONE; showToday++
                    },
                    onPickAndroid = { info ->
                        host.widgets.begin(info) { id, provider ->
                            launcher.update { l -> l.copy(today = l.today + TodayItem("w$id", TodayItem.KIND_ANDROID, id, defaultWidgetHeight(context, provider))) }
                            showToday++
                        }
                        panel = Panel.NONE
                    },
                    onClose = { panel = Panel.NONE })
            }
            if (panel == Panel.HOME_MENU) {
                val (menuScreen, page, cap) = homeMenu
                val list = launcher.layout.home(menuScreen)
                val pageEmpty = (page * cap until (page + 1) * cap).all { list.getOrElse(it) { "" }.isEmpty() }
                HomeMenu(onDismiss = { panel = Panel.NONE },
                    onWidgets = if (menuScreen == Screen.INNER) ({ panel = Panel.WIDGETS }) else null,
                    onSettings = { panel = Panel.SETTINGS }, onApps = { panel = Panel.DRAWER },
                    onToday = if (menuScreen == Screen.INNER) ({ todayEditing = true }) else null,
                    onAddPage = { launcher.update { LayoutEdits.addPage(it, menuScreen) } },
                    onSelect = { selection.start(null) },
                    onAddApps = { panel = Panel.ADD_APPS },
                    onDeletePage = if (pageEmpty && launcher.layout.pages(menuScreen) > 1)
                        ({ launcher.edit("Page supprimée") { LayoutEdits.deletePage(it, menuScreen, page, cap) } }) else null,
                    locked = launcher.settings.lockLayout, onLock = { v -> launcher.updateSettings { it.copy(lockLayout = v) } },
                    copyFrom = if (menuScreen == Screen.INNER) "l’écran externe" else "l’écran interne",
                    onCopy = {
                        val other = if (menuScreen == Screen.INNER) Screen.COVER else Screen.INNER
                        launcher.edit("Disposition copiée") { LayoutEdits.copyHome(it, other, menuScreen) }
                    })
            }
            folder?.let { (id, s) ->
                FolderPanel(id, s, launcher, renameFolder, onDismiss = { folder = null }, onOpen = { app, r -> open(app, r) },
                    onHold = { menu = it })
            }
            menu?.let { request ->
                AppMenu(request, launcher, screen, onDismiss = { menu = null },
                    onRenameFolder = { id -> folder = id to screen; renameFolder = true },
                    onUninstall = host::uninstall, onInfo = host::appInfo, onOpenApp = { open(it, null) },
                    onSelect = { selection.start(it) }, onRename = { renameApp = it }, onChangeIcon = { iconApp = it })
            }
            renameApp?.let { app -> RenameDialog(app, launcher) { renameApp = null } }
            FullPanel(iconApp != null) { m -> iconApp?.let { app -> IconPicker(app, launcher, host, m) { iconApp = null } } }
            // "Annuler" for a few seconds after a change.
            val undo = launcher.undo
            LaunchedEffect(launcher.undoStamp) { if (launcher.undo != null) { delay(6000); launcher.dismissUndo() } }
            if (undo != null && panel == Panel.NONE && menu == null) Box(Modifier.fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars).padding(bottom = 110.dp), contentAlignment = Alignment.BottomCenter) {
                Row(Modifier.halo(Holo.Violet.copy(alpha = .2f), 12.dp).glass(RoundedCornerShape(50), .16f)
                    .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(undo.first, color = Holo.Ink, fontSize = 14.sp, modifier = Modifier.padding(end = 12.dp))
                    PillButton("Annuler", highlighted = true) { launcher.undoLast() }
                }
            }
        }
    }
}

/** Callbacks shared by both screens. */
class HomeActions(
    val open: (AppInfo, Rect?) -> Unit,
    val openFolder: (String) -> Unit,
    val menu: (String, Rect, DragSource) -> Unit,
    val closeMenu: () -> Unit,
    val emptyHold: (Screen, Int, Int) -> Unit,
    val search: () -> Unit,
    val drawer: () -> Unit,
    val notifications: () -> Unit,
    val todayEditing: () -> Boolean,
    val setTodayEditing: (Boolean) -> Unit,
    val addWidget: () -> Unit,
    val settings: () -> Unit,
    val gesture: (String) -> Unit,
    val read: (Article) -> Unit,
)

/** A full-screen frosted panel that slides up (search, all apps, settings, widgets). */
@Composable
private fun FullPanel(visible: Boolean, content: @Composable (Modifier) -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn() + slideInVertically { it / 8 }, exit = fadeOut() + slideOutVertically { it / 8 }) {
        Box(Modifier.fillMaxSize().background(Holo.Base.copy(alpha = .985f))
            .clickable(remember { MutableInteractionSource() }, null) {}) {
            Box(Modifier.fillMaxSize().background(Brush.radialGradient(listOf(Holo.Violet.copy(alpha = .10f), Color.Transparent),
                center = Offset(0f, 0f), radius = 1400f)))
            Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing), contentAlignment = Alignment.TopCenter) {
                content(Modifier.widthIn(max = 760.dp).fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp))
            }
        }
    }
}

@Composable
private fun DefaultHomeBanner(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.glass(RoundedCornerShape(50), .08f).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Home, null, tint = Holo.Violet)
        Text("Faire de Pli l’appli d’accueil", color = Holo.Ink, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp))
    }
}

/** Swipe up: on an app icon, its favourite shortcut; elsewhere, all apps. */
private fun swipeUp(drag: DragState, actions: HomeActions) {
    val id = drag.pressedId
    if (id != null && !HomeLayout.isFolder(id)) drag.onIconSwipeUp(id) else actions.drawer()
}

private fun swipeDown(host: PliHost, actions: HomeActions, fraction: Float) {
    if (host.launcher.settings.swipeDownNotifications && fraction < .15f) actions.notifications() else actions.search()
}

@Composable
private fun HomePressScroll(signals: HostSignals, pager: PagerState, firstHome: Int) {
    LaunchedEffect(signals.homePresses) { if (signals.homePresses > 0 && pager.currentPage != firstHome) pager.animateScrollToPage(firstHome) }
}

/** Cover screen: Discover and "Aujourd'hui" on the left, big clock, home pages, dock with the search orb. */
@Composable
private fun CoverHome(host: PliHost, signals: HostSignals, today: TodayState, drag: DragState, actions: HomeActions,
    swipesOn: Boolean, showToday: Int) {
    val launcher = host.launcher
    val items = launcher.visibleHome(Screen.COVER)
    val nowPlaying by MediaWatcher.nowPlaying.collectAsState()
    val discover = launcher.settings.discover
    val lead = if (discover) 1 else 0
    var homePages by remember { mutableIntStateOf(1) }
    val pager = rememberPagerState(initialPage = lead) { lead + homePages + if (drag.active) 1 else 0 }
    HomePressScroll(signals, pager, lead)
    Column(Modifier.fillMaxSize().homeSwipes(swipesOn, { drag.source != null || pager.currentPage < lead }, { swipeUp(drag, actions) }, { swipeDown(host, actions, it) }, { up -> actions.gesture(if (up) launcher.settings.twoFingerUp else launcher.settings.twoFingerDown) })) {
        // The clock sits above the home pages only: it slides away with them when the Actus page comes in.
        val density = LocalDensity.current
        var headerHeight by remember { mutableStateOf(206.dp) }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val s = launcher.settings
            val metrics = gridMetrics(maxWidth, maxHeight - headerHeight - 18.dp, s.labels, wide = false, s.coverColumns, s.coverRows)
            val pages = maxOf(launcher.layout.coverPages, (items.size + metrics.capacity - 1) / metrics.capacity, 1)
            SideEffect { homePages = pages; launcher.useGrid(Screen.COVER, Grid(metrics.columns, metrics.rows)) }
            Column(Modifier.fillMaxSize()) {
                HomePages(Screen.COVER, launcher, items, metrics, pager, lead, drag, s.labels, Modifier.weight(1f),
                    onOpenApp = { a, r -> actions.open(a, r) }, onOpenFolder = actions.openFolder,
                    onMenu = actions.menu, onCloseMenu = actions.closeMenu, onEmptyHold = { actions.emptyHold(Screen.COVER, it, metrics.capacity) },
                    onEmptyDoubleTap = { actions.gesture(launcher.settings.doubleTap) }, homePages = pages, topPadding = headerHeight,
                    leadingPage = { NewsPage(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), actions.read) })
                PageDots(pager, pager.pageCount, lead, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
            val position = pager.currentPage + pager.currentPageOffsetFraction
            val shown = if (lead == 0) 1f else (position - (lead - 1)).coerceIn(0f, 1f)
            if (shown > 0f) {
                val widthPx = with(density) { maxWidth.toPx() }
                ClockHeader(today, 84.sp, compact = false,
                    Modifier.graphicsLayer { alpha = shown; translationX = (1f - shown) * widthPx }
                        .onSizeChanged { with(density) { headerHeight = it.height.toDp() + 10.dp } }
                        .padding(start = 22.dp, end = 22.dp, top = 10.dp), onWeather = {},
                    trailing = if (launcher.settings.headerSearch != "none") ({ HeaderSearch(launcher.settings.headerSearch, actions.search) }) else null,
                    nowPlaying = nowPlaying, onOpenPlaying = { host.openPackage(it) })
            }
        }
        if (!signals.isDefaultHome) DefaultHomeBanner(host::requestDefaultHome, Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp))
        Dock(launcher, Screen.COVER, drag, 54.dp, Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
            onOpenApp = { a, r -> actions.open(a, r) }, onMenu = actions.menu, onCloseMenu = actions.closeMenu, onSearch = null)
    }
}

/** Inner screen: "Aujourd'hui" on the left half, home on the right half (split on the hinge), wide dock below both. */
@Composable
private fun InnerHome(host: PliHost, signals: HostSignals, today: TodayState, drag: DragState, actions: HomeActions,
    swipesOn: Boolean, @Suppress("UNUSED_PARAMETER") showToday: Int) {
    val launcher = host.launcher
    val items = launcher.visibleHome(Screen.INNER)
    val discover = launcher.settings.discover
    val lead = if (discover) 1 else 0
    var homePages by remember { mutableIntStateOf(1) }
    val pager = rememberPagerState(initialPage = lead) { lead + homePages + if (drag.active) 1 else 0 }
    HomePressScroll(signals, pager, lead)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            TodayColumn(launcher, host.widgets, today, actions.todayEditing(),
                Modifier.weight(1f).fillMaxHeight().padding(start = 24.dp, end = 22.dp, top = 14.dp),
                onEditing = actions.setTodayEditing, onAddWidget = actions.addWidget, onPermission = host::requestPermission,
                onNotificationAccess = host::openNotificationAccess, onSettings = actions.settings, onOpenPackage = host::openPackage)
            Box(Modifier.width(2.dp).fillMaxHeight(.72f).align(Alignment.CenterVertically)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Holo.Violet.copy(alpha = .35f), Color.Transparent))))
            Column(Modifier.weight(1f).fillMaxHeight()
                .homeSwipes(swipesOn, { drag.source != null || pager.currentPage < lead }, { swipeUp(drag, actions) }, { swipeDown(host, actions, it) }, { up -> actions.gesture(if (up) launcher.settings.twoFingerUp else launcher.settings.twoFingerDown) })) {
                ClockHeader(today, 66.sp, compact = true, Modifier.padding(start = 26.dp, end = 22.dp, top = 14.dp), onWeather = {},
                    trailing = if (launcher.settings.headerSearch != "none") ({ HeaderSearch(launcher.settings.headerSearch, actions.search) }) else null)
                Spacer(Modifier.height(16.dp))
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val s = launcher.settings
                    val metrics = gridMetrics(maxWidth - 16.dp, maxHeight - 18.dp, s.labels, wide = true, s.innerColumns, s.innerRows)
                    val pages = maxOf(launcher.layout.innerPages, (items.size + metrics.capacity - 1) / metrics.capacity, 1)
                    SideEffect { homePages = pages; launcher.useGrid(Screen.INNER, Grid(metrics.columns, metrics.rows)) }
                    Column(Modifier.fillMaxSize()) {
                        HomePages(Screen.INNER, launcher, items, metrics, pager, lead, drag, s.labels, Modifier.weight(1f).padding(horizontal = 8.dp),
                            onOpenApp = { a, r -> actions.open(a, r) }, onOpenFolder = actions.openFolder,
                            onMenu = actions.menu, onCloseMenu = actions.closeMenu, onEmptyHold = { actions.emptyHold(Screen.INNER, it, metrics.capacity) },
                            onEmptyDoubleTap = { actions.gesture(launcher.settings.doubleTap) }, homePages = pages,
                            leadingPage = { NewsPage(Modifier.fillMaxSize().padding(horizontal = 8.dp), actions.read) })
                        PageDots(pager, pager.pageCount, lead, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }
            }
        }
        if (!signals.isDefaultHome) DefaultHomeBanner(host::requestDefaultHome, Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp))
        val context = LocalContext.current
        val recents = remember(launcher.lastLaunch, launcher.layout.dock, launcher.layout.dockExtra, launcher.settings.recentInDock, launcher.byId) {
            UsageLog.recent(context, launcher.byId, launcher.settings.recentInDock, launcher.layout.dockOf(Screen.INNER).toSet() + launcher.layout.hidden)
        }
        val dockCount = (launcher.visibleDock(Screen.INNER).size + recents.size).coerceAtLeast(3)
        Dock(launcher, Screen.INNER, drag, 58.dp,
            Modifier.align(Alignment.CenterHorizontally).widthIn(max = (dockCount * 78 + 40).dp).fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, bottom = 12.dp, top = 6.dp),
            onOpenApp = { a, r -> actions.open(a, r) }, onMenu = actions.menu, onCloseMenu = actions.closeMenu, onSearch = null,
            recents = recents)
    }
}

/** A full-width bar across the top of the screen (status bar included), over the clock. */
@Composable
private fun TopBar(modifier: Modifier, highlight: Boolean, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(Holo.Panel, Holo.Panel.copy(alpha = .94f))))
        .then(if (highlight) Modifier.background(Holo.Badge.copy(alpha = .18f)) else Modifier)
        .windowInsetsPadding(WindowInsets.systemBars.only(androidx.compose.foundation.layout.WindowInsetsSides.Top))
        .then(modifier).height(76.dp), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) { content() }
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(1.dp)
            .background(Brush.horizontalGradient(Holo.Iridescent.map { it.copy(alpha = .5f) })))
    }
}
