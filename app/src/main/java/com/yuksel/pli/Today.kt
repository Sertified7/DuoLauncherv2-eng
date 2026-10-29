package com.yuksel.pli

import android.Manifest
import android.annotation.SuppressLint
import android.appwidget.AppWidgetProviderInfo
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.LinkOff
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import java.time.format.DateTimeFormatter

data class Gadget(val name: String, val level: Int)

/** Live data for the built-in widgets and the header chips. */
class TodayState {
    var weather by mutableStateOf<Weather?>(null)
    var events by mutableStateOf<List<AgendaEvent>>(emptyList())
    var battery by mutableStateOf<BatteryInfo?>(null)
    var gadgets by mutableStateOf<List<Gadget>>(emptyList())
    var calendarAllowed by mutableStateOf(false)
    var notificationsAllowed by mutableStateOf(false)
    var refreshCount by mutableStateOf(0)
    private var weatherAt = 0L
    private var weatherKey = ""

    suspend fun refresh(context: Context, settings: PliSettings, force: Boolean = false) {
        refreshCount++
        battery = readBattery(context)
        gadgets = readGadgets(context)
        calendarAllowed = hasCalendarAccess(context)
        notificationsAllowed = BadgeService.hasAccess(context)
        events = loadEvents(context)
        val key = "${settings.latitude},${settings.longitude}"
        if (force || key != weatherKey || System.currentTimeMillis() - weatherAt > 20 * 60_000L || weather == null) {
            loadWeather(context, settings)?.let { weather = it; weatherAt = System.currentTimeMillis(); weatherKey = key }
        }
    }
}

