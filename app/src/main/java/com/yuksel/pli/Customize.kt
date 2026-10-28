package com.yuksel.pli

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Gives an app another name on Pli (its real name stays unchanged). */
@Composable
fun RenameDialog(app: AppInfo, launcher: Launcher, onDone: () -> Unit) {
    val original = launcher.originalLabel(app.id)
    val renamed = app.id in launcher.layout.labels
    var text by remember { mutableStateOf(app.label) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    fun save() {
        val value = text.trim()
        launcher.update { l -> l.copy(labels = if (value.isEmpty()) l.labels - app.id else l.labels + (app.id to value)) }
        onDone()
    }
    Box(Modifier.fillMaxSize().imePadding()) {
        Scrim(.5f, onDone)
        Column(Modifier.align(Alignment.Center).widthIn(max = 380.dp).fillMaxWidth().padding(24.dp)
            .clip(RoundedCornerShape(30.dp)).background(Holo.Panel).border(1.dp, Holo.edgeBrush, RoundedCornerShape(30.dp))
            .clickable(remember { MutableInteractionSource() }, null) {}.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(app, 44.dp, showBadge = false)
                IridescentText("Renommer", 24.sp, Modifier.padding(start = 12.dp), letterSpacing = 0.sp)
            }
            Box(Modifier.fillMaxWidth().glass(RoundedCornerShape(18.dp), .07f).padding(horizontal = 16.dp, vertical = 14.dp)) {
                BasicTextField(text, { text = it }, Modifier.fillMaxWidth().focusRequester(focus), singleLine = true,
                    textStyle = TextStyle(color = Holo.Ink, fontSize = 17.sp), cursorBrush = SolidColor(Holo.Violet))
            }
            Text(if (renamed && original != null) "Nom d’origine : $original" else "Ce nom ne s’affiche que dans Pli.", color = Holo.Muted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (app.id in launcher.layout.labels) PillButton("Nom d’origine") {
                    launcher.update { l -> l.copy(labels = l.labels - app.id) }; onDone()
                }
                Spacer(Modifier.weight(1f))
                PillButton("Annuler", onClick = onDone)
                PillButton("OK", highlighted = true, onClick = ::save)
            }
        }
    }
}

/** Chooses one app's icon: from an installed icon pack, from a picture, or back to the original. */
@Composable
fun IconPicker(app: AppInfo, launcher: Launcher, host: PliHost, modifier: Modifier, onDone: () -> Unit) {
    val context = LocalContext.current
    val packs = remember { IconPacks.installed(context) }
    var pack by remember { mutableStateOf(packs.firstOrNull()?.packageName) }
    var filter by remember { mutableStateOf("") }
    val names by produceState<List<String>?>(null, pack) {
        value = null
        value = pack?.let { p -> withContext(Dispatchers.IO) { CustomIcons.packIcons(context, p) } } ?: emptyList()
    }
    // Icons whose name looks like the app's come first.
    val key = app.label.normalizedForSearch().replace(" ", "")
    val shown = remember(names, filter) {
        val all = names ?: emptyList()
        val q = filter.trim().lowercase().replace(" ", "_")
        (if (q.isEmpty()) all else all.filter { it.contains(q) }).sortedBy { if (key.isNotEmpty() && it.replace("_", "").contains(key)) 0 else 1 }
    }
    fun set(spec: String?) {
        launcher.update { l -> l.copy(icons = if (spec == null) l.icons - app.id else l.icons + (app.id to spec)) }
        onDone()
    }
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app, 44.dp, showBadge = false)
            IridescentText("Icône de ${app.label}", 22.sp, Modifier.weight(1f).padding(start = 12.dp), letterSpacing = 0.sp)
            PillButton("Fermer", onClick = onDone)
        }
        Row(Modifier.padding(vertical = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton("Choisir une image", highlighted = true) {
                host.pickImage { uri ->
                    CustomIcons.saveImage(context, uri, app.id)?.let { set(it) }
                }
            }
            if (app.id in launcher.layout.icons) PillButton("Icône d’origine") { set(null) }
        }
        if (packs.isEmpty()) {
            Text("Installe un pack d’icônes depuis le Play Store pour choisir parmi ses icônes, ou prends une image.",
                color = Holo.Muted, fontSize = 13.sp)
            return@Column
        }
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            packs.forEach { p -> PillButton(p.label, highlighted = p.packageName == pack) { pack = p.packageName } }
        }
        Spacer(Modifier.height(10.dp))
        GlassField(filter, { filter = it }, "Chercher une icône (ex. camera)", null, Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        val list = names
        if (list == null) Box(Modifier.fillMaxWidth().padding(30.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Holo.Violet, strokeWidth = 2.dp)
        } else LazyVerticalGrid(GridCells.Adaptive(68.dp), Modifier.fillMaxSize()) {
            items(shown, key = { it }) { name ->
                val p = pack ?: return@items
                val image by produceState<ImageBitmap?>(null, p, name) {
                    value = withContext(Dispatchers.IO) { CustomIcons.load(context, "pack:$p/$name", 144) }
                }
                Box(Modifier.padding(6.dp).size(56.dp).clip(CircleShape).clickable { set("pack:$p/$name") }, contentAlignment = Alignment.Center) {
                    image?.let { Image(it, name, Modifier.size(52.dp)) }
                }
            }
        }
    }
}
