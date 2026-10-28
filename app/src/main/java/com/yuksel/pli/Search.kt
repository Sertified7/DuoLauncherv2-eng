package com.yuksel.pli

import android.Manifest
import android.app.SearchManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AirplanemodeActive
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.BatteryFull
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Shop
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import kotlin.math.abs
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// ---------- Calculator ----------

/** Evaluates simple arithmetic typed in search ("12*8", "(3,5+2)/4", "15 %"). Null if it isn't a calculation. */
fun evaluateCalculation(input: String): String? {
    val text = input.replace(" ", "").replace(',', '.').replace('×', '*').replace('x', '*').replace('÷', '/')
    if (text.isEmpty() || text.none { it in "+-*/%" } || text.any { it !in "0123456789.+-*/%()" }) return null
    var pos = 0
    fun peek() = text.getOrNull(pos)
    fun expr(): Double {
        fun factor(): Double {
            val c = peek() ?: error("end")
            if (c == '-') { pos++; return -factor() }
            if (c == '+') { pos++; return factor() }
            if (c == '(') { pos++; val v = expr(); require(peek() == ')'); pos++; return v }
            val start = pos
            while (peek()?.let { it.isDigit() || it == '.' } == true) pos++
            var value = text.substring(start, pos).toDouble()
            if (peek() == '%') { pos++; value /= 100.0 }
            return value
        }
        fun term(): Double {
            var v = factor()
            while (peek() == '*' || peek() == '/') { val op = text[pos++]; val r = factor(); v = if (op == '*') v * r else v / r }
            return v
        }
        var v = term()
        while (peek() == '+' || peek() == '-') { val op = text[pos++]; val r = term(); v = if (op == '+') v + r else v - r }
        return v
    }
    val result = runCatching { expr().also { require(pos == text.length) } }.getOrNull() ?: return null
    if (result.isNaN() || result.isInfinite()) return null
    return if (result == kotlin.math.floor(result) && kotlin.math.abs(result) < 1e15) result.toLong().toString()
    else "%.6f".format(java.util.Locale.FRANCE, result).trimEnd('0').trimEnd(',')
}

// ---------- Settings & contacts ----------

private data class SettingShortcut(val label: String, val keywords: String, val action: String, val icon: ImageVector)

private val settingShortcuts = listOf(
    SettingShortcut("Wi-Fi", "wifi internet reseau", Settings.ACTION_WIFI_SETTINGS, Icons.Rounded.Wifi),
    SettingShortcut("Bluetooth", "bluetooth casque ecouteurs montre", Settings.ACTION_BLUETOOTH_SETTINGS, Icons.Rounded.Bluetooth),
    SettingShortcut("Affichage", "ecran luminosite affichage mode sombre", Settings.ACTION_DISPLAY_SETTINGS, Icons.Rounded.LightMode),
    SettingShortcut("Son et vibration", "son volume sonnerie vibration", Settings.ACTION_SOUND_SETTINGS, Icons.Rounded.VolumeUp),
    SettingShortcut("Batterie", "batterie energie economie", Intent.ACTION_POWER_USAGE_SUMMARY, Icons.Rounded.BatteryFull),
    SettingShortcut("Notifications", "notifications alertes", Settings.ACTION_APP_NOTIFICATION_SETTINGS, Icons.Rounded.Notifications),
    SettingShortcut("Applications", "applications applis apps", Settings.ACTION_APPLICATION_SETTINGS, Icons.Rounded.Apps),
    SettingShortcut("Stockage", "stockage memoire espace", Settings.ACTION_INTERNAL_STORAGE_SETTINGS, Icons.Rounded.Storage),
    SettingShortcut("Localisation", "position gps localisation", Settings.ACTION_LOCATION_SOURCE_SETTINGS, Icons.Rounded.LocationOn),
    SettingShortcut("Mode avion", "avion vol", Settings.ACTION_AIRPLANE_MODE_SETTINGS, Icons.Rounded.AirplanemodeActive),
    SettingShortcut("Données mobiles", "donnees mobiles 4g 5g reseau sim", Settings.ACTION_DATA_ROAMING_SETTINGS, Icons.Rounded.SignalCellularAlt),
    SettingShortcut("Paramètres", "parametres reglages", Settings.ACTION_SETTINGS, Icons.Rounded.Settings),
)

private data class Contact(val name: String, val lookup: Uri)

private suspend fun searchContacts(context: Context, query: String): List<Contact> = withContext(Dispatchers.IO) {
    if (query.isBlank() || context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return@withContext emptyList()
    runCatching {
        val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(query))
        val result = ArrayList<Contact>()
        context.contentResolver.query(uri, arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY,
            ContactsContract.Contacts.DISPLAY_NAME_PRIMARY), null, null, null)?.use { c ->
            while (c.moveToNext() && result.size < 4) {
                val name = c.getString(2) ?: continue
                result += Contact(name, ContactsContract.Contacts.getLookupUri(c.getLong(0), c.getString(1)))
            }
        }
        result
    }.getOrDefault(emptyList())
}