/** Battery of connected Bluetooth gadgets (earbuds, watch) when the system reports it. */
@SuppressLint("MissingPermission")
private fun readGadgets(context: Context): List<Gadget> {
    if (context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return emptyList()
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
    return runCatching {
        adapter.bondedDevices.orEmpty().mapNotNull { device ->
            val connected = runCatching { BluetoothDevice::class.java.getMethod("isConnected").invoke(device) as Boolean }.getOrDefault(false)
            if (!connected) return@mapNotNull null
            val level = runCatching { BluetoothDevice::class.java.getMethod("getBatteryLevel").invoke(device) as Int }.getOrDefault(-1)
            if (level < 0) null else Gadget(device.alias ?: device.name ?: "Device", level)
        }.take(2)
    }.getOrDefault(emptyList())
}

private val cardShape = RoundedCornerShape(28.dp)

/** The "Today" column: built-in cards and Android widgets, in the order the user chose. */
@Composable
fun TodayColumn(
    launcher: Launcher, widgets: Widgets, today: TodayState, editing: Boolean, modifier: Modifier,
    onEditing: (Boolean) -> Unit, onAddWidget: () -> Unit, onPermission: (String) -> Unit,
    onNotificationAccess: () -> Unit, onSettings: () -> Unit, onOpenPackage: (String) -> Unit,
    showTitle: Boolean = true,
) {
    val items = launcher.layout.today
    val nowPlaying by MediaWatcher.nowPlaying.collectAsState()
    Column(modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        if (showTitle) Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IridescentText("Today", 32.sp, Modifier.weight(1f), letterSpacing = (-.5).sp)
            PillButton(if (editing) "Done" else "Edit", highlighted = editing) { onEditing(!editing) }
        } else if (editing) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            PillButton("Done", highlighted = true) { onEditing(false) }
        }
        val card: @Composable (TodayItem) -> Unit = { item ->
            when (item.kind) {
                TodayItem.WEATHER -> WeatherCard(today.weather, onSettings, onPermission)
                TodayItem.AGENDA -> AgendaCard(today, onPermission)
                TodayItem.MEDIA -> {
                    val playing = nowPlaying
                    if (playing != null) NowPlayingCard(playing) { onOpenPackage(playing.packageName) }
                    else if (!today.notificationsAllowed) HintCard("Now playing", "Allow notification access to see and control your music.", "Allow", onNotificationAccess)
                    else HintCard("Now playing", "Nothing playing right now.", null, null)
                }
                TodayItem.BATTERY -> BatteryCard(today, onPermission)
                TodayItem.NOTE -> NoteCard(launcher)
                TodayItem.TOGGLES -> TogglesCard(onPermission)
                TodayItem.TASKS -> TasksCard()
                TodayItem.SCREEN_TIME -> ScreenTimeCard(launcher, today.refreshCount)
                TodayItem.KIND_ANDROID -> AndroidWidgetCard(widgets, item)
            }
        }
        fun idle(item: TodayItem) = item.kind == TodayItem.MEDIA && nowPlaying == null && today.notificationsAllowed
        if (editing) items.forEachIndexed { index, item ->
            Column {
                EditBar(
                    title = cardTitle(item, widgets) + if (item.stack.isNotEmpty()) " · stack" else "",
                    canUp = index > 0, canDown = index < items.lastIndex, resizable = item.isAndroid,
                    onUp = { launcher.update { it.copy(today = it.today.move(index, index - 1)) } },
                    onDown = { launcher.update { it.copy(today = it.today.move(index, index + 1)) } },
                    onBigger = { launcher.update { l -> l.copy(today = l.today.map { if (it.key == item.key) it.copy(height = (it.height + 60).coerceAtMost(640)) else it }) } },
                    onSmaller = { launcher.update { l -> l.copy(today = l.today.map { if (it.key == item.key) it.copy(height = (it.height - 60).coerceAtLeast(80)) else it }) } },
                    onRemove = {
                        if (item.isAndroid) widgets.delete(item.widgetId)
                        launcher.update { l -> l.copy(today = l.today.filterNot { it.key == item.key }) }
                    },
                    stacked = item.stack.isNotEmpty(),
                    canStack = index < items.lastIndex,
                    onStack = {
                        launcher.update { l ->
                            val next = l.today.getOrNull(index + 1) ?: return@update l
                            val id = item.stack.ifEmpty { next.stack.ifEmpty { "s" + System.nanoTime().toString(36) } }
                            l.copy(today = l.today.mapIndexed { i, t ->
                                if (i == index || i == index + 1 || (next.stack.isNotEmpty() && t.stack == next.stack)) t.copy(stack = id) else t })
                        }
                    },
                    onUnstack = { launcher.update { l -> l.copy(today = l.today.map { if (it.key == item.key) it.copy(stack = "") else it }) } })
                card(item)
            }
        } else {
            // Consecutive cards of one stack become one swipeable card.
            val groups = ArrayList<List<TodayItem>>()
            items.filterNot(::idle).forEach { item ->
                val last = groups.lastOrNull()
                if (last != null && item.stack.isNotEmpty() && last.first().stack == item.stack) groups[groups.lastIndex] = last + item
                else groups += listOf(item)
            }
            groups.forEach { group -> if (group.size == 1) card(group[0]) else StackedCards(group, card) }
        }
        if (editing || items.isEmpty()) Row(Modifier.fillMaxWidth().height(56.dp).glass(cardShape, .04f).clickable(onClick = onAddWidget),
            horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Add, null, tint = Holo.Violet)
            Text("Add a widget", color = Holo.Ink, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
        }
        Spacer(Modifier.height(8.dp))
    }
}

private fun <T> List<T>.move(from: Int, to: Int): List<T> {
    if (from !in indices || to !in indices) return this
    return toMutableList().apply { add(to, removeAt(from)) }
}

fun cardTitle(item: TodayItem, widgets: Widgets): String = when (item.kind) {
    TodayItem.WEATHER -> "Weather"
    TodayItem.AGENDA -> "Calendar"
    TodayItem.MEDIA -> "Now playing"
    TodayItem.BATTERY -> "Battery"
    TodayItem.NOTE -> "Quick note"
    TodayItem.TOGGLES -> "Quick toggles"
    TodayItem.TASKS -> "Reminders"
    TodayItem.SCREEN_TIME -> "Screen time"
    else -> widgets.info(item.widgetId)?.let(widgets::label) ?: "Widget"
}

