package com.yuksel.pli

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.rounded.Newspaper
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.SwipeLeft
import androidx.compose.material.icons.rounded.SwipeUp
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.rounded.VerticalSplit
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class GestureHelp(val icon: ImageVector, val title: String, val text: String)

private val GESTURES = listOf(
    GestureHelp(Icons.Rounded.KeyboardArrowDown, "Glisser vers le bas", "Ouvre la recherche (applis, calcul, contacts, raccourcis, web). Tout en haut de l’écran : les notifications."),
    GestureHelp(Icons.Rounded.KeyboardArrowUp, "Glisser vers le haut", "Ouvre la liste de toutes les applis. Glisse vers le bas pour la refermer."),
    GestureHelp(Icons.Rounded.SwipeUp, "Glisser vers le haut sur une icône", "Ouvre son raccourci favori (marqué ↑ dans le menu de l’appli)."),
    GestureHelp(Icons.Rounded.TouchApp, "Appui long sur une appli", "Raccourcis, aperçu des notifications, renommer, changer l’icône, protéger, masquer…"),
    GestureHelp(Icons.Rounded.PanTool, "Appui long puis glisser", "Déplace l’appli dans la case de ton choix ; garde-la au bord de l’écran pour changer de page."),
    GestureHelp(Icons.Rounded.CreateNewFolder, "Lâcher une appli sur une autre", "Crée un dossier. Appui long sur le dossier pour le mettre en grand (2×2, toute la largeur…)."),
    GestureHelp(Icons.Rounded.Delete, "Glisser vers la barre du haut", "« Retirer de l’accueil » enlève l’icône (l’appli reste dans la liste)."),
    GestureHelp(Icons.Rounded.VerticalSplit, "Écran partagé (écran interne)", "Glisse une appli sur « Écran partagé » en haut : elle s’ouvre à côté de la dernière appli."),
    GestureHelp(Icons.Rounded.Apps, "Appui long sur une case vide", "Ajouter des applis, sélectionner, pages, widgets, verrouiller, réglages."),
    GestureHelp(Icons.Rounded.SwipeLeft, "Balayer vers la droite", "Tout à gauche : la page Actus."),
    GestureHelp(Icons.Rounded.ViewCarousel, "Widgets empilés", "Sur « Aujourd’hui », balaie une pile de widgets de côté pour passer de l’un à l’autre."),
    GestureHelp(Icons.Rounded.Home, "Bouton Accueil", "Ferme tout et revient à la première page."),
)

/** Every gesture of Pli, with a short explanation. */
@Composable
fun GestureGuide(modifier: Modifier, onClose: () -> Unit) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IridescentText("Gestes de Pli", 28.sp, Modifier.weight(1f), letterSpacing = (-.3).sp)
            PillButton("Fermer", onClick = onClose)
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GESTURES.forEach { GestureRow(it) }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun GestureRow(g: GestureHelp) {
    Row(Modifier.fillMaxWidth().glass(RoundedCornerShape(22.dp), .05f).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).glass(CircleShape, .08f), contentAlignment = Alignment.Center) {
            Icon(g.icon, null, tint = Holo.Violet, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text(g.title, color = Holo.Ink, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(g.text, color = Holo.Muted, fontSize = 13.sp, lineHeight = 18.sp)
        }
    }
}

/** First launch: home app, notifications, weather, gestures, backup. Each step can be skipped. */
@Composable
fun Onboarding(
    launcher: Launcher, isDefaultHome: Boolean, notificationsAllowed: Boolean, modifier: Modifier,
    onMakeDefault: () -> Unit, onNotificationAccess: () -> Unit, onLocation: () -> Unit, onWeatherChanged: () -> Unit,
    onImport: () -> Unit, onDone: () -> Unit,
) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    val last = 5
    Column(modifier) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Spacer(Modifier.height(20.dp))
            when (step) {
                0 -> {
                    IridescentText("Pli", 72.sp, letterSpacing = (-2).sp)
                    Text("Ton launcher holographique pour le Galaxy Z Fold.", color = Holo.Ink, fontSize = 20.sp, lineHeight = 26.sp)
                    Text("Deux écrans, deux dispositions, un dock commun, des widgets et une recherche qui apprend de toi. Quelques réglages et c’est parti.",
                        color = Holo.Muted, fontSize = 15.sp, lineHeight = 21.sp)
                }
                1 -> StepCard(Icons.Rounded.Home, "Appli d’accueil", "Pour que le bouton Accueil, les deux écrans et les raccourcis d’applis passent par Pli.",
                    done = isDefaultHome, action = "Faire de Pli l’appli d’accueil", onAction = onMakeDefault)
                2 -> StepCard(Icons.Rounded.Notifications, "Pastilles et musique", "Les pastilles de notifications, leur aperçu en appui long et la carte de musique en cours ont besoin de l’accès aux notifications. Rien ne sort de ton téléphone.",
                    done = notificationsAllowed, action = "Autoriser", onAction = onNotificationAccess)
                3 -> {
                    StepTitle(Icons.Rounded.Newspaper, "Météo", "Choisis ta ville, ou utilise la position du téléphone.")
                    WeatherSettings(launcher, onLocation, onWeatherChanged)
                }
                4 -> {
                    StepTitle(Icons.Rounded.TouchApp, "Les gestes", "Tu retrouveras ce guide dans Réglages → Paramètres.")
                    GESTURES.take(6).forEach { GestureRow(it) }
                }
                else -> {
                    StepTitle(Icons.Rounded.CheckCircle, "C’est prêt", "Tes applis principales sont déjà placées. Tu peux aussi reprendre une sauvegarde Pli.")
                    PillButton("Importer une sauvegarde Pli", onClick = onImport)
                    Text("Duo Launcher et l’accueil Samsung ne laissent pas une autre appli lire leur disposition : pour les retrouver, ajoute tes applis avec un appui long sur une case vide → « Ajouter des applis ».",
                        color = Holo.Muted, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step in 1 until last) Text("Passer", color = Holo.Muted, fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { step = last }.padding(8.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                repeat(last + 1) { i ->
                    Box(Modifier.padding(horizontal = 3.dp).size(if (i == step) 8.dp else 5.dp)
                        .background(if (i == step) Holo.Violet else Color.White.copy(alpha = .3f), CircleShape))
                }
            }
            if (step > 0) PillButton("Retour") { step-- }
            Spacer(Modifier.size(8.dp))
            PillButton(if (step == last) "Terminer" else if (step == 0) "Commencer" else "Suivant", highlighted = true) {
                if (step == last) onDone() else step++
            }
        }
    }
}

@Composable
private fun StepTitle(icon: ImageVector, title: String, text: String) {
    Box(Modifier.size(64.dp).halo(Holo.Violet.copy(alpha = .3f), 12.dp).glass(CircleShape, .1f), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = Holo.Violet, modifier = Modifier.size(30.dp))
    }
    IridescentText(title, 32.sp, letterSpacing = (-.5).sp)
    Text(text, color = Holo.Soft, fontSize = 15.sp, lineHeight = 21.sp)
}

@Composable
private fun StepCard(icon: ImageVector, title: String, text: String, done: Boolean, action: String, onAction: () -> Unit) {
    StepTitle(icon, title, text)
    if (done) Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.CheckCircle, null, tint = Holo.Mint)
        Text("C’est fait", color = Holo.Mint, fontSize = 15.sp, modifier = Modifier.padding(start = 8.dp))
    } else PillButton(action, highlighted = true, onClick = onAction)
}