fun matchApps(apps: List<AppInfo>, query: String): List<AppInfo> {
    val q = query.trim().normalizedForSearch()
    if (q.isEmpty()) return emptyList()
    val starts = apps.filter { it.searchKey.startsWith(q) }
    val words = apps.filter { it !in starts && it.searchKey.split(' ', '-', '.').any { w -> w.startsWith(q) } }
    val contains = apps.filter { it !in starts && it !in words && it.searchKey.contains(q) }
    return starts + words + contains
}

private fun startSafely(context: Context, intent: Intent) = runCatching {
    context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}.isSuccess

fun webSearch(context: Context, query: String) {
    if (!startSafely(context, Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, query)))
        startSafely(context, Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))))
}

@Composable
fun GlassField(value: String, onValue: (String) -> Unit, placeholder: String, focus: FocusRequester?, modifier: Modifier = Modifier,
    onDone: () -> Unit = {}, trailing: (@Composable () -> Unit)? = null) {
    Row(modifier.height(56.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = .07f))
        .border(1.dp, Brush.linearGradient(Holo.Iridescent.map { it.copy(alpha = .75f) }), RoundedCornerShape(50))
        .padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Search, null, tint = Holo.Violet, modifier = Modifier.size(20.dp))
        Box(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            if (value.isEmpty()) Text(placeholder, color = Holo.Muted, fontSize = 16.sp)
            BasicTextField(value, onValue, Modifier.fillMaxWidth().then(if (focus != null) Modifier.focusRequester(focus) else Modifier),
                singleLine = true, textStyle = TextStyle(color = Holo.Ink, fontSize = 16.sp), cursorBrush = SolidColor(Holo.Violet),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { onDone() }))
        }
        trailing?.invoke()
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text.uppercase(), color = Holo.Muted, fontSize = 11.sp, letterSpacing = 2.5.sp, modifier = Modifier.padding(top = 6.dp, bottom = 8.dp))
}

@Composable
private fun ResultRow(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).glass(CircleShape, .06f), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Holo.Cyan, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, color = Holo.Ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Holo.Muted, fontSize = 12.sp, maxLines = 1)
        }
    }
}