@Composable
private fun EditBar(title: String, canUp: Boolean, canDown: Boolean, resizable: Boolean,
    onUp: () -> Unit, onDown: () -> Unit, onBigger: () -> Unit, onSmaller: () -> Unit, onRemove: () -> Unit,
    stacked: Boolean = false, canStack: Boolean = false, onStack: () -> Unit = {}, onUnstack: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Holo.Muted, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(start = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (resizable) {
            SmallRound(Icons.Rounded.Remove, "Shrink", onSmaller)
            SmallRound(Icons.Rounded.Add, "Enlarge", onBigger)
        }
        if (stacked) SmallRound(Icons.Rounded.LinkOff, "Remove from stack", onUnstack)
        else if (canStack) SmallRound(Icons.Rounded.Layers, "Stack with next", onStack)
        SmallRound(Icons.Rounded.KeyboardArrowUp, "Move up", onUp, canUp)
        SmallRound(Icons.Rounded.KeyboardArrowDown, "Move down", onDown, canDown)
        SmallRound(Icons.Rounded.Close, "Remove", onRemove, tint = Holo.Badge)
    }
}

@Composable
private fun SmallRound(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit,
    enabled: Boolean = true, tint: Color = Holo.Ink) {
    // A compact round button: 34 dp drawn, 8 dp apart, so the row stays tidy.
    Box(Modifier.padding(start = 8.dp).size(34.dp).glass(CircleShape, .08f)
        .clickable(enabled = enabled, role = androidx.compose.ui.semantics.Role.Button, onClick = onClick)
        .semantics { contentDescription = label }, contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = if (enabled) tint else tint.copy(alpha = .3f), modifier = Modifier.size(18.dp))
    }
}

