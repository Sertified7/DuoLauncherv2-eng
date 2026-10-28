package com.yuksel.pli

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.Swipe
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material.icons.rounded.AddToHomeScreen
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Interests
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val section = RoundedCornerShape(26.dp)

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(title.uppercase(), color = Holo.Muted, fontSize = 11.sp, letterSpacing = 2.5.sp, modifier = Modifier.padding(start = 6.dp, bottom = 8.dp))
        Column(Modifier.fillMaxWidth().glass(section, .05f).padding(vertical = 6.dp)) { content() }
    }
}

@Composable
private fun ToggleRow(title: String, detail: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Holo.Ink, fontSize = 15.sp)
            if (detail != null) Text(detail, color = Holo.Muted, fontSize = 12.sp)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedThumbColor = Color(0xFF120A22), checkedTrackColor = Holo.Violet,
            uncheckedThumbColor = Holo.Muted, uncheckedTrackColor = Color.White.copy(alpha = .08f), uncheckedBorderColor = Color.White.copy(alpha = .2f)))
    }
}

@Composable
private fun ActionRow(title: String, detail: String?, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Holo.Ink, fontSize = 15.sp)
            if (detail != null) Text(detail, color = Holo.Muted, fontSize = 12.sp)
        }
        Text(action, color = Holo.Violet, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

/** A row of round choices; 0 shows as "Auto". */
@Composable
private fun ChoiceRow(title: String, detail: String?, options: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(title, color = Holo.Ink, fontSize = 15.sp)
        if (detail != null) Text(detail, color = Holo.Muted, fontSize = 12.sp)
        Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { n ->
                val on = selected == n
                Box(Modifier.height(38.dp).widthIn(min = 38.dp).clip(RoundedCornerShape(50))
                    .then(if (on) Modifier.background(Brush.linearGradient(Holo.Iridescent), RoundedCornerShape(50)) else Modifier.glass(RoundedCornerShape(50)))
                    .clickable { onSelect(n) }.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Text(if (n == 0) "Auto" else "$n", color = if (on) Color(0xFF120A22) else Holo.Soft, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

private enum class SettingsCategory(val label: String, val icon: ImageVector, val subs: List<Pair<String, ImageVector>>) {
    THEMES("Thèmes", Icons.Rounded.Palette, listOf("Fond" to Icons.Rounded.Wallpaper, "Icônes" to Icons.Rounded.Category, "Forme" to Icons.Rounded.Interests, "Animations" to Icons.Rounded.AutoAwesome)),
    HOME("Accueil", Icons.Rounded.PhoneAndroid, listOf("Grille" to Icons.Rounded.GridView, "Dock" to Icons.Rounded.Dock,
        "Horloge" to Icons.Rounded.Schedule, "Pages" to Icons.Rounded.ViewCarousel)),
    APPS("Applis", Icons.Rounded.Apps, listOf("Masquées" to Icons.Rounded.VisibilityOff, "Ajout" to Icons.Rounded.AddToHomeScreen)),
    WIDGETS("Widgets", Icons.Rounded.Widgets, listOf("Météo" to Icons.Rounded.WbSunny, "Notifications" to Icons.Rounded.Notifications)),
    GESTURES("Gestes", Icons.Rounded.Swipe, listOf("Gestes" to Icons.Rounded.TouchApp)),
    GENERAL("Paramètres", Icons.Rounded.Settings, listOf("Général" to Icons.Rounded.Tune, "Sauvegarde" to Icons.Rounded.Backup)),
}

/** One entry of the side rails: a round icon with its name; the chosen one glows. */
@Composable
private fun RailItem(icon: ImageVector, label: String, selected: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(52.dp).then(if (selected) Modifier.halo(Holo.Violet.copy(alpha = .3f), 8.dp)
            .background(Brush.linearGradient(Holo.Iridescent.map { it.copy(alpha = .35f) }), CircleShape)
            .border(1.dp, Holo.edgeBrush, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = if (selected) Holo.Ink else Holo.Soft, modifier = Modifier.size(26.dp))
        }
        Text(label, color = if (selected) Holo.Ink else Holo.Muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
}

/** A small live copy of the cover home screen: it follows the settings as they change. */
@Composable
private fun HomePreview(launcher: Launcher, modifier: Modifier) {
    val s = launcher.settings
    BoxWithConstraints(modifier.clip(RoundedCornerShape(26.dp)).border(1.dp, Holo.edgeBrush, RoundedCornerShape(26.dp))) {
        HoloBackground(s.phoneWallpaper, false)
        if (s.phoneWallpaper) Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2B4C6B), Color(0xFFB0573A)))))
        val columns = if (s.coverColumns > 0) s.coverColumns else 4
        val rows = if (s.coverRows > 0) s.coverRows else 5
        val pad = 10.dp
        val cell = (maxWidth - pad * 2) / columns
        val icon = (cell * .7f).coerceAtMost(40.dp)
        val apps = launcher.visibleHome(Screen.COVER).filter { it.isNotEmpty() && it != HomeLayout.COVERED }
            .mapNotNull { launcher.byId[it] ?: launcher.folderApps(it).firstOrNull() }.take(columns * rows)
        val dock = launcher.visibleDock(Screen.COVER).mapNotNull { launcher.byId[it] }
        Column(Modifier.fillMaxSize().padding(pad)) {
            Row(verticalAlignment = Alignment.Bottom) {
                IridescentText("18:42", 30.sp, Modifier.weight(1f), letterSpacing = (-1).sp)
                if (s.headerSearch != "none") Box(Modifier.padding(bottom = 6.dp).size(24.dp).glass(CircleShape, .1f), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Search, null, tint = Holo.Ink, modifier = Modifier.size(13.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            apps.chunked(columns).forEach { line ->
                Row(Modifier.fillMaxWidth()) {
                    line.forEach { app ->
                        Column(Modifier.width(cell).padding(vertical = 3.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            AppIcon(app, icon, showBadge = false)
                            if (s.labels) Text(app.label, color = Holo.Soft, fontSize = 6.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(50), .08f).padding(horizontal = 6.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceEvenly) {
                dock.take(s.dockSize).forEach { AppIcon(it, (icon * .85f), showBadge = false) }
            }
        }
    }
}

@Composable
fun SettingsPanel(
    launcher: Launcher, isDefaultHome: Boolean, badgesAllowed: Boolean, modifier: Modifier,
    onClose: () -> Unit, onMakeDefault: () -> Unit, onNotificationAccess: () -> Unit, onLocation: () -> Unit,
    onWeatherChanged: () -> Unit,
    onExport: () -> Unit = {}, onImport: () -> Unit = {}, accessibilityOn: Boolean = false, onAccessibility: () -> Unit = {},
) {
    var picking by remember { mutableStateOf<String?>(null) }
    picking?.let { which ->
        AppPick(launcher, modifier, onClose = { picking = null }) { app ->
            launcher.updateSettings { when (which) {
                "doubleTap" -> it.copy(doubleTap = "app:${app.id}")
                "twoFingerUp" -> it.copy(twoFingerUp = "app:${app.id}")
                else -> it.copy(twoFingerDown = "app:${app.id}")
            } }
            picking = null
        }
        return
    }
    var category by rememberSaveable { mutableStateOf(if (isDefaultHome) SettingsCategory.THEMES else SettingsCategory.GENERAL) }
    var sub by rememberSaveable { mutableStateOf(category.subs.first().first) }
    val options: @Composable (Modifier) -> Unit = { m ->
        Column(m.clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)).background(Holo.Panel.copy(alpha = .96f))
            .border(1.dp, Holo.edgeBrush, RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)).padding(top = 18.dp)) {
            Text(category.label, color = Holo.Muted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 22.dp))
            Text(sub, color = Holo.Ink, fontSize = 26.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 22.dp))
            Spacer(Modifier.height(10.dp))
            Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                SettingsOptions(category, sub, launcher, isDefaultHome, badgesAllowed, onMakeDefault, onNotificationAccess, onLocation,
                    onWeatherChanged, onExport, onImport, accessibilityOn, onAccessibility, onPick = { picking = it })
                Spacer(Modifier.height(24.dp))
            }
        }
    }
    val rails: @Composable (Modifier) -> Unit = { m ->
        Row(m) {
            Column(Modifier.width(80.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                SettingsCategory.entries.forEach { c ->
                    RailItem(c.icon, c.label, c == category) { category = c; sub = c.subs.first().first }
                }
            }
            HomePreview(launcher, Modifier.weight(1f).fillMaxHeight().padding(vertical = 6.dp, horizontal = 6.dp))
            Column(Modifier.width(84.dp).fillMaxHeight().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.Center) {
                category.subs.forEach { (name, icon) -> RailItem(icon, name, name == sub) { sub = name } }
            }
        }
    }
    BoxWithConstraints(modifier) {
        val wide = maxWidth > 700.dp
        val height = maxHeight
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IridescentText("Réglages", 28.sp, Modifier.weight(1f), letterSpacing = (-.5).sp)
                PillButton("Fermer", onClick = onClose)
            }
            if (wide) Row(Modifier.fillMaxSize()) {
                rails(Modifier.weight(1f).fillMaxHeight().padding(bottom = 10.dp))
                Spacer(Modifier.width(16.dp))
                options(Modifier.weight(1f).fillMaxHeight())
            } else {
                rails(Modifier.fillMaxWidth().height(height * .44f))
                Spacer(Modifier.height(10.dp))
                options(Modifier.fillMaxWidth().weight(1f))
            }
        }
    }
}

/** The options of one sub-section. */
@Composable
private fun SettingsOptions(
    category: SettingsCategory, sub: String, launcher: Launcher, isDefaultHome: Boolean, badgesAllowed: Boolean,
    onMakeDefault: () -> Unit, onNotificationAccess: () -> Unit, onLocation: () -> Unit, onWeatherChanged: () -> Unit,
    onExport: () -> Unit, onImport: () -> Unit, accessibilityOn: Boolean, onAccessibility: () -> Unit, onPick: (String) -> Unit,
) {
    val s = launcher.settings
    val context = androidx.compose.ui.platform.LocalContext.current
    when (category to sub) {
        SettingsCategory.THEMES to "Fond" -> Section("Arrière-plan") {
            ToggleRow("Fond holographique", if (s.phoneWallpaper) "Ton fond d’écran du téléphone est utilisé" else "Noir profond avec halos de couleur",
                !s.phoneWallpaper) { v -> launcher.updateSettings { it.copy(phoneWallpaper = !v) } }
            ToggleRow("Halos animés", "Les couleurs bougent quand tu inclines le téléphone", s.parallax) { v -> launcher.updateSettings { it.copy(parallax = v) } }
            ActionRow("Changer le fond d’écran du téléphone", "Utilisé quand le fond holographique est coupé", "Ouvrir") {
                runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_SET_WALLPAPER).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)) }
            }
        }
        SettingsCategory.THEMES to "Icônes" -> {
            val packs = remember { IconPacks.installed(context) }
            Section("Pack d’icônes") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text(if (packs.isEmpty()) "Installe un pack d’icônes depuis le Play Store (format Nova / ADW)" else "Les applis absentes du pack gardent leur icône",
                        color = Holo.Muted, fontSize = 12.sp)
                    Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        (listOf(IconPackInfo("", "Aucun")) + packs).forEach { p ->
                            PillButton(p.label, highlighted = s.iconPack == p.packageName) { launcher.updateSettings { it.copy(iconPack = p.packageName) } }
                        }
                    }
                }
                ToggleRow("Icônes teintées", "Monochromes aux couleurs holographiques", s.tintedIcons) { v -> launcher.updateSettings { it.copy(tintedIcons = v) } }
                ToggleRow("Noms des applis", null, s.labels) { v -> launcher.updateSettings { it.copy(labels = v) } }
            }
        }
        SettingsCategory.THEMES to "Forme" -> Section("Forme des icônes") {
            ToggleRow("Cadre de verre", "Sinon, l’icône seule dans la forme choisie", s.bubbles) { v -> launcher.updateSettings { it.copy(bubbles = v) } }
            val sample = launcher.visibleApps.firstOrNull()
            Row(Modifier.padding(12.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ICON_SHAPES.forEach { (key, name) ->
                    val on = s.iconShape == key
                    Column(Modifier.clip(RoundedCornerShape(16.dp)).clickable { launcher.updateSettings { it.copy(iconShape = key) } }
                        .then(if (on) Modifier.glass(RoundedCornerShape(16.dp), .12f) else Modifier).padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.compose.runtime.CompositionLocalProvider(LocalIconShape provides key) {
                            if (sample != null) AppIcon(sample, 44.dp, showBadge = false)
                        }
                        Text(name, color = if (on) Holo.Violet else Holo.Soft, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        SettingsCategory.THEMES to "Animations" -> Section("Ouverture des applis") {
            val choices = listOf("holo" to "Holographique", "system" to "Système")
            Text("Holographique : un anneau irisé jaillit de l’icône et l’appli grandit depuis sa bulle.", color = Holo.Muted, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.forEach { (key, name) -> PillButton(name, highlighted = s.launchAnimation == key) { launcher.updateSettings { it.copy(launchAnimation = key) } } }
            }
        }
        SettingsCategory.HOME to "Grille" -> {
            Section("Écran externe") {
                ChoiceRow("Colonnes", null, listOf(0, 3, 4, 5, 6), s.coverColumns) { v -> launcher.updateSettings { it.copy(coverColumns = v) } }
                ChoiceRow("Lignes", null, listOf(0, 4, 5, 6, 7, 8), s.coverRows) { v -> launcher.updateSettings { it.copy(coverRows = v) } }
            }
            Section("Écran interne") {
                ChoiceRow("Colonnes", null, listOf(0, 3, 4, 5, 6, 7, 8), s.innerColumns) { v -> launcher.updateSettings { it.copy(innerColumns = v) } }
                ChoiceRow("Lignes", null, listOf(0, 4, 5, 6, 7, 8, 9), s.innerRows) { v -> launcher.updateSettings { it.copy(innerRows = v) } }
            }
        }
        SettingsCategory.HOME to "Dock" -> Section("Dock") {
            ChoiceRow("Écran externe", "Les mêmes en premier sur l’écran interne", (3..6).toList(), s.dockSize) { v ->
                launcher.updateSettings { it.copy(dockSize = v, innerDockSize = maxOf(v, it.innerDockSize)) } }
            ChoiceRow("Écran interne", "Jusqu’à 10 ; celles en plus n’apparaissent que dépliées", (s.dockSize..10).toList(), s.innerDockSize.coerceAtLeast(s.dockSize)) { v ->
                launcher.updateSettings { it.copy(innerDockSize = v) } }
            ChoiceRow("Applis récentes au bout du dock (interne)", null, listOf(0, 1, 2, 3), s.recentInDock) { v -> launcher.updateSettings { it.copy(recentInDock = v) } }
        }
        SettingsCategory.HOME to "Horloge" -> Section("À droite de l’horloge") {
            val choices = listOf("google" to "Recherche Google", "finder" to "Finder Samsung", "pli" to "Recherche Pli", "none" to "Rien")
            Row(Modifier.padding(16.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                choices.forEach { (key, name) -> PillButton(name, highlighted = s.headerSearch == key) { launcher.updateSettings { it.copy(headerSearch = key) } } }
            }
        }
        SettingsCategory.HOME to "Pages" -> Section("Pages d’accueil") {
            ToggleRow("Verrouiller la disposition", "Empêche de déplacer ou retirer des icônes par erreur", s.lockLayout) { v -> launcher.updateSettings { it.copy(lockLayout = v) } }
            ToggleRow("Page Actus", "Tout à gauche : Google Actualités, Le Monde, franceinfo", s.discover) { v -> launcher.updateSettings { it.copy(discover = v) } }
            ToggleRow("Ajouter les nouvelles applis", "Une appli installée apparaît sur les deux écrans", s.addNewApps) { v -> launcher.updateSettings { it.copy(addNewApps = v) } }
        }
        SettingsCategory.APPS to "Masquées" -> Section("Applis masquées") {
            val hidden = launcher.layout.hidden.toList()
            if (hidden.isEmpty()) Text("Aucune. Maintiens une appli puis « Masquer l’appli ».", color = Holo.Muted, fontSize = 13.sp, modifier = Modifier.padding(16.dp))
            hidden.forEach { id ->
                val app = launcher.byId[id]
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (app != null) AppIcon(app, 36.dp, showBadge = false)
                    Text(app?.label ?: id.substringBefore('/'), color = Holo.Ink, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp))
                    PillButton("Afficher") { launcher.unhide(id) }
                }
            }
        }
        SettingsCategory.APPS to "Ajout" -> Section("Nouvelles applis") {
            ToggleRow("Ajouter à l’accueil à l’installation", "Sur les deux écrans, dans la première case libre", s.addNewApps) { v -> launcher.updateSettings { it.copy(addNewApps = v) } }
            Text("Pour en ajouter plusieurs : appui long sur une case vide → « Ajouter des applis ».", color = Holo.Muted, fontSize = 12.sp,
                modifier = Modifier.padding(16.dp))
        }
        SettingsCategory.WIDGETS to "Météo" -> WeatherSettings(launcher, onLocation, onWeatherChanged)
        SettingsCategory.WIDGETS to "Notifications" -> Section("Notifications") {
            ActionRow("Pastilles, aperçus et musique en cours", if (badgesAllowed) "Accès aux notifications activé" else "Demande l’accès aux notifications",
                if (badgesAllowed) "Ouvrir" else "Activer", onNotificationAccess)
        }
        SettingsCategory.GESTURES to "Gestes" -> Section("Gestes sur l’accueil") {
            ToggleRow("Glisser vers le bas en haut de l’écran", "Ouvre les notifications (ailleurs : la recherche)", s.swipeDownNotifications) { v ->
                launcher.updateSettings { it.copy(swipeDownNotifications = v) } }
            GestureRow("Double appui sur un espace vide", s.doubleTap, launcher, onPick = { onPick("doubleTap") }) { v -> launcher.updateSettings { it.copy(doubleTap = v) } }
            GestureRow("Glisser à deux doigts vers le haut", s.twoFingerUp, launcher, onPick = { onPick("twoFingerUp") }) { v -> launcher.updateSettings { it.copy(twoFingerUp = v) } }
            GestureRow("Glisser à deux doigts vers le bas", s.twoFingerDown, launcher, onPick = { onPick("twoFingerDown") }) { v -> launcher.updateSettings { it.copy(twoFingerDown = v) } }
            if (listOf(s.doubleTap, s.twoFingerUp, s.twoFingerDown).contains("lock") && !accessibilityOn)
                ActionRow("Verrouiller demande une autorisation", "Active « Pli – verrouillage par geste » dans Accessibilité", "Activer", onAccessibility)
        }
        SettingsCategory.GENERAL to "Général" -> Section("Pli") {
            if (!isDefaultHome) ActionRow("Faire de Pli l’appli d’accueil", "Pour le bouton Accueil, les raccourcis et les deux écrans", "Choisir", onMakeDefault)
            else Text("Pli est ton appli d’accueil.", color = Holo.Soft, fontSize = 14.sp, modifier = Modifier.padding(16.dp))
            ToggleRow("Vibrations", "Petit retour haptique sur les appuis longs et les déplacements", s.haptics) { v -> launcher.updateSettings { it.copy(haptics = v) } }
            Text("Pli · launcher holographique pour Galaxy Z Fold", color = Holo.Muted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        SettingsCategory.GENERAL to "Sauvegarde" -> Section("Sauvegarde") {
            ActionRow("Exporter ma disposition", "Pages, dossiers, dock, widgets Pli et réglages dans un fichier", "Exporter", onExport)
            ActionRow("Importer une disposition", "Remplace la disposition actuelle", "Importer", onImport)
        }
        else -> Unit
    }
}

@Composable
private fun WeatherSettings(launcher: Launcher, onLocation: () -> Unit, onWeatherChanged: () -> Unit) {
    val scope = rememberCoroutineScope()
    var city by remember { mutableStateOf(launcher.settings.city) }
    var cityStatus by remember { mutableStateOf<String?>(null) }
    fun validate() = scope.launch {
        cityStatus = "Recherche…"
        val found = findCity(city)
        if (found == null) cityStatus = "Ville introuvable ou pas de connexion"
        else {
            launcher.updateSettings { it.copy(city = found.first, latitude = found.second, longitude = found.third) }
            city = found.first; cityStatus = "Météo de ${found.first}"; onWeatherChanged()
        }
    }
    Section("Ville de la météo") {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassField(city, { city = it }, "Ville (ex. Paris)", null, Modifier.fillMaxWidth(), onDone = { validate() })
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                PillButton("Valider la ville", true) { validate() }
                PillButton("Ma position") {
                    launcher.updateSettings { it.copy(city = "", latitude = null, longitude = null) }
                    city = ""; cityStatus = "Position du téléphone utilisée"; onLocation()
                }
            }
            cityStatus?.let { Text(it, color = Holo.Muted, fontSize = 12.sp) }
        }
    }
}

private val GESTURE_ACTIONS = listOf("none" to "Rien", "lock" to "Verrouiller", "notifications" to "Notifications",
    "search" to "Recherche", "drawer" to "Toutes les applis")

@Composable
private fun GestureRow(title: String, value: String, launcher: Launcher, onPick: () -> Unit, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(title, color = Holo.Ink, fontSize = 15.sp)
        Row(Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            val app = if (value.startsWith("app:")) launcher.byId[value.removePrefix("app:")] else null
            (GESTURE_ACTIONS + ("app" to (app?.label ?: "Ouvrir une appli…"))).forEach { (key, label) ->
                val on = value == key || (key == "app" && value.startsWith("app:"))
                Box(Modifier.height(38.dp).clip(RoundedCornerShape(50))
                    .then(if (on) Modifier.background(Brush.linearGradient(Holo.Iridescent), RoundedCornerShape(50)) else Modifier.glass(RoundedCornerShape(50)))
                    .clickable { if (key == "app") onPick() else onSelect(key) }.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center) {
                    Text(label, color = if (on) Color(0xFF120A22) else Holo.Soft, fontSize = 14.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun AppPick(launcher: Launcher, modifier: Modifier, onClose: () -> Unit, onPick: (AppInfo) -> Unit) {
    var filter by remember { mutableStateOf("") }
    val apps = launcher.visibleApps
    val shown = remember(filter, apps) { if (filter.isBlank()) apps else matchApps(apps, filter) }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IridescentText("Choisir une appli", 26.sp, Modifier.weight(1f), letterSpacing = 0.sp)
            PillButton("Retour", onClick = onClose)
        }
        GlassField(filter, { filter = it }, "Filtrer", null, Modifier.fillMaxWidth())
        Column(Modifier.padding(top = 10.dp).verticalScroll(rememberScrollState())) {
            shown.forEach { app ->
                Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(18.dp)).clickable { onPick(app) }
                    .padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(app, 40.dp, showBadge = false)
                    Text(app.label, color = Holo.Ink, fontSize = 15.sp, modifier = Modifier.padding(start = 14.dp))
                }
            }
        }
    }
}
