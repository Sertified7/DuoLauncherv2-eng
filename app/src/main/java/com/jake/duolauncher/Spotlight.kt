package com.jake.duolauncher

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.text.Normalizer
import java.time.LocalDateTime
import java.time.ZoneId

// ---------- Siri-style suggestions from your own launch history ----------

/** Remembers recent launches (on this device only) to suggest the apps you usually open at this time. */
internal object UsageLog {
    private const val PREFS = "usage"
    private const val MAX = 400

    fun record(context: Context, appId: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val array = runCatching { JSONArray(prefs.getString("launches", "[]")) }.getOrDefault(JSONArray())
        array.put(JSONArray().put(appId).put(System.currentTimeMillis()))
        val trimmed = JSONArray().also { out -> for (i in maxOf(0, array.length() - MAX) until array.length()) out.put(array.get(i)) }
        prefs.edit().putString("launches", trimmed.toString()).apply()
    }

    /** Scores each app by launches near the current hour of day, with newer launches counting more. */
    fun suggestions(context: Context, apps: List<AppEntry>, count: Int = 8): List<AppEntry> {
        val array = runCatching {
            JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("launches", "[]"))
        }.getOrDefault(JSONArray())
        val now = System.currentTimeMillis()
        val hour = LocalDateTime.now().hour
        val scores = mutableMapOf<String, Double>()
        for (i in 0 until array.length()) {
            val entry = array.optJSONArray(i) ?: continue
            val id = entry.optString(0); val time = entry.optLong(1)
            val launchHour = java.time.Instant.ofEpochMilli(time).atZone(ZoneId.systemDefault()).hour
            val hourDistance = minOf(kotlin.math.abs(launchHour - hour), 24 - kotlin.math.abs(launchHour - hour))
            val timeOfDay = when (hourDistance) { 0 -> 3.0; 1 -> 2.0; 2 -> 1.0; else -> .25 }
            val ageDays = (now - time) / 86_400_000.0
            scores[id] = (scores[id] ?: 0.0) + timeOfDay / (1.0 + ageDays / 7.0)
        }
        val byId = apps.associateBy(AppEntry::id)
        return scores.entries.sortedByDescending { it.value }.mapNotNull { byId[it.key] }.filter { it.available }.take(count)
    }
}

// ---------- Calculator ----------

/** Evaluates simple arithmetic typed in search ("12*8", "(3,5+2)/4", "15 %"). Null if it isn't a calculation. */
internal fun evaluateCalculation(input: String): String? {
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

private data class SettingsShortcut(val label: String, val keywords: String, val action: String, val icon: ImageVector)

private val settingsShortcuts = listOf(
    SettingsShortcut("Wi-Fi", "wifi internet reseau", Settings.ACTION_WIFI_SETTINGS, Icons.Rounded.Wifi),
    SettingsShortcut("Bluetooth", "bluetooth casque ecouteurs", Settings.ACTION_BLUETOOTH_SETTINGS, Icons.Rounded.Bluetooth),
    SettingsShortcut("Affichage", "ecran luminosite affichage mode sombre", Settings.ACTION_DISPLAY_SETTINGS, Icons.Rounded.LightMode),
    SettingsShortcut("Son et vibration", "son volume sonnerie vibration", Settings.ACTION_SOUND_SETTINGS, Icons.Rounded.VolumeUp),
    SettingsShortcut("Batterie", "batterie energie economie", Intent.ACTION_POWER_USAGE_SUMMARY, Icons.Rounded.BatteryFull),
    SettingsShortcut("Notifications", "notifications alertes", Settings.ACTION_APP_NOTIFICATION_SETTINGS, Icons.Rounded.Notifications),
    SettingsShortcut("Applications", "applications applis apps", Settings.ACTION_APPLICATION_SETTINGS, Icons.Rounded.Apps),
    SettingsShortcut("Stockage", "stockage memoire espace", Settings.ACTION_INTERNAL_STORAGE_SETTINGS, Icons.Rounded.Storage),
    SettingsShortcut("Localisation", "position gps localisation", Settings.ACTION_LOCATION_SOURCE_SETTINGS, Icons.Rounded.LocationOn),
    SettingsShortcut("Mode avion", "avion vol", Settings.ACTION_AIRPLANE_MODE_SETTINGS, Icons.Rounded.AirplanemodeActive),
    SettingsShortcut("Données mobiles", "donnees mobiles 4g 5g reseau sim", Settings.ACTION_DATA_ROAMING_SETTINGS, Icons.Rounded.SignalCellularAlt),
    SettingsShortcut("Paramètres", "parametres reglages", Settings.ACTION_SETTINGS, Icons.Rounded.Settings),
)

private fun fold(text: String) = Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

private data class ContactHit(val name: String, val lookup: Uri)

private fun searchContacts(context: Context, query: String): List<ContactHit> = runCatching {
    if (context.checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return emptyList()
    val uri = Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_FILTER_URI, Uri.encode(query))
    context.contentResolver.query(uri, arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.LOOKUP_KEY,
        ContactsContract.Contacts.DISPLAY_NAME_PRIMARY), null, null, null)?.use { c ->
        buildList {
            while (c.moveToNext() && size < 5) {
                add(ContactHit(c.getString(2) ?: continue, ContactsContract.Contacts.getLookupUri(c.getLong(0), c.getString(1))))
            }
        }
    } ?: emptyList()
}.getOrDefault(emptyList())

