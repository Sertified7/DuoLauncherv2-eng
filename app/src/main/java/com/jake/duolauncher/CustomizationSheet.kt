package com.jake.duolauncher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal enum class CustomizationPage { OVERVIEW, WALLPAPER, HOME, GESTURES, BACKUP, HELP }

@Composable
internal fun CustomizationSheet(state: LauncherState, initiallyWide: Boolean, model: LauncherModel,
    isDefaultHome: Boolean, page: CustomizationPage, onPage: (CustomizationPage) -> Unit,
    onMakeDefault: () -> Unit, onClose: () -> Unit, onEditPins: () -> Unit, onWidget: (Int) -> Unit,
    onAddWidget: (Int) -> Unit, onRemoveWidget: (Int) -> Unit, onWallpaperPreview: () -> Unit,
    onExportLayout: () -> Unit, onImportLayout: () -> Unit,
    appearance: AppearanceState, onAppearanceMode: (AppearanceMode) -> Unit,
    onAppearanceManual: (String, Double, Double) -> Unit, onAppearanceDeviceLocation: () -> Unit,
    onAppearanceClear: () -> Unit, backgrounds: LauncherBackgroundController, homePage: Int = 0,
    onShadeSetup: () -> Unit = {},
) {
    var wide by rememberSaveable { mutableStateOf(initiallyWide) }
    val title = when (page) {
        CustomizationPage.OVERVIEW -> "Personnalisez-le"
        CustomizationPage.WALLPAPER -> "Fond d’écran et apparence"
        CustomizationPage.HOME -> "Disposition de l’accueil"
        CustomizationPage.GESTURES -> "Gestes et recherche"
        CustomizationPage.BACKUP -> "Sauvegarde"
        CustomizationPage.HELP -> "Aide et configuration"
    }
    val bodyScroll = rememberScrollState()
    LaunchedEffect(page) { bodyScroll.scrollTo(0) }
    Column(Modifier.fillMaxWidth().fillMaxHeight(.92f).padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
            if (page != CustomizationPage.OVERVIEW) IconButton(onClick = { onPage(CustomizationPage.OVERVIEW) },
                Modifier.testTag("customization-back")) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Fermer la personnalisation") }
        }
        Column(Modifier.weight(1f).verticalScroll(bodyScroll).padding(bottom = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            when (page) {
                CustomizationPage.OVERVIEW -> {
                    if (!isDefaultHome) Button(onClick = onMakeDefault, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("default-home-settings")) { Text("Définir comme appli d’accueil") }
                    if (state.canUndoEdit) OutlinedButton(onClick = { model.undoEdit(); onClose() },
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Annuler la dernière modification") }
                    MiniHomePreview(backgrounds.previewBitmap, state, 176.dp)
                    CustomizationDestination(Icons.Rounded.Wallpaper, "Fond d’écran et apparence",
                        if (backgrounds.previewPending) "Photo prête à vérifier" else "Arrière-plan, couleurs et luminosité",
                        "customization-wallpaper") { onPage(CustomizationPage.WALLPAPER) }
                    CustomizationDestination(Icons.Rounded.GridView, "Disposition de l’accueil",
                        "Icônes, espacement, dock et widgets", "customization-home") { onPage(CustomizationPage.HOME) }
                    CustomizationDestination(Icons.Rounded.Search, "Gestes et recherche",
                        "Libellés, état et recherche", "customization-gestures") { onPage(CustomizationPage.GESTURES) }
                    CustomizationDestination(Icons.Rounded.Save, "Sauvegarde",
                        "Enregistrer ou restaurer cette disposition", "customization-backup") { onPage(CustomizationPage.BACKUP) }
                    CustomizationDestination(Icons.Rounded.HelpOutline, "Aide et configuration",
                        "Appli d’accueil, widgets, gestes et Discover", "customization-help") {
                        onPage(CustomizationPage.HELP)
                    }
                    if (isDefaultHome) TextButton(onClick = onMakeDefault, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("default-home-settings")) { Text("Changer d’appli d’accueil") }
                }
                CustomizationPage.WALLPAPER -> {
                    MiniHomePreview(backgrounds.previewBitmap, state, 228.dp)
                    Text("Arrière-plan du lanceur", style = MaterialTheme.typography.titleMedium)
                    Text("Change l’image derrière les écrans d’accueil de Duo.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = backgrounds::choosePhoto, enabled = !backgrounds.loading,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("background-choose")) {
                        Text(if (backgrounds.previewPending) "Choisir une autre photo" else "Choisir une photo")
                    }
                    if (backgrounds.previewPending) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = backgrounds::cancelPreview, Modifier.weight(1f).heightIn(min = 48.dp)
                            .testTag("background-preview-cancel")) { Text("Annuler") }
                        Button(onClick = backgrounds::applyPreview, enabled = backgrounds.previewBitmap != null,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("background-preview-apply")) { Text("Appliquer") }
                    }
                    if (backgrounds.photoSelected && !backgrounds.previewPending) OutlinedButton(onClick = backgrounds::reset,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("background-reset")) { Text("Revenir aux dunes Duo") }
                    if (backgrounds.loading) LinearProgressIndicator(Modifier.fillMaxWidth().testTag("background-loading"))
                    (backgrounds.errorMessage ?: backgrounds.successMessage)?.let { message ->
                        TextButton(onClick = backgrounds::clearMessage, Modifier.fillMaxWidth().testTag("background-message")) { Text(message) }
                    }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Text("Fond d’écran Android", style = MaterialTheme.typography.titleMedium)
                    Text("Ouvre l’aperçu d’Android pour changer le fond d’écran du téléphone. L’arrière-plan du lanceur Duo n’est pas modifié.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    OutlinedButton(onClick = onWallpaperPreview, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        .testTag("wallpaper-preview")) { Icon(Icons.Rounded.Wallpaper, null); Spacer(Modifier.width(8.dp)); Text("Aperçu du fond d’écran Android") }
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    AppearanceSettings(appearance, onAppearanceMode, onAppearanceManual, onAppearanceDeviceLocation, onAppearanceClear)
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    Text("Style des icônes", style = MaterialTheme.typography.titleSmall)
                    val iconCtx = androidx.compose.ui.platform.LocalContext.current
                    LaunchedEffect(Unit) { IconStyleStore.load(iconCtx) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconStyle.entries.forEach { style ->
                            FilterChip(IconStyleStore.style.value == style, { IconStyleStore.set(iconCtx, style) }, label = { Text(style.label) })
                        }
                    }
                }
                CustomizationPage.HOME -> HomeLayoutSettings(state, wide, { wide = it }, model, homePage,
                    onEditPins, onWidget, onAddWidget, onRemoveWidget)
                CustomizationPage.GESTURES -> {
                    SettingsSwitch("Afficher le nom des applis", state.labels, model::setLabels, "label-switch")
                    SettingsSwitch("Afficher l’état en haut à droite", state.verticalStatus, model::setVerticalStatus, "status-switch")
                    SettingsSwitch("Le bouton Recherche ouvre Google", state.googleSearch, model::setGoogleSearch, "google-search-switch")
                    val context = androidx.compose.ui.platform.LocalContext.current
                    var standBy by remember { mutableStateOf(isStandByEnabled(context)) }
                    SettingsSwitch("Mode StandBy", standBy, { standBy = it; setStandByEnabled(context, it) }, "standby-switch")
                    Text("Écran externe, en charge et posé debout à l’horizontale : grande horloge, météo, agenda et réveil. Touchez pour quitter.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    var calendar by remember { mutableStateOf(hasCalendarAccess(context)) }
                    val askCalendar = androidx.activity.compose.rememberLauncherForActivityResult(
                        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()) { granted -> calendar = granted }
                    SettingsSwitch("Agenda dans StandBy", calendar, { wanted ->
                        if (wanted && !calendar) askCalendar.launch(android.Manifest.permission.READ_CALENDAR)
                        else if (!wanted && calendar) context.startActivity(android.content.Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.fromParts("package", context.packageName, null)))
                    }, "standby-calendar-switch")
                    Text("La météo utilise le lieu choisi dans Apparence, sinon la position approximative.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    // Notification badges need Android's notification access, granted in system settings.
                    var badgeAccess by remember { mutableStateOf(BadgeService.hasAccess(context)) }
                    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
                    DisposableEffect(lifecycle) {
                        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) badgeAccess = BadgeService.hasAccess(context)
                        }
                        lifecycle.addObserver(observer); onDispose { lifecycle.removeObserver(observer) }
                    }
                    SettingsSwitch("Pastilles de notification", badgeAccess, {
                        runCatching { context.startActivity(android.content.Intent(android.provider.Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
                    }, "badges-switch")
                    Text("Affiche le nombre de notifications sur les icônes. Activez « Pastilles Duo Launcher » dans l’accès aux notifications.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    HorizontalDivider(Modifier.padding(vertical = 6.dp))
                    val hiddenApps = state.apps.filter { it.id in state.hidden }
                    Text("Applis masquées", style = MaterialTheme.typography.titleSmall)
                    if (hiddenApps.isEmpty()) Text("Aucune. Pour masquer une appli : appui long sur son icône, puis « Masquer l’appli ».",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    hiddenApps.forEach { app ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(app.label, Modifier.weight(1f))
                            TextButton(onClick = { model.setHidden(app.id, false) }) { Text("Afficher") }
                        }
                    }
                    Text("« Toutes les applis » garde toujours la recherche locale.", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("Balayez latéralement pour changer de page. Balayez vers le bas depuis le haut de l’écran pour les notifications, ou depuis le milieu pour la recherche (applis, contacts, réglages, calculs).",
                        style = MaterialTheme.typography.bodyMedium)
                }
                CustomizationPage.BACKUP -> {
                    Text("Enregistrez la disposition actuelle de l’accueil, les dossiers, les widgets et les réglages de disposition.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = onExportLayout, Modifier.weight(1f).heightIn(min = 48.dp).testTag("layout-export")) { Text("Enregistrer") }
                        Button(onClick = onImportLayout, Modifier.weight(1f).heightIn(min = 48.dp).testTag("layout-import")) { Text("Restaurer") }
                    }
                    Text("La restauration affiche un aperçu avant de modifier l’accueil.", style = MaterialTheme.typography.bodySmall)
                }
                CustomizationPage.HELP -> LauncherHelp(
                    isDefaultHome = isDefaultHome,
                    onHomeSettings = onMakeDefault,
                    onAddWidget = { onAddWidget(homePage) },
                    onShadeSetup = onShadeSetup,
                )
            }
        }
    }
}

@Composable
private fun LauncherHelp(
    isDefaultHome: Boolean,
    onHomeSettings: () -> Unit,
    onAddWidget: () -> Unit,
    onShadeSetup: () -> Unit,
) {
    HelpSection(Icons.Rounded.Home, "Appli d’accueil",
        if (isDefaultHome) "Duo est votre appli d’accueil. Vous pouvez changer de lanceur dans les paramètres d’accueil d’Android."
        else "Choisissez Duo dans les paramètres d’accueil d’Android pour l’utiliser quand vous appuyez sur Accueil.")
    Button(onClick = onHomeSettings, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("help-home-settings")) {
        Text(if (isDefaultHome) "Changer d’appli d’accueil" else "Définir Duo comme accueil")
    }
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    HelpSection(Icons.Rounded.TouchApp, "Personnaliser n’importe quelle page",
        "Appuyez longuement sur un espace vide, puis choisissez Personnaliser le lanceur. Si une page est pleine, appuyez longuement sur la fine zone à son bord gauche.")
    HelpSection(Icons.Rounded.Widgets, "Widgets",
        "Ajoutez des widgets Android dans les cases vides de l’accueil. Maintenez un widget pour le déplacer ou le retirer.")
    OutlinedButton(onClick = onAddWidget, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("help-add-widget")) {
        Text("Ajouter un widget à cette page")
    }
    HorizontalDivider(Modifier.padding(vertical = 4.dp))
    HelpSection(Icons.Rounded.SwipeDown, "Notifications et réglages rapides",
        "Balayez vers le bas sur l’accueil. La première fois, Duo explique le réglage d’accessibilité facultatif d’Android. Le service ouvre seulement les panneaux système.")
    TextButton(onClick = onShadeSetup, Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("help-shade-setup")) {
        Text("Configurer les gestes du volet")
    }
    HelpSection(Icons.Rounded.Explore, "Discover",
        "Balayez vers la droite depuis la première page d’accueil pour ouvrir l’appli Google et son fil Discover.")
}

@Composable
private fun HelpSection(icon: ImageVector, title: String, detail: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, Modifier.padding(top = 2.dp).size(22.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(detail, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable private fun CustomizationDestination(icon: ImageVector, title: String, detail: String, tag: String, onClick: () -> Unit) {
    Surface(onClick = onClick, modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag(tag),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .52f), shape = RoundedCornerShape(20.dp)) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) { Text(title, style = MaterialTheme.typography.titleMedium); Text(detail,
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            Icon(Icons.Rounded.ChevronRight, null)
        }
    }
}

@Composable private fun MiniHomePreview(stagedBitmap: android.graphics.Bitmap?, state: LauncherState,
    previewHeight: androidx.compose.ui.unit.Dp) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val backgroundRevision = LauncherBackgroundCache.revision.intValue
    val committedBitmap = remember(backgroundRevision) { cachedLauncherBackground(context) }
    val bitmap = stagedBitmap ?: committedBitmap
    val apps = remember(state.apps) { state.apps.associateBy { it.id } }
    val homeIcons = state.homeSlots.mapNotNull { id -> id?.let(apps::get) }.take(8)
    val dockIcons = state.dock.mapNotNull { id -> id?.let(apps::get) }.take(5)
    val scale = previewHeight.value * .632f / 250f
    fun unit(value: Float) = (value * scale).dp
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(Modifier.height(previewHeight).width(previewHeight * .632f).clip(RoundedCornerShape(unit(24f)))
            .testTag("customization-home-preview")) {
            DuneWallpaper()
            bitmap?.let { Image(it.asImageBitmap(), null, Modifier.matchParentSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.Crop) }
            Column(Modifier.fillMaxSize().padding(start = unit(16f), top = unit(18f), end = unit(54f)),
                verticalArrangement = Arrangement.spacedBy(unit(10f))) {
                Box(Modifier.fillMaxWidth().height(unit(42f)).background(MaterialTheme.colorScheme.surface.copy(alpha = .38f), RoundedCornerShape(unit(12f))))
                homeIcons.chunked(4).forEach { row -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    row.forEach { app -> Image(app.icon.asImageBitmap(), null, Modifier.size(unit(24f)).clip(RoundedCornerShape(unit(7f)))) }
                } }
            }
            Column(Modifier.align(Alignment.CenterEnd).padding(end = unit(10f)).width(unit(36f))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = .42f), RoundedCornerShape(unit(18f)))
                .padding(vertical = unit(8f)), horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(unit(8f))) {
                dockIcons.forEach { app -> Image(app.icon.asImageBitmap(), null, Modifier.size(unit(22f)).clip(RoundedCornerShape(unit(7f)))) }
            }
        }
    }
}