@Composable
fun PillButton(text: String, highlighted: Boolean = false, onClick: () -> Unit) {
    Box(Modifier.height(36.dp).clip(RoundedCornerShape(50))
        .then(if (highlighted) Modifier.background(Brush.linearGradient(Holo.Iridescent), RoundedCornerShape(50)) else Modifier.glass(RoundedCornerShape(50)))
        .clickable(onClick = onClick).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
        Text(text, color = if (highlighted) Color(0xFF120A22) else Holo.Soft, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun CardLabel(text: String) {
    Text(text.uppercase(), color = Holo.Muted, fontSize = 11.sp, letterSpacing = 2.5.sp)
}

@Composable
private fun HintCard(title: String, text: String, action: String?, onAction: (() -> Unit)?) {
    Column(Modifier.fillMaxWidth().glass(cardShape, .05f).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        CardLabel(title)
        Text(text, color = Holo.Soft, fontSize = 14.sp)
        if (action != null && onAction != null) PillButton(action, true, onAction)
    }
}

@Composable
private fun WeatherCard(weather: Weather?, onSettings: () -> Unit, onPermission: (String) -> Unit) {
    if (weather == null) {
        Column(Modifier.fillMaxWidth().glass(cardShape, .05f).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CardLabel("Weather")
            Text("Choose your city or allow location to see the weather.", color = Holo.Soft, fontSize = 14.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton("Choose a city", true, onSettings)
                PillButton("My location") { onPermission(Manifest.permission.ACCESS_COARSE_LOCATION) }
            }
        }
        return
    }
    Row(Modifier.fillMaxWidth().glass(cardShape, .05f).padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(weather.place, color = Holo.Muted, fontSize = 12.sp)
            Text("${weather.temperature}°", color = Holo.Ink, fontSize = 54.sp, fontWeight = FontWeight.ExtraLight, lineHeight = 56.sp)
            val range = if (weather.high != null && weather.low != null) " · ${weather.low}° / ${weather.high}°" else ""
            Text(weather.label + range, color = Holo.Soft, fontSize = 12.sp)
        }
        if (weather.hours.isEmpty()) Icon(skyIcon(weather.sky), null, tint = Holo.Violet, modifier = Modifier.size(56.dp))
        else Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val min = weather.hours.minOf { it.temperature }; val max = weather.hours.maxOf { it.temperature }
            weather.hours.forEach { h ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("${h.hour}:00", color = Holo.Muted, fontSize = 11.sp)
                    Icon(skyIcon(h.sky), null, tint = Holo.Cyan, modifier = Modifier.size(16.dp))
                    val bar = 14.dp + 26.dp * (if (max == min) .5f else (h.temperature - min).toFloat() / (max - min))
                    Box(Modifier.width(7.dp).height(bar).background(Brush.verticalGradient(listOf(Holo.Cyan, Holo.Violet)), CircleShape))
                    Text("${h.temperature}°", color = Holo.Ink, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun AgendaCard(today: TodayState, onPermission: (String) -> Unit) {
    val context = LocalContext.current
    val pattern = if (android.text.format.DateFormat.is24HourFormat(context)) "H:mm" else "h:mm a"
    Column(Modifier.fillMaxWidth().glass(cardShape, .05f).clickable { openCalendar(context) }.padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        CardLabel("Calendar")
        when {
            !today.calendarAllowed -> {
                Text("Allow calendar access to see your upcoming events.", color = Holo.Soft, fontSize = 14.sp)
                PillButton("Allow", true) { onPermission(Manifest.permission.READ_CALENDAR) }
            }
            today.events.isEmpty() -> Text("Nothing planned in the next 24 hours.", color = Holo.Soft, fontSize = 14.sp)
            else -> today.events.forEach { e ->
                val color = if (e.color != 0) Color(e.color or 0xFF000000.toInt()) else Holo.Cyan
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).halo(color.copy(alpha = .6f), 5.dp).background(color, CircleShape))
                    Text(e.title, color = Holo.Ink, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                    Text(if (e.allDay) "All day" else e.start.format(DateTimeFormatter.ofPattern(pattern)), color = Holo.Muted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun BatteryRing(level: Int, name: String, size: Dp = 52.dp) {
    val color = when { level <= 20 -> Holo.Badge; level <= 50 -> Holo.Gold; else -> Holo.Mint }
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(size).halo(color.copy(alpha = .22f), 6.dp).drawBehind {
            val stroke = 4.dp.toPx()
            val inset = stroke / 2
            drawArc(Color.White.copy(alpha = .12f), 0f, 360f, false, Offset(inset, inset), Size(this.size.width - stroke, this.size.height - stroke), style = Stroke(stroke))
            drawArc(color, -90f, 360f * level / 100f, false, Offset(inset, inset), Size(this.size.width - stroke, this.size.height - stroke), style = Stroke(stroke, cap = StrokeCap.Round))
        }, contentAlignment = Alignment.Center) {
            Text("$level%", color = Holo.Ink, fontSize = 11.sp)
        }
        Text(name, color = Holo.Muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.width(size + 18.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun BatteryCard(today: TodayState, onPermission: (String) -> Unit) {
    val context = LocalContext.current
    val bluetoothAllowed = context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    Column(Modifier.fillMaxWidth().glass(cardShape, .05f).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) { CardLabel("Battery") }
            if (today.battery?.charging == true) Text("Charging", color = Holo.Mint, fontSize = 12.sp)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
            today.battery?.let { BatteryRing(it.level, "Phone") }
            today.gadgets.forEach { BatteryRing(it.level, it.name) }
            if (!bluetoothAllowed) Text("Earbuds and watch", color = Holo.Cyan, fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { onPermission(Manifest.permission.BLUETOOTH_CONNECT) }.padding(6.dp))
        }
    }
}

@Composable
private fun NoteCard(launcher: Launcher) {
    var text by remember { mutableStateOf(launcher.store.note) }
    Column(Modifier.fillMaxWidth().clip(cardShape)
        .background(Brush.linearGradient(listOf(Holo.Cyan.copy(alpha = .22f), Holo.Violet.copy(alpha = .22f), Holo.Pink.copy(alpha = .22f))), cardShape)
        .border(1.dp, Color.White.copy(alpha = .22f), cardShape).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("NOTE", color = Holo.Soft, fontSize = 11.sp, letterSpacing = 2.5.sp)
        BasicTextField(text, { text = it; launcher.store.note = it }, Modifier.fillMaxWidth().heightIn(min = 44.dp),
            textStyle = TextStyle(color = Holo.Ink, fontSize = 15.sp, lineHeight = 21.sp), cursorBrush = SolidColor(Holo.Violet),
            decorationBox = { inner ->
                if (text.isEmpty()) Text("Write a note…", color = Holo.Soft.copy(alpha = .6f), fontSize = 15.sp)
                inner()
            })
    }
}

@Composable
private fun AndroidWidgetCard(widgets: Widgets, item: TodayItem) {
    val height = (if (item.height > 0) item.height else 180).dp
    BoxWithConstraints(Modifier.fillMaxWidth().height(height).glass(cardShape, .04f)) {
        val width = maxWidth
        LaunchedEffect(item.widgetId, width, height) { widgets.resize(item.widgetId, width.value, height.value) }
        AndroidView(factory = { ctx -> widgets.view(ctx, item.widgetId) ?: FrameLayout(ctx) }, modifier = Modifier.fillMaxSize())
        if (widgets.info(item.widgetId) == null) Text("Widget unavailable", color = Holo.Muted, fontSize = 13.sp, modifier = Modifier.align(Alignment.Center))
    }
}

/** Lists the built-in cards not shown yet and every Android widget, grouped by app. */
@Composable
fun WidgetPicker(launcher: Launcher, widgets: Widgets, modifier: Modifier, onPickBuiltIn: (String) -> Unit,
    onPickAndroid: (AppWidgetProviderInfo) -> Unit, onClose: () -> Unit) {
    val present = launcher.layout.today.map { it.kind }.toSet()
    val builtIns = TodayItem.BUILT_IN.filter { it !in present }
    val groups = remember { widgets.providers().groupBy { widgets.appLabel(it) }.toSortedMap(String.CASE_INSENSITIVE_ORDER) }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IridescentText("Widgets", 30.sp, Modifier.weight(1f), letterSpacing = (-.5).sp)
            PillButton("Close", onClick = onClose)
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (builtIns.isNotEmpty()) item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CardLabel("Fold Launcher Pro widgets")
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        builtIns.forEach { kind ->
                            PillButton(cardTitle(TodayItem(kind, kind), widgets)) { onPickBuiltIn(kind) }
                        }
                    }
                }
            }
            items(groups.entries.toList(), key = { it.key }) { (app, list) ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    CardLabel(app)
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        list.forEach { info ->
                            val preview = remember(info) { widgets.preview(info)?.let { d ->
                                runCatching {
                                    val w = d.intrinsicWidth.coerceIn(1, 600); val h = d.intrinsicHeight.coerceIn(1, 600)
                                    d.toBitmap(w, h).asImageBitmap()
                                }.getOrNull()
                            } }
                            Column(Modifier.width(150.dp).glass(RoundedCornerShape(20.dp), .05f).clickable { onPickAndroid(info) }.padding(10.dp),
                                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(Modifier.fillMaxWidth().height(90.dp), contentAlignment = Alignment.Center) {
                                    if (preview != null) Image(preview, null, Modifier.fillMaxSize())
                                }
                                Text(widgets.label(info), color = Holo.Soft, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Pixels → dp of an Android widget's default height for the card. */
fun defaultWidgetHeight(context: Context, info: AppWidgetProviderInfo): Int {
    val density = context.resources.displayMetrics.density
    return (info.minHeight / density).toInt().coerceIn(110, 420)
}

/** Several cards in one place: swipe sideways to go through them. */
@Composable
private fun StackedCards(group: List<TodayItem>, card: @Composable (TodayItem) -> Unit) {
    val pager = androidx.compose.foundation.pager.rememberPagerState { group.size }
    Column {
        androidx.compose.foundation.pager.HorizontalPager(pager, Modifier.fillMaxWidth(), pageSpacing = 12.dp,
            verticalAlignment = Alignment.Top) { page -> card(group[page]) }
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
            repeat(group.size) { i ->
                Box(Modifier.padding(horizontal = 3.dp).size(if (pager.currentPage == i) 7.dp else 5.dp)
                    .background(if (pager.currentPage == i) Holo.Violet else Color.White.copy(alpha = .3f), CircleShape))
            }
        }
    }
}
