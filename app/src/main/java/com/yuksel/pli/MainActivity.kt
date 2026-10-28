package com.yuksel.pli

import android.app.ActivityOptions
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Rect as AndroidRect
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.geometry.Rect
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class MainActivity : ComponentActivity(), PliHost {
    override lateinit var launcher: Launcher
    override lateinit var widgets: Widgets
    private val signals = HostSignals()

    private val permission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { signals.resumes++ }
    private val exportFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            val ok = runCatching {
                contentResolver.openOutputStream(uri)?.use { it.write(launcher.store.export().toString(2).toByteArray()) } != null
            }.getOrDefault(false)
            toast(if (ok) "Disposition exportée" else "Export impossible")
        }
    }
    private val importFile = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) lifecycleScope.launch {
            val json = runCatching { contentResolver.openInputStream(uri)?.use { org.json.JSONObject(it.bufferedReader().readText()) } }.getOrNull()
            val ok = json != null && launcher.store.import(json, launcher.layout.today.filter { it.isAndroid })
            if (ok) launcher.reloadAll()
            toast(if (ok) "Disposition importée" else "Ce fichier n’est pas une sauvegarde Pli")
        }
    }
    private val defaultHome = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refreshDefaultHome() }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Pli is always dark: light status and navigation bar icons on a transparent bar.
        enableEdgeToEdge(SystemBarStyle.dark(android.graphics.Color.TRANSPARENT), SystemBarStyle.dark(android.graphics.Color.TRANSPARENT))
        super.onCreate(savedInstanceState)
        launcher = Launcher(this)
        widgets = Widgets(this)
        setContent { PliApp(this, signals) }
        lifecycleScope.launch { launcher.start() }
        launcher.repository.listen { packageName, kind ->
            lifecycleScope.launch {
                launcher.reloadApps(
                    removedPackage = packageName.takeIf { kind == AppRepository.REMOVED },
                    addedPackage = packageName.takeIf { kind == AppRepository.ADDED },
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        widgets.start()
        MediaWatcher.refresh(this)
    }

    override fun onStop() {
        widgets.stop()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        refreshDefaultHome()
        signals.resumes++
    }

    override fun onDestroy() {
        launcher.repository.stop()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Pressing Home while Pli is already in front closes panels and returns to the first page.
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) signals.homePresses++
    }

    @Deprecated("Widget binding and configuration still report through onActivityResult")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (!widgets.onActivityResult(requestCode, resultCode)) {
            @Suppress("DEPRECATION")
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun refreshDefaultHome() {
        val roles = getSystemService(RoleManager::class.java)
        signals.isDefaultHome = runCatching { roles.isRoleHeld(RoleManager.ROLE_HOME) }.getOrDefault(true)
    }

    override fun launch(app: AppInfo, bounds: Rect?) {
        val view = window.decorView
        val androidBounds = bounds?.let { AndroidRect(it.left.roundToInt(), it.top.roundToInt(), it.right.roundToInt(), it.bottom.roundToInt()) }
        val options = androidBounds?.let {
            ActivityOptions.makeClipRevealAnimation(view, it.left, it.top, it.width(), it.height()).toBundle()
        }
        if (!launcher.repository.launch(app, androidBounds, options)) launcher.repository.launch(app, androidBounds, null)
    }

    override fun openPackage(packageName: String) {
        val app = launcher.apps.firstOrNull { it.packageName == packageName && !it.work } ?: launcher.apps.firstOrNull { it.packageName == packageName }
        if (app != null) launch(app, null)
        else runCatching { packageManager.getLaunchIntentForPackage(packageName)?.let(::startActivity) }
    }

    override fun appInfo(app: AppInfo) { launcher.repository.openDetails(app) }
    override fun uninstall(app: AppInfo) { launcher.repository.uninstall(app) }

    override fun requestDefaultHome() {
        val roles = getSystemService(RoleManager::class.java)
        runCatching {
            if (roles.isRoleAvailable(RoleManager.ROLE_HOME) && !roles.isRoleHeld(RoleManager.ROLE_HOME))
                defaultHome.launch(roles.createRequestRoleIntent(RoleManager.ROLE_HOME))
            else startActivity(Intent(Settings.ACTION_HOME_SETTINGS))
        }.onFailure { runCatching { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) } }
    }

    override fun requestPermission(permission: String) {
        if (checkSelfPermission(permission) == android.content.pm.PackageManager.PERMISSION_GRANTED) { signals.resumes++; return }
        this.permission.launch(permission)
    }

    override fun openNotificationAccess() {
        val component = ComponentName(this, BadgeService::class.java)
        runCatching {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_DETAIL_SETTINGS)
                .putExtra(Settings.EXTRA_NOTIFICATION_LISTENER_COMPONENT_NAME, component.flattenToString()))
        }.onFailure { runCatching { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) } }
    }

    override val hasGoogleApp: Boolean
        get() = runCatching { packageManager.getLaunchIntentForPackage(GOOGLE) != null }.getOrDefault(false)

    /** Opens the Google app (its home is the Discover feed), sliding in from the left. */
    override fun openDiscover() {
        val intent = packageManager.getLaunchIntentForPackage(GOOGLE) ?: return
        val options = ActivityOptions.makeCustomAnimation(this, android.R.anim.slide_in_left, android.R.anim.fade_out).toBundle()
        runCatching { startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), options) }
    }

    private fun toast(text: String) = android.widget.Toast.makeText(this, text, android.widget.Toast.LENGTH_SHORT).show()

    override fun openAccessibilitySettings() {
        toast("Active « Pli – verrouillage par geste »")
        runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
    }

    override fun exportBackup() {
        exportFile.launch("pli-disposition-" + java.time.LocalDate.now() + ".json")
    }

    override fun importBackup() {
        importFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream"))
    }

    override fun expandNotifications() {
        runCatching {
            val service = getSystemService("statusbar")
            Class.forName("android.app.StatusBarManager").getMethod("expandNotificationsPanel").invoke(service)
        }
    }

    private companion object { const val GOOGLE = "com.google.android.googlequicksearchbox" }
}
