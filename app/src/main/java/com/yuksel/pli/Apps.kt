package com.yuksel.pli

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherActivityInfo
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.os.UserManager
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Collator
import java.text.Normalizer
import java.util.Locale

/** One launchable activity, for one user profile. */
data class AppInfo(
    val id: String,
    val label: String,
    val packageName: String,
    val component: ComponentName,
    val user: UserHandle,
    val work: Boolean,
    val icon: ImageBitmap,
) {
    /** Lower-case label without accents, for search. */
    val searchKey: String = label.normalizedForSearch()
}

fun String.normalizedForSearch(): String =
    Normalizer.normalize(lowercase(Locale.FRANCE), Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "")

/** Lists apps through LauncherApps (all profiles, badged icons) and reports install/remove/update. */
class AppRepository(private val context: Context) {
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val userManager = context.getSystemService(UserManager::class.java)
    private val iconSize = (context.resources.displayMetrics.density * 72).toInt().coerceIn(96, 256)
    private val iconCache = HashMap<String, ImageBitmap>()
    private var callback: LauncherApps.Callback? = null
    private var styleKey = ""
    private var pack: IconPacks.Pack? = null

    /** Icon look: an icon pack (package name, or empty) and/or monochrome tinting. */
    data class IconStyle(val pack: String = "", val tinted: Boolean = false)

    fun idOf(component: ComponentName, user: UserHandle): String =
        component.flattenToShortString() + "#" + userManager.getSerialNumberForUser(user)

    suspend fun load(style: IconStyle = IconStyle()): List<AppInfo> = withContext(Dispatchers.IO) {
        val key = style.toString()
        if (key != styleKey) {
            synchronized(iconCache) { iconCache.clear() }
            pack = style.pack.takeIf { it.isNotEmpty() }?.let { IconPacks.load(context, it) }
            styleKey = key
        }
        val mainUser = android.os.Process.myUserHandle()
        val collator = Collator.getInstance(Locale.FRANCE).apply { strength = Collator.PRIMARY }
        val result = ArrayList<AppInfo>()
        for (user in userManager.userProfiles) {
            if (user != mainUser && runCatching { userManager.isQuietModeEnabled(user) }.getOrDefault(false)) continue
            val list = runCatching { launcherApps.getActivityList(null, user) }.getOrNull() ?: continue
            for (info in list) {
                if (info.componentName.packageName == context.packageName) continue
                result += toApp(info, user, user != mainUser, style.tinted)
            }
        }
        result.sortedWith { a, b -> collator.compare(a.label, b.label) }
    }

    private fun toApp(info: LauncherActivityInfo, user: UserHandle, work: Boolean, tinted: Boolean): AppInfo {
        val id = idOf(info.componentName, user)
        val icon = synchronized(iconCache) { iconCache[id] } ?: run {
            val dpi = context.resources.displayMetrics.densityDpi
            val bitmap = runCatching {
                val packed = pack?.icon(info.componentName, dpi)
                val drawable = packed ?: info.getIcon(dpi)
                val base = if (tinted && packed == null) IconPacks.tinted(drawable, iconSize, TINTS[(id.hashCode() and 0x7fffffff) % TINTS.size])
                    else drawable.toBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888)
                // Keep the work badge visible on work apps.
                if (work) context.packageManager.getUserBadgedIcon(android.graphics.drawable.BitmapDrawable(context.resources, base), user)
                    .toBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888) else base
            }.getOrElse { Bitmap.createBitmap(iconSize, iconSize, Bitmap.Config.ARGB_8888) }.asImageBitmap()
            synchronized(iconCache) { iconCache[id] = bitmap }
            bitmap
        }
        return AppInfo(id, info.label?.toString()?.takeIf { it.isNotBlank() } ?: info.componentName.packageName,
            info.componentName.packageName, info.componentName, user, work, icon)
    }

    /** Calls [onChange] with the package name and [ADDED], [REMOVED] or [CHANGED]. */
    fun listen(onChange: (String, Int) -> Unit) {
        val handler = Handler(Looper.getMainLooper())
        val cb = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: UserHandle) { forget(packageName); onChange(packageName, REMOVED) }
            override fun onPackageAdded(packageName: String, user: UserHandle) { forget(packageName); onChange(packageName, ADDED) }
            override fun onPackageChanged(packageName: String, user: UserHandle) { forget(packageName); onChange(packageName, CHANGED) }
            override fun onPackagesAvailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
                packageNames.forEach { forget(it) }; onChange(packageNames.firstOrNull() ?: "", CHANGED)
            }
            override fun onPackagesUnavailable(packageNames: Array<out String>, user: UserHandle, replacing: Boolean) {
                onChange(packageNames.firstOrNull() ?: "", CHANGED)
            }
        }
        callback = cb
        launcherApps.registerCallback(cb, handler)
    }

    fun stop() { callback?.let(launcherApps::unregisterCallback); callback = null }

    private fun forget(packageName: String) = synchronized(iconCache) {
        iconCache.keys.removeAll { it.startsWith("$packageName/") }
    }

    fun launch(app: AppInfo, bounds: Rect?, options: Bundle?) = runCatching {
        launcherApps.startMainActivity(app.component, app.user, bounds, options)
    }.isSuccess

    fun openDetails(app: AppInfo) = runCatching {
        launcherApps.startAppDetailsActivity(app.component, app.user, null, null)
    }.isSuccess

    companion object {
        const val ADDED = 1; const val REMOVED = -1; const val CHANGED = 0
        private val TINTS = intArrayOf(0xFF9BE7FF.toInt(), 0xFFC7A6FF.toInt(), 0xFFFFB3D9.toInt(), 0xFF9BFFD8.toInt())
    }

    fun uninstall(app: AppInfo) = runCatching {
        context.startActivity(Intent(Intent.ACTION_DELETE, android.net.Uri.parse("package:${app.packageName}"))
            .putExtra(Intent.EXTRA_USER, app.user).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}
