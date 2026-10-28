package com.yuksel.pli

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.clip
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

@Composable
fun SettingsPanel(
    launcher: Launcher, isDefaultHome: Boolean, badgesAllowed: Boolean, modifier: Modifier,
    onClose: () -> Unit, onMakeDefault: () -> Unit, onNotificationAccess: () -> Unit, onLocation: () -> Unit,
    onWeatherChanged: () -> Unit,
) {
    val s = launcher.settings
    val scope = rememberCoroutineScope()
    var city by remember { mutableStateOf(s.city) }
    var cityStatus by remember { mutableStateOf<String?>(null) }
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            IridescentText("Réglages", 30.sp, Modifier.weight(1f), letterSpacing = (-.5).sp)
            PillButton("Fermer", onClick = onClose)
        }
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (!isDefaultHome) Section("Démarrage") {
                ActionRow("Faire de Pli l’appli d’accueil", "Pour le bouton Accueil, les raccourcis et les deux écrans", "Choisir", onMakeDefault)
            }
            Section("Apparence") {
                ToggleRow("Fond holographique", if (s.phoneWallpaper) "Ton fond d’écran du téléphone est utilisé" else "Noir profond avec halos de couleur",
                    !s.phoneWallpaper) { v -> launcher.updateSettings { it.copy(phoneWallpaper = !v) } }
                ToggleRow("Halos animés", "Les couleurs bougent quand tu inclines le téléphone", s.parallax) { v -> launcher.updateSettings { it.copy(parallax = v) } }
                ToggleRow("Icônes en bulles de verre", "Sinon, la forme d’origine des icônes", s.bubbles) { v -> launcher.updateSettings { it.copy(bubbles = v) } }
                ToggleRow("Noms des applis", null, s.labels) { v -> launcher.updateSettings { it.copy(labels = v) } }
            }
            Section("Dock") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Places dans le dock", color = Holo.Ink, fontSize = 15.sp)
                        Text("Le même dock sur les deux écrans", color = Holo.Muted, fontSize = 12.sp)
                    }
                    (3..6).forEach { n ->
                        val on = s.dockSize == n
                        Box(Modifier.padding(start = 6.dp).size(38.dp).clip(CircleShape)
                            .then(if (on) Modifier.background(androidx.compose.ui.graphics.Brush.linearGradient(Holo.Iridescent), CircleShape) else Modifier.glass(CircleShape))
                            .clickable { launcher.updateSettings { it.copy(dockSize = n) } }, contentAlignment = Alignment.Center) {
                            Text("$n", color = if (on) Color(0xFF120A22) else Holo.Soft, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            Section("Accueil") {
                ToggleRow("Ajouter les nouvelles applis", "Une appli installée apparaît sur les deux écrans", s.addNewApps) { v -> launcher.updateSettings { it.copy(addNewApps = v) } }
                ToggleRow("Glisser vers le bas en haut de l’écran", "Ouvre les notifications (ailleurs : la recherche)", s.swipeDownNotifications) { v ->
                    launcher.updateSettings { it.copy(swipeDownNotifications = v) } }
                ActionRow("Pastilles et musique en cours", if (badgesAllowed) "Accès aux notifications activé" else "Demande l’accès aux notifications",
                    if (badgesAllowed) "Ouvrir" else "Activer", onNotificationAccess)
            }
            Section("Météo") {
                Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    GlassField(city, { city = it }, "Ville (ex. Paris)", null, Modifier.fillMaxWidth(), onDone = {
                        scope.launch {
                            cityStatus = "Recherche…"
                            val found = findCity(city)
                            if (found == null) cityStatus = "Ville introuvable ou pas de connexion"
                            else {
                                launcher.updateSettings { it.copy(city = found.first, latitude = found.second, longitude = found.third) }
                                city = found.first; cityStatus = "Météo de ${found.first}"; onWeatherChanged()
                            }
                        }
                    })
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        PillButton("Valider la ville", true) {
                            scope.launch {
                                cityStatus = "Recherche…"
                                val found = findCity(city)
                                if (found == null) cityStatus = "Ville introuvable ou pas de connexion"
                                else {
                                    launcher.updateSettings { it.copy(city = found.first, latitude = found.second, longitude = found.third) }
                                    city = found.first; cityStatus = "Météo de ${found.first}"; onWeatherChanged()
                                }
                            }
                        }
                        PillButton("Ma position") {
                            launcher.updateSettings { it.copy(city = "", latitude = null, longitude = null) }
                            city = ""; cityStatus = "Position du téléphone utilisée"; onLocation()
                        }
                    }
                    cityStatus?.let { Text(it, color = Holo.Muted, fontSize = 12.sp) }
                }
            }
            val hidden = launcher.layout.hidden.toList()
            Section("Applis masquées") {
                if (hidden.isEmpty()) Text("Aucune. Maintiens une appli puis « Masquer l’appli ».", color = Holo.Muted, fontSize = 13.sp,
                    modifier = Modifier.padding(16.dp))
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
            Text("Pli · launcher holographique pour Galaxy Z Fold", color = Holo.Muted, fontSize = 12.sp,
                modifier = Modifier.padding(start = 6.dp))
            Spacer(Modifier.height(30.dp))
        }
    }
}
