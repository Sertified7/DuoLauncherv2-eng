package com.jake.duolauncher

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun LayoutRestorePreview(preview: LayoutImportPreview, onRestore: () -> Unit, onCancel: () -> Unit) {
    AlertDialog(onDismissRequest = onCancel, modifier = Modifier.testTag("layout-restore-preview"),
        title = { Text("Vérifier la disposition restaurée") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${preview.appCount} apps · ${preview.folderCount} folders · ${preview.widgetCount} widgets")
                if (preview.layout.leadingSlots.any { it != null } || preview.layout.widgetPlacements.any { it.page == -1 })
                    Text("Inclut votre page réservée à l’écran déplié.", style = MaterialTheme.typography.bodySmall)
                Text("Cela restaure aussi la disposition des icônes, les libellés, la recherche et les réglages d’état.")
                Text("La photo d’arrière-plan choisie n’est pas incluse dans les sauvegardes de disposition.",
                    style = MaterialTheme.typography.bodySmall)
                if (preview.missingApps.isNotEmpty()) {
                    Text("Applis indisponibles (${preview.missingApps.size})", style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error)
                    preview.missingApps.forEach { saved ->
                        val label = saved.substringAfterLast('(').removeSuffix(")").takeIf { it.isNotBlank() } ?: "Appli indisponible"
                        Text("• $label — its saved position will stay empty")
                    }
                }
                if (preview.profileIssues.isNotEmpty()) {
                    Text("Attention au profil", style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.error)
                    preview.profileIssues.forEach { Text("• $it") }
                }
                val reconnect = preview.layout.widgetPlacements.count { it.id == NEEDS_BINDING_WIDGET }
                if (reconnect > 0) Text("$reconnect widget${if (reconnect == 1) "" else "s"} : emplacement conservé, reconnexion demandée après la restauration.")
                Text("Rien ne change tant que vous ne choisissez pas Restaurer.", style = MaterialTheme.typography.bodySmall)
            }
        }, confirmButton = { Button(onClick = onRestore, modifier = Modifier.testTag("layout-restore-apply")) { Text("Restaurer") } },
        dismissButton = { TextButton(onClick = onCancel, modifier = Modifier.testTag("layout-restore-cancel")) { Text("Annuler") } })
}
