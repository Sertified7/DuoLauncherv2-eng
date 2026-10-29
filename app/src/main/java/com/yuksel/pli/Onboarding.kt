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
    GestureHelp(Icons.Rounded.KeyboardArrowDown, "Swipe down", "Opens search (apps, calculator, contacts, shortcuts, web). From the very top of the screen: notifications."),
    GestureHelp(Icons.Rounded.KeyboardArrowUp, "Swipe up", "Opens the all-apps drawer. Swipe down to close it."),
    GestureHelp(Icons.Rounded.SwipeUp, "Swipe up on an icon", "Opens its favorite shortcut (marked with ↑ in the app menu)."),
    GestureHelp(Icons.Rounded.TouchApp, "Long-press an app", "Shortcuts, notification preview, rename, change icon, lock, hide…"),
    GestureHelp(Icons.Rounded.PanTool, "Long-press and drag", "Moves the app to the slot of your choice; hold it at the edge of the screen to change pages."),
    GestureHelp(Icons.Rounded.CreateNewFolder, "Drop an app onto another", "Creates a folder. Long-press the folder to enlarge it (2×2, full width…)."),
    GestureHelp(Icons.Rounded.Delete, "Drag to the top bar", "\"Remove from Home\" removes the icon (the app stays in the app drawer)."),
    GestureHelp(Icons.Rounded.VerticalSplit, "Split screen (inner screen)", "Drag an app to \"Split screen\" at the top: it opens next to the last active app."),
    GestureHelp(Icons.Rounded.Apps, "Long-press an empty slot", "Add apps, select multiple, pages, widgets, lock, settings."),
    GestureHelp(Icons.Rounded.SwipeLeft, "Swipe right", "On the far left: the Discover / Today page."),
    GestureHelp(Icons.Rounded.ViewCarousel, "Stacked widgets", "On \"Today\", swipe a widget stack sideways to switch between them."),
    GestureHelp(Icons.Rounded.Home, "Home button", "Closes all panels and returns to the first page."),
)

/** Every gesture of Pli, with a short explanation. */
@Composable
fun GestureGuide(modifier: Modifier, onClose: () -> Unit) {
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IridescentText("Fold Launcher Pro Gestures", 28.sp, Modifier.weight(1f), letterSpacing = (-.3).sp)
            PillButton("Close", onClick = onClose)
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
                    IridescentText("Fold Launcher", 52.sp, letterSpacing = (-1.5).sp)
                    IridescentText("Pro", 52.sp, letterSpacing = (-1.5).sp)
                    Text("Your holographic launcher for the Galaxy Z Fold.", color = Holo.Ink, fontSize = 20.sp, lineHeight = 26.sp)
                    Text("Two screens, two layouts, a shared dock, widgets, and search that learns from you. Just a few settings and you're ready to go.",
                        color = Holo.Muted, fontSize = 15.sp, lineHeight = 21.sp)
                }
                1 -> StepCard(Icons.Rounded.Home, "Default home app", "So the Home button, both screens, and app shortcuts work seamlessly with Fold Launcher Pro.",
                    done = isDefaultHome, action = "Set Fold Launcher Pro as default home", onAction = onMakeDefault)
                2 -> StepCard(Icons.Rounded.Notifications, "Badges and media", "Notification badges, long-press previews, and the now playing media card require notification access. Nothing leaves your phone.",
                    done = notificationsAllowed, action = "Allow", onAction = onNotificationAccess)
                3 -> {
                    StepTitle(Icons.Rounded.Newspaper, "Weather", "Choose your city, or use your phone's location.")
                    WeatherSettings(launcher, onLocation, onWeatherChanged)
                }
                4 -> {
                    StepTitle(Icons.Rounded.TouchApp, "Gestures", "You can find this guide anytime in Settings → Preferences.")
                    GESTURES.take(6).forEach { GestureRow(it) }
                }
                else -> {
                    StepTitle(Icons.Rounded.CheckCircle, "All set", "Your main apps are already placed. You can also restore a Fold Launcher Pro backup.")
                    PillButton("Import a Fold Launcher Pro backup", onClick = onImport)
                    Text("Other launchers and One UI Home don't allow apps to read their layouts: to set up your icons, long-press an empty slot → \"Add apps\".",
                        color = Holo.Muted, fontSize = 13.sp, lineHeight = 18.sp)
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (step in 1 until last) Text("Skip", color = Holo.Muted, fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { step = last }.padding(8.dp))
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.Center) {
                repeat(last + 1) { i ->
                    Box(Modifier.padding(horizontal = 3.dp).size(if (i == step) 8.dp else 5.dp)
                        .background(if (i == step) Holo.Violet else Color.White.copy(alpha = .3f), CircleShape))
                }
            }
            if (step > 0) PillButton("Back") { step-- }
            Spacer(Modifier.size(8.dp))
            PillButton(if (step == last) "Finish" else if (step == 0) "Get started" else "Next", highlighted = true) {
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
        Text("Done", color = Holo.Mint, fontSize = 15.sp, modifier = Modifier.padding(start = 8.dp))
    } else PillButton(action, highlighted = true, onClick = onAction)
}