// ---------- The search screen ----------

/** iPhone Spotlight: swipe down on Home to search apps, contacts, settings, do quick maths or search the web. */
@Composable
internal fun SpotlightSearch(state: LauncherState, onLaunch: (AppEntry) -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    BackHandler(onBack = onClose)
    LaunchedEffect(Unit) { focus.requestFocus() }
    val suggestions = remember(state.visibleApps) { UsageLog.suggestions(context, state.visibleApps) }
    val folded = fold(query.trim())
    val apps = remember(folded, state.visibleApps) {
        if (folded.isEmpty()) emptyList() else state.visibleApps
            .filter { fold(it.label).contains(folded) }
            .sortedBy { if (fold(it.label).startsWith(folded)) 0 else 1 }.take(12)
    }
    val calculation = remember(query) { evaluateCalculation(query) }
    val settings = remember(folded) {
        if (folded.length < 2) emptyList() else settingsShortcuts.filter { fold(it.label).contains(folded) || it.keywords.contains(folded) }.take(4)
    }
    var contactsAllowed by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED)
    }
    val askContacts = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { contactsAllowed = it }
    val contacts by produceState(emptyList<ContactHit>(), folded, contactsAllowed) {
        value = if (folded.length < 2 || !contactsAllowed) emptyList()
            else withContext(Dispatchers.IO) { searchContacts(context, query.trim()) }
    }
    fun open(intent: Intent) { runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }; onClose() }

    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = .62f))
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose)
        .windowInsetsPadding(WindowInsets.safeDrawing).imePadding()) {
        Column(Modifier.align(Alignment.TopCenter).widthIn(max = 640.dp).fillMaxWidth().padding(16.dp)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}) {
            TextField(query, { query = it }, Modifier.fillMaxWidth().focusRequester(focus).clip(RoundedCornerShape(18.dp)),
                placeholder = { Text("Rechercher") }, singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Effacer") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = {
                    apps.firstOrNull()?.let { onLaunch(it); onClose() } ?: if (query.isNotBlank())
                        open(Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, query)) else Unit
                }),
                colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent))
            Spacer(Modifier.height(12.dp))
            LazyColumn(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).liquidGlass(RoundedCornerShape(22.dp)),
                contentPadding = PaddingValues(12.dp)) {
                if (folded.isEmpty()) {
                    if (suggestions.isNotEmpty()) {
                        item { SectionTitle("Suggestions") }
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                suggestions.take(4).forEach { app -> AppBubble(app) { onLaunch(app); onClose() } }
                            }
                        }
                        if (suggestions.size > 4) item {
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                                suggestions.drop(4).take(4).forEach { app -> AppBubble(app) { onLaunch(app); onClose() } }
                            }
                        }
                    } else item { Text("Tapez pour chercher une appli, un contact, un réglage ou un calcul.", color = Color.White.copy(alpha = .8f)) }
                }
                calculation?.let { result ->
                    item { SectionTitle("Calcul") }
                    item {
                        ResultRow(Icons.Rounded.Calculate, "= $result", "Toucher pour copier") {
                            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(ClipData.newPlainText("Résultat", result))
                            onClose()
                        }
                    }
                }
                if (apps.isNotEmpty()) {
                    item { SectionTitle("Applis") }
                    items(apps, key = { it.id }) { app ->
                        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable { onLaunch(app); onClose() }
                            .padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Image(app.icon.asImageBitmap(), null, Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)))
                            Spacer(Modifier.width(12.dp)); Text(app.label, color = Color.White, fontSize = 16.sp)
                        }
                    }
                }
                if (contacts.isNotEmpty()) {
                    item { SectionTitle("Contacts") }
                    items(contacts, key = { it.lookup.toString() }) { contact ->
                        ResultRow(Icons.Rounded.Person, contact.name, null) { open(Intent(Intent.ACTION_VIEW, contact.lookup)) }
                    }
                } else if (folded.length >= 2 && !contactsAllowed) item {
                    ResultRow(Icons.Rounded.Person, "Chercher aussi dans les contacts", "Autoriser l’accès aux contacts") {
                        askContacts.launch(Manifest.permission.READ_CONTACTS)
                    }
                }
                if (settings.isNotEmpty()) {
                    item { SectionTitle("Réglages") }
                    items(settings, key = { it.action + it.label }) { s -> ResultRow(s.icon, s.label, null) { open(Intent(s.action)) } }
                }
                if (folded.isNotEmpty()) {
                    item { SectionTitle("Web") }
                    item {
                        ResultRow(Icons.Rounded.TravelExplore, "Rechercher « ${query.trim()} »", "Avec Google") {
                            open(Intent(Intent.ACTION_WEB_SEARCH).putExtra(android.app.SearchManager.QUERY, query.trim()))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) =
    Text(text, color = Color.White.copy(alpha = .7f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 8.dp, top = 10.dp, bottom = 6.dp))

@Composable
private fun AppBubble(app: AppEntry, onClick: () -> Unit) {
    Column(Modifier.width(72.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Image(app.icon.asImageBitmap(), null, Modifier.size(52.dp).clip(RoundedCornerShape(13.dp)))
        Text(app.label, color = Color.White, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun ResultRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(Color.White.copy(alpha = .18f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = Color.White)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = Color.White, fontSize = 16.sp)
            subtitle?.let { Text(it, color = Color.White.copy(alpha = .7f), fontSize = 12.sp) }
        }
    }
}