@Composable
private fun AppChoice(app: AppInfo, size: androidx.compose.ui.unit.Dp, onOpen: (AppInfo, Rect) -> Unit, onHold: ((AppInfo, Rect) -> Unit)?) {
    var bounds by remember { mutableStateOf(Rect.Zero) }
    Column(Modifier.fillMaxWidth().onGloballyPositioned { bounds = it.boundsInRoot() }.clip(RoundedCornerShape(18.dp))
        .combinedClickable(onClick = { onOpen(app, bounds) }, onLongClick = onHold?.let { { it(app, bounds) } })
        .padding(vertical = 6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        AppIcon(app, size)
        IconLabel(app.label)
    }
}

/** Spotlight-style search: calculator, apps, contacts, settings and the web. */
@Composable
fun SearchPanel(launcher: Launcher, modifier: Modifier, onOpen: (AppInfo, Rect?) -> Unit, onHold: (AppInfo, Rect) -> Unit,
    onClose: () -> Unit, onRequestContacts: () -> Unit) {
    val context = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() }; keyboard?.show() }
    val apps = launcher.visibleApps
    val matches = remember(query, apps) { matchApps(apps, query).take(8) }
    val suggestions = remember(apps) { UsageLog.suggestions(context, launcher.byId, 8).ifEmpty { launcher.visibleDock(Screen.COVER).mapNotNull(launcher.byId::get) } }
    val calc = remember(query) { evaluateCalculation(query) }
    val contactsAllowed = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    val contacts by produceState(emptyList<Contact>(), query, contactsAllowed) { value = searchContacts(context, query) }
    val q = query.trim().normalizedForSearch()
    val settings = remember(q) { if (q.length < 2) emptyList() else settingShortcuts.filter { it.label.normalizedForSearch().contains(q) || it.keywords.contains(q) }.take(3) }
    fun launchFirst() {
        val first = matches.firstOrNull()
        if (first != null) onOpen(first, null) else if (query.isNotBlank()) { webSearch(context, query); onClose() }
    }
    val list = rememberLazyListState()
    Column(modifier.swipeDownToClose({ list.firstVisibleItemIndex == 0 && list.firstVisibleItemScrollOffset == 0 }) { keyboard?.hide(); onClose() }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GlassField(query, { query = it }, "Rechercher", focus, Modifier.weight(1f), onDone = ::launchFirst)
            Text("Annuler", color = Holo.Violet, fontSize = 14.sp, modifier = Modifier.padding(start = 12.dp).clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onClose).padding(8.dp))
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn(Modifier.fillMaxSize(), list, verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (calc != null) item {
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(Holo.Iridescent.map { it.copy(alpha = .2f) }))
                    .border(1.dp, Color.White.copy(alpha = .22f), RoundedCornerShape(28.dp)).padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("CALCUL", color = Holo.Soft, fontSize = 11.sp, letterSpacing = 2.5.sp)
                        IridescentText("= $calc", 40.sp, letterSpacing = 0.sp)
                    }
                    PillButton("Copier") {
                        context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Résultat", calc))
                    }
                }
            }
            val shown = if (query.isBlank()) suggestions else matches
            if (shown.isNotEmpty()) item {
                Column {
                    SectionLabel(if (query.isBlank()) "Suggestions" else "Applis")
                    shown.chunked(4).forEach { row ->
                        Row(Modifier.fillMaxWidth()) {
                            row.forEach { app -> Box(Modifier.weight(1f)) { AppChoice(app, 56.dp, { a, r -> onOpen(a, r) }, onHold) } }
                            repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }
            if (query.isNotBlank()) {
                if (contacts.isNotEmpty() || !contactsAllowed) item {
                    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), .05f).padding(6.dp)) {
                        SectionLabel("  Contacts")
                        if (!contactsAllowed) ResultRow(Icons.Rounded.Person, "Chercher aussi dans les contacts", "Autoriser l’accès", onRequestContacts)
                        contacts.forEach { c ->
                            ResultRow(Icons.Rounded.Person, c.name, "Contact") { startSafely(context, Intent(Intent.ACTION_VIEW, c.lookup)); onClose() }
                        }
                    }
                }
                if (settings.isNotEmpty()) item {
                    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), .05f).padding(6.dp)) {
                        SectionLabel("  Réglages")
                        settings.forEach { s -> ResultRow(s.icon, s.label, "Réglages") { startSafely(context, Intent(s.action)); onClose() } }
                    }
                }
                item {
                    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(24.dp), .05f).padding(6.dp)) {
                        SectionLabel("  Web")
                        ResultRow(Icons.Rounded.Language, "Rechercher « ${query.trim()} »", "Sur le web") { webSearch(context, query); onClose() }
                        ResultRow(Icons.Rounded.Shop, "Chercher une appli « ${query.trim()} »", "Play Store") {
                            startSafely(context, Intent(Intent.ACTION_VIEW, Uri.parse("market://search?q=" + Uri.encode(query)))); onClose()
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/** Every app, A to Z, with a filter field. */
@Composable
fun AppDrawer(launcher: Launcher, modifier: Modifier, onOpen: (AppInfo, Rect) -> Unit, onHold: (AppInfo, Rect) -> Unit, onClose: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf("") }
    val apps = launcher.visibleApps
    val shown = remember(filter, apps) { if (filter.isBlank()) apps else matchApps(apps, filter) }
    val grid = rememberLazyGridState()
    Column(modifier.swipeDownToClose({ grid.firstVisibleItemIndex == 0 && grid.firstVisibleItemScrollOffset == 0 }, onClose)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IridescentText("Applis", 30.sp, Modifier.weight(1f), letterSpacing = (-.5).sp)
            Text("${apps.size}", color = Holo.Muted, fontSize = 13.sp, modifier = Modifier.padding(end = 12.dp))
            PillButton("Fermer", onClick = onClose)
        }
        Spacer(Modifier.height(12.dp))
        GlassField(filter, { filter = it }, "Filtrer les applis", null, Modifier.fillMaxWidth(),
            onDone = { shown.firstOrNull()?.let { onOpen(it, Rect.Zero) } })
        Spacer(Modifier.height(12.dp))
        LazyVerticalGrid(GridCells.Adaptive(86.dp), Modifier.fillMaxSize(), grid) {
            items(shown, key = { it.id }) { app -> AppChoice(app, 56.dp, onOpen, onHold) }
        }
    }
}

/**
 * Closes a panel with a downward swipe when its list is scrolled to the top.
 * Upward swipes and swipes inside a scrolled list are left alone.
 */
fun Modifier.swipeDownToClose(atTop: () -> Boolean, onClose: () -> Unit): Modifier =
    pointerInput(Unit) {
        val threshold = 64.dp.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (!atTop()) return@awaitEachGesture
            var total = Offset.Zero
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!change.pressed) break
                total += change.positionChange()
                if (total.y < -12f || abs(total.x) > threshold) break
                if (total.y > threshold && total.y > abs(total.x) * 1.3f) {
                    change.consume(); onClose(); break
                }
            }
        }
    }
