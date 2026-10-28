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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalContext
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
}

/** Counters the activity bumps so the UI can react. */
class HostSignals {
    var homePresses by mutableIntStateOf(0)
    var resumes by mutableIntStateOf(0)
    var isDefaultHome by mutableStateOf(true)
}

private enum class Panel { NONE, SEARCH, DRAWER, SETTINGS, WIDGETS, HOME_MENU }

/** Swipe up / down on the home screen (only when nothing below used the gesture). */
fun Modifier.homeSwipes(enabled: Boolean, onUp: () -> Unit, onDown: (Float) -> Unit): Modifier = pointerInput(enabled) {
    if (!enabled) return@pointerInput
    val threshold = 56.dp.toPx()
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        var total = Offset.Zero
        while (true) {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            if (!change.pressed || change.isConsumed) break
            total += change.positionChange()
            if (abs(total.y) > threshold && abs(total.y) > abs(total.x) * 1.4f) {
                if (total.y < 0) onUp() else onDown(down.position.y / size.height.coerceAtLeast(1))
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

    fun closeAll() { panel = Panel.NONE; menu = null; folder = null; todayEditing = false; drag.clear() }
    LaunchedEffect(signals.homePresses) { if (signals.homePresses > 0) closeAll() }
    BackHandler(panel != Panel.NONE || menu != null || folder != null || todayEditing) {
        when {
            menu != null -> menu = null
            folder != null -> folder = null
            panel != Panel.NONE -> panel = Panel.NONE
            else -> todayEditing = false
        }
    }

    val open: (AppInfo, Rect?) -> Unit = { app, bounds ->
        launcher.recordLaunch(app)
        host.launch(app, bounds?.takeIf { it != Rect.Zero })
        if (panel == Panel.SEARCH || panel == Panel.DRAWER) panel = Panel.NONE
        folder = null
    }
    val showMenu: (String, Rect, DragSource) -> Unit = { id, bounds, source ->
        menu = MenuRequest(id, bounds, when (source) {
            is DragSource.Home -> Origin.Home(source.screen)
            is DragSource.Dock -> Origin.Dock
            is DragSource.InFolder -> Origin.InFolder(source.folderId, screen)
        })
    }
    val actions = HomeActions(
        open = open, openFolder = { folder = it to screen; renameFolder = false },
        menu = showMenu, closeMenu = { menu = null },
        emptyHold = { panel = Panel.HOME_MENU }, search = { panel = Panel.SEARCH }, drawer = { panel = Panel.DRAWER },
        notifications = { host.expandNotifications() },
        todayEditing = { todayEditing }, setTodayEditing = { todayEditing = it },
        addWidget = { panel = Panel.WIDGETS }, settings = { panel = Panel.SETTINGS },
    )

    CompositionLocalProvider(LocalBubbles provides launcher.settings.bubbles, LocalBadges provides badges) {
        Box(Modifier.fillMaxSize()) {
            HoloBackground(launcher.settings.phoneWallpaper, launcher.settings.parallax)
            if (launcher.loaded) BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                val wide = maxWidth >= 600.dp
                val current = if (wide) Screen.INNER else Screen.COVER
                SideEffect { if (screen != current) { screen = current; drag.clear() } }
                val swipesOn = panel == Panel.NONE && menu == null && folder == null && !drag.active
                if (wide) key("inner") { InnerHome(host, signals, today, drag, actions, swipesOn, showToday) }
                else key("cover") { CoverHome(host, signals, today, drag, actions, swipesOn, showToday) }
            }
            DragGhost(launcher, drag, if (screen == Screen.INNER) 62.dp else 60.dp)

            FullPanel(panel == Panel.SEARCH) {
                key(signals.resumes) {
                    SearchPanel(launcher, it, open, onHold = { app, r -> menu = MenuRequest(app.id, r, Origin.Drawer) },
                        onClose = { panel = Panel.NONE }, onRequestContacts = { host.requestPermission(android.Manifest.permission.READ_CONTACTS) })
                }
            }
            FullPanel(panel == Panel.DRAWER) {
                AppDrawer(launcher, it, { app, r -> open(app, r) }, onHold = { app, r -> menu = MenuRequest(app.id, r, Origin.Drawer) },
                    onClose = { panel = Panel.NONE })
            }
            FullPanel(panel == Panel.SETTINGS) {
                key(signals.resumes) {
                    SettingsPanel(launcher, signals.isDefaultHome, today.notificationsAllowed, it,
                        onClose = { panel = Panel.NONE }, onMakeDefault = host::requestDefaultHome,
                        onNotificationAccess = host::openNotificationAccess,
                        onLocation = { host.requestPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION); weatherTick++ },
                        onWeatherChanged = { weatherTick++ })
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
            if (panel == Panel.HOME_MENU) HomeMenu(onDismiss = { panel = Panel.NONE }, onWidgets = { panel = Panel.WIDGETS },
                onSettings = { panel = Panel.SETTINGS }, onApps = { panel = Panel.DRAWER },
                onToday = { todayEditing = true; showToday++ })
            folder?.let { (id, s) ->
                FolderPanel(id, s, launcher, renameFolder, onDismiss = { folder = null }, onOpen = { app, r -> open(app, r) },
                    onHold = { menu = it })
            }
            menu?.let { request ->
                AppMenu(request, launcher, screen, onDismiss = { menu = null },
                    onRenameFolder = { id -> folder = id to screen; renameFolder = true },
                    onUninstall = host::uninstall, onInfo = host::appInfo)
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
    val emptyHold: () -> Unit,
    val search: () -> Unit,
    val drawer: () -> Unit,
    val notifications: () -> Unit,
    val todayEditing: () -> Boolean,
    val setTodayEditing: (Boolean) -> Unit,
    val addWidget: () -> Unit,
    val settings: () -> Unit,
)

/** A full-screen frosted panel that slides up (search, all apps, settings, widgets). */
@Composable
private fun FullPanel(visible: Boolean, content: @Composable (Modifier) -> Unit) {
    AnimatedVisibility(visible, enter = fadeIn() + slideInVertically { it / 8 }, exit = fadeOut() + slideOutVertically { it / 8 }) {
        Box(Modifier.fillMaxSize().background(Holo.Base.copy(alpha = .93f))
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

private fun swipeDown(host: PliHost, actions: HomeActions, fraction: Float) {
    if (host.launcher.settings.swipeDownNotifications && fraction < .3f) actions.notifications() else actions.search()
}

@Composable
private fun HomePressScroll(signals: HostSignals, pager: PagerState, firstHome: Int) {
    LaunchedEffect(signals.homePresses) { if (signals.homePresses > 0 && pager.currentPage != firstHome) pager.animateScrollToPage(firstHome) }
}

/** Cover screen: big clock, the "Aujourd'hui" page on the left, home pages, dock with the search orb. */
@Composable
private fun CoverHome(host: PliHost, signals: HostSignals, today: TodayState, drag: DragState, actions: HomeActions,
    swipesOn: Boolean, showToday: Int) {
    val launcher = host.launcher
    val items = launcher.visibleHome(Screen.COVER)
    val nowPlaying by MediaWatcher.nowPlaying.collectAsState()
    Column(Modifier.fillMaxSize().homeSwipes(swipesOn, actions.drawer) { swipeDown(host, actions, it) }) {
        ClockHeader(today, 84.sp, compact = false, Modifier.padding(start = 22.dp, end = 22.dp, top = 10.dp), onWeather = {})
        Spacer(Modifier.height(10.dp))
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val metrics = gridMetrics(maxWidth, maxHeight, launcher.settings.labels, wide = false)
            val homePages = maxOf(1, (items.size + metrics.capacity - 1) / metrics.capacity)
            val pager = rememberPagerState(initialPage = 1) { 1 + homePages + if (drag.active) 1 else 0 }
            HomePressScroll(signals, pager, 1)
            LaunchedEffect(showToday) { if (showToday > 0) pager.animateScrollToPage(0) }
            Column(Modifier.fillMaxSize()) {
                HomePages(Screen.COVER, launcher, items, metrics, pager, 1, drag, launcher.settings.labels, Modifier.weight(1f),
                    onOpenApp = { a, r -> actions.open(a, r) }, onOpenFolder = actions.openFolder,
                    onMenu = actions.menu, onCloseMenu = actions.closeMenu, onEmptyHold = actions.emptyHold,
                    leadingPage = {
                        TodayColumn(launcher, host.widgets, today, actions.todayEditing(), Modifier.fillMaxSize().padding(horizontal = 16.dp),
                            onEditing = actions.setTodayEditing, onAddWidget = actions.addWidget, onPermission = host::requestPermission,
                            onNotificationAccess = host::openNotificationAccess, onSettings = actions.settings, onOpenPackage = host::openPackage)
                    })
                val playing = nowPlaying
                if (playing != null && pager.currentPage >= 1) NowPlayingCard(playing, Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                    host.openPackage(playing.packageName)
                }
                PageDots(pager, pager.pageCount, todayPage = true, modifier = Modifier.align(Alignment.CenterHorizontally))
            }
        }
        if (!signals.isDefaultHome) DefaultHomeBanner(host::requestDefaultHome, Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp))
        Dock(launcher, drag, 54.dp, Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, bottom = 10.dp),
            onOpenApp = { a, r -> actions.open(a, r) }, onMenu = actions.menu, onCloseMenu = actions.closeMenu, onSearch = actions.search)
    }
}

/** Inner screen: "Aujourd'hui" on the left half, home on the right half; the split sits on the hinge. */
@Composable
private fun InnerHome(host: PliHost, signals: HostSignals, today: TodayState, drag: DragState, actions: HomeActions,
    swipesOn: Boolean, showToday: Int) {
    val launcher = host.launcher
    val items = launcher.visibleHome(Screen.INNER)
    Row(Modifier.fillMaxSize()) {
        TodayColumn(launcher, host.widgets, today, actions.todayEditing(),
            Modifier.weight(1f).fillMaxHeight().padding(start = 24.dp, end = 22.dp, top = 14.dp),
            onEditing = actions.setTodayEditing, onAddWidget = actions.addWidget, onPermission = host::requestPermission,
            onNotificationAccess = host::openNotificationAccess, onSettings = actions.settings, onOpenPackage = host::openPackage)
        Box(Modifier.width(2.dp).fillMaxHeight(.72f).align(Alignment.CenterVertically)
            .background(Brush.verticalGradient(listOf(Color.Transparent, Holo.Violet.copy(alpha = .35f), Color.Transparent))))
        Column(Modifier.weight(1f).fillMaxHeight().homeSwipes(swipesOn, actions.drawer) { swipeDown(host, actions, it) }) {
            ClockHeader(today, 66.sp, compact = true, Modifier.padding(start = 26.dp, end = 22.dp, top = 14.dp), onWeather = {},
                trailing = { SearchOrb(52.dp, actions.search) })
            Spacer(Modifier.height(16.dp))
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                val metrics = gridMetrics(maxWidth, maxHeight - 18.dp, launcher.settings.labels, wide = true)
                val homePages = maxOf(1, (items.size + metrics.capacity - 1) / metrics.capacity)
                val pager = rememberPagerState(initialPage = 0) { homePages + if (drag.active) 1 else 0 }
                HomePressScroll(signals, pager, 0)
                Column(Modifier.fillMaxSize()) {
                    HomePages(Screen.INNER, launcher, items, metrics, pager, 0, drag, launcher.settings.labels, Modifier.weight(1f).padding(horizontal = 8.dp),
                        onOpenApp = { a, r -> actions.open(a, r) }, onOpenFolder = actions.openFolder,
                        onMenu = actions.menu, onCloseMenu = actions.closeMenu, onEmptyHold = actions.emptyHold)
                    PageDots(pager, pager.pageCount, todayPage = false, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
            if (!signals.isDefaultHome) DefaultHomeBanner(host::requestDefaultHome, Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp))
            Dock(launcher, drag, 56.dp, Modifier.fillMaxWidth().padding(start = 18.dp, end = 18.dp, bottom = 12.dp),
                onOpenApp = { a, r -> actions.open(a, r) }, onMenu = actions.menu, onCloseMenu = actions.closeMenu, onSearch = null)
        }
    }
}