@Composable private fun HomeLayoutSettings(state: LauncherState, wide: Boolean, onWide: (Boolean) -> Unit,
    model: LauncherModel, homePage: Int, onEditPins: () -> Unit, onWidget: (Int) -> Unit,
    onAddWidget: (Int) -> Unit, onRemoveWidget: (Int) -> Unit) {
    val p = if (wide) state.expanded else state.compact
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(!wide, { onWide(false) }, label = { Text("Écran externe") })
        FilterChip(wide, { onWide(true) }, label = { Text("Écran interne") })
    }
    OutlinedButton(onClick = onEditPins, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Choisir les applis de l’accueil") }
    CustomizationSlider("Taille des icônes", "${p.iconSize.toInt()} dp", p.iconSize, 40f..68f) { model.setPreset(wide, p.copy(iconSize = it)) }
    CustomizationSlider("Espace entre les rangées", "${p.rowGap.toInt()} dp", p.rowGap, 0f..28f) { model.setPreset(wide, p.copy(rowGap = it)) }
    CustomizationSlider("Largeur du dock", "${p.dockWidth.toInt()} dp", p.dockWidth, 56f..84f) { model.setPreset(wide, p.copy(dockWidth = it)) }
    DockSizeSetting(state, wide, model)
    SettingsSwitch("Aligner le dock sur les rangées d’applis", p.dockAlignToGrid, { model.setPreset(wide, p.copy(dockAlignToGrid = it)) })
    if (!p.dockAlignToGrid) CustomizationSlider("Hauteur du dock à l’écran", "${(p.dockPosition * 100).toInt()}%", p.dockPosition, .25f.. .75f) { model.setPreset(wide, p.copy(dockPosition = it)) }
    TextButton(onClick = { model.setPreset(wide, LayoutPreset()) }, Modifier.fillMaxWidth()) { Text("Réinitialiser cette disposition") }
    HorizontalDivider(Modifier.padding(vertical = 6.dp))
    Text("Widgets · Page ${homePage + 1}", style = MaterialTheme.typography.titleMedium)
    state.widgetPlacements.filter { it.page == homePage || (wide && it.page == -1) }.forEach { placement ->
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (placement.page == -1) "Page de l’écran déplié" else "Widget ${placement.spanX} × ${placement.spanY} · rangée ${placement.row + 1}", Modifier.weight(1f))
            IconButton(onClick = { onRemoveWidget(placement.slot) }, modifier = Modifier.semantics { contentDescription = if (placement.page == -1) "Retirer le widget de la page de l’écran déplié" else "Retirer le widget" }) { Icon(Icons.Rounded.DeleteOutline, null) }
            TextButton(onClick = { onWidget(placement.slot) }) { Text("Remplacer") }
        }
    }
    TextButton(onClick = { onAddWidget(homePage) }, Modifier.fillMaxWidth().heightIn(min = 48.dp)) { Text("Ajouter un widget à cette page") }
}

@Composable private fun SettingsSwitch(label: String, checked: Boolean, onChecked: (Boolean) -> Unit, tag: String? = null) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f)); Switch(checked, onChecked, Modifier.then(if (tag != null) Modifier.testTag(tag) else Modifier))
    }
}

/** The dock is shared by both screens and sizes itself to the apps it holds. */
@Composable internal fun DockSizeSetting(@Suppress("UNUSED_PARAMETER") state: LauncherState,
    @Suppress("UNUSED_PARAMETER") wide: Boolean, @Suppress("UNUSED_PARAMETER") model: LauncherModel) {
    Text("Le dock s’agrandit ou rétrécit selon le nombre d’applis (8 maximum) et il est le même sur les deux écrans.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable private fun CustomizationSlider(label: String, valueLabel: String, value: Float,
    range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column { Row { Text(label, Modifier.weight(1f)); Text(valueLabel, color = MaterialTheme.colorScheme.primary) }
        Slider(value, onChange, valueRange = range, modifier = Modifier.semantics { contentDescription = label }) }
}
