package com.yuksel.pli

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Process
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId

// ---------- Rappels (to-do list) ----------

data class Task(val id: Long, val text: String, val done: Boolean)

private object TaskStore {
    fun load(context: Context): List<Task> = runCatching {
        val a = JSONArray(context.getSharedPreferences("pli", Context.MODE_PRIVATE).getString("tasks", "[]"))
        (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { Task(it.optLong("id"), it.optString("text"), it.optBoolean("done")) }
    }.getOrDefault(emptyList())

    fun save(context: Context, tasks: List<Task>) {
        val a = JSONArray().apply { tasks.forEach { put(JSONObject().put("id", it.id).put("text", it.text).put("done", it.done)) } }
        context.getSharedPreferences("pli", Context.MODE_PRIVATE).edit().putString("tasks", a.toString()).apply()
    }
}

/** "Rappels": a small checklist. Tap a circle to tick, × to delete. */
@Composable
fun TasksCard() {
    val context = LocalContext.current
    var tasks by remember { mutableStateOf(TaskStore.load(context)) }
    var draft by remember { mutableStateOf("") }
    fun save(list: List<Task>) { tasks = list; TaskStore.save(context, list) }
    fun add() {
        val text = draft.trim()
        if (text.isEmpty()) return
        save(tasks + Task(System.currentTimeMillis(), text, false)); draft = ""
    }
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(28.dp), .05f).padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("RAPPELS", color = Holo.Muted, fontSize = 11.sp, letterSpacing = 2.5.sp, modifier = Modifier.weight(1f))
            val doneCount = tasks.count { it.done }
            if (doneCount > 0) Text("Effacer les terminés ($doneCount)", color = Holo.Violet, fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { save(tasks.filterNot { it.done }) }.padding(4.dp))
        }
        (tasks.filterNot { it.done } + tasks.filter { it.done }).forEach { t ->
            Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(26.dp).clip(CircleShape)
                    .then(if (t.done) Modifier.background(Brush.linearGradient(Holo.Iridescent), CircleShape) else Modifier.border(1.5.dp, Holo.Muted, CircleShape))
                    .clickable { save(tasks.map { if (it.id == t.id) it.copy(done = !it.done) else it }) }
                    .semantics { contentDescription = if (t.done) "Marquer comme à faire" else "Marquer comme fait" },
                    contentAlignment = Alignment.Center) {
                    if (t.done) Icon(Icons.Rounded.Check, null, tint = Color(0xFF120A22), modifier = Modifier.size(16.dp))
                }
                Text(t.text, color = if (t.done) Holo.Muted else Holo.Ink, fontSize = 15.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    textDecoration = if (t.done) TextDecoration.LineThrough else null, modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                Box(Modifier.size(32.dp).clip(CircleShape).clickable { save(tasks.filterNot { it.id == t.id }) }
                    .semantics { contentDescription = "Supprimer" }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, null, tint = Holo.Muted, modifier = Modifier.size(16.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(46.dp).glass(RoundedCornerShape(50), .06f).padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                if (draft.isEmpty()) Text("Ajouter un rappel…", color = Holo.Muted, fontSize = 14.sp)
                BasicTextField(draft, { draft = it }, Modifier.fillMaxWidth(), singleLine = true,
                    textStyle = TextStyle(color = Holo.Ink, fontSize = 14.sp), cursorBrush = SolidColor(Holo.Violet),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { add() }))
            }
            Box(Modifier.size(36.dp).clip(CircleShape).background(Brush.linearGradient(Holo.Iridescent), CircleShape).clickable { add() }
                .semantics { contentDescription = "Ajouter" }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Add, null, tint = Color(0xFF120A22), modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ---------- Temps d'écran ----------

fun hasUsageAccess(context: Context): Boolean = runCatching {
    val ops = context.getSystemService(AppOpsManager::class.java)
    ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
}.getOrDefault(false)

/** Foreground time per package since midnight, longest first. */
private suspend fun todayUsage(context: Context): List<Pair<String, Long>> = withContext(Dispatchers.IO) {
    runCatching {
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        usm.queryAndAggregateUsageStats(start, System.currentTimeMillis()).values
            .filter { it.packageName != context.packageName && it.totalTimeInForeground > 60_000 }
            .map { it.packageName to it.totalTimeInForeground }.sortedByDescending { it.second }
    }.getOrDefault(emptyList())
}

private fun duration(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes >= 60) "${minutes / 60} h ${"%02d".format(minutes % 60)}" else "$minutes min"
}

/** "Temps d'écran": today's total and the three apps used the most. */
@Composable
fun ScreenTimeCard(launcher: Launcher, refresh: Int) {
    val context = LocalContext.current
    val allowed = hasUsageAccess(context)
    val usage by produceState(emptyList<Pair<String, Long>>(), allowed, refresh) { if (allowed) value = todayUsage(context) }
    Column(Modifier.fillMaxWidth().glass(RoundedCornerShape(28.dp), .05f).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("TEMPS D’ÉCRAN", color = Holo.Muted, fontSize = 11.sp, letterSpacing = 2.5.sp)
        if (!allowed) {
            Text("Autorise l’accès à l’utilisation pour voir ton temps d’écran du jour.", color = Holo.Soft, fontSize = 14.sp)
            PillButton("Autoriser", true) {
                runCatching { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
            return@Column
        }
        val total = usage.sumOf { it.second }
        IridescentText(duration(total), 40.sp, letterSpacing = 0.sp)
        Text("aujourd’hui", color = Holo.Muted, fontSize = 12.sp)
        val top = usage.take(3)
        val max = top.maxOfOrNull { it.second } ?: 1L
        top.forEach { (pkg, ms) ->
            val app = launcher.apps.firstOrNull { it.packageName == pkg }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (app != null) AppIcon(app, 30.dp, showBadge = false) else Box(Modifier.size(30.dp))
                Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(app?.label ?: pkg.substringAfterLast('.'), color = Holo.Ink, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(5.dp).background(Color.White.copy(alpha = .08f), CircleShape)) {
                        Box(Modifier.fillMaxWidth((ms.toFloat() / max).coerceIn(.05f, 1f)).height(5.dp)
                            .background(Brush.horizontalGradient(Holo.Iridescent), CircleShape))
                    }
                }
                Text(duration(ms), color = Holo.Soft, fontSize = 12.sp)
            }
        }
        if (top.isEmpty()) Text("Pas encore d’utilisation aujourd’hui.", color = Holo.Soft, fontSize = 13.sp)
    }
}
