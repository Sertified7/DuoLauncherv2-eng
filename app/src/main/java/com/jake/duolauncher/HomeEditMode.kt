package com.jake.duolauncher

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * iPhone-style Home editing: icons wiggle, a "–" badge removes them and "OK" ends editing.
 * Shared through a composition local so every Home surface (grid, dock, folders) reacts the same way.
 */
internal class HomeEditMode(
    val active: Boolean,
    val removeFromHome: (String) -> Unit = {},
    val removeFromDock: (Int) -> Unit = {},
    val exit: () -> Unit = {},
)

internal val LocalHomeEditMode = staticCompositionLocalOf { HomeEditMode(false) }

/** Gentle iOS-like wiggle while editing; each icon gets its own rhythm so the grid doesn't move in lockstep. */
@Composable
internal fun Modifier.homeJiggle(key: String): Modifier {
    if (!LocalHomeEditMode.current.active) return this
    val seed = remember(key) { (key.hashCode() and 0xff) / 255f }
    val transition = rememberInfiniteTransition(label = "jiggle")
    val angle by transition.animateFloat(
        initialValue = -1.6f - seed * .6f, targetValue = 1.6f + seed * .6f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 120 + (seed * 50).toInt(), easing = LinearEasing), RepeatMode.Reverse),
        label = "jiggle $key")
    return graphicsLayer { rotationZ = angle }
}

/** The grey "–" bubble on an icon's top-left corner while editing. */
@Composable
internal fun RemoveBadge(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.size(24.dp).shadow(3.dp, CircleShape).background(Color(0xFFE5E5EA), CircleShape)
        .clickable(onClick = onClick).semantics { contentDescription = "Retirer" },
        contentAlignment = Alignment.Center) {
        Box(Modifier.width(11.dp).height(2.5.dp).background(Color(0xFF3A3A3C), RoundedCornerShape(2.dp)))
    }
}

/** Top bar shown while editing: add (widgets, wallpaper, settings) on the left, "OK" on the right. */
@Composable
internal fun HomeEditBar(onAdd: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically) {
        EditPill("+ Ajouter", onAdd)
        EditPill("OK", onDone, bold = true)
    }
}

@Composable
private fun EditPill(label: String, onClick: () -> Unit, bold: Boolean = false) {
    Surface(Modifier.heightIn(min = 40.dp).clickable(onClick = onClick), shape = RoundedCornerShape(20.dp),
        color = Color.White.copy(alpha = .22f),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = .45f))) {
        Box(Modifier.padding(horizontal = 18.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
            Text(label, color = Color.White, fontSize = 15.sp, fontWeight = if (bold) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

/**
 * iOS "Liquid Glass"-style surface: a light-to-clear gradient, a bright top rim fading downward and a
 * soft shadow, so the dock, folders and widget cards read as frosted glass over the wallpaper.
 */
internal fun Modifier.liquidGlass(shape: androidx.compose.ui.graphics.Shape, strength: Float = 1f): Modifier = this
    .shadow(12.dp, shape, ambientColor = Color.Black.copy(alpha = .22f), spotColor = Color.Black.copy(alpha = .28f))
    .then(Modifier.background(androidx.compose.ui.graphics.Brush.verticalGradient(
        listOf(Color.White.copy(alpha = .30f * strength), Color.White.copy(alpha = .10f * strength))), shape))
    .then(Modifier.border(1.dp, androidx.compose.ui.graphics.Brush.verticalGradient(
        listOf(Color.White.copy(alpha = .78f), Color.White.copy(alpha = .14f))), shape))
