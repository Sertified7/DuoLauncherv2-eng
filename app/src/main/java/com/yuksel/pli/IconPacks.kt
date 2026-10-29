package com.yuksel.pli

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.util.Locale

data class IconPackInfo(val packageName: String, val label: String)

/** Icon packs from the Play Store (ADW / Nova format: appfilter.xml mapping components to drawables). */
object IconPacks {
    private val THEME_ACTIONS = listOf("org.adw.launcher.THEMES", "com.novalauncher.THEME", "com.gau.go.launcherex.theme",
        "com.teslacoilsw.launcher.THEME", "com.anddoes.launcher.THEME")

    fun installed(context: Context): List<IconPackInfo> {
        val pm = context.packageManager
        return THEME_ACTIONS.flatMap { action ->
            runCatching { pm.queryIntentActivities(Intent(action), PackageManager.GET_META_DATA) }.getOrDefault(emptyList())
        }.map { it.activityInfo.packageName }.distinct().mapNotNull { pkg ->
            runCatching { IconPackInfo(pkg, pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()) }.getOrNull()
        }.sortedBy { it.label.lowercase(Locale.US) }
    }

    /** Loaded pack: component ("pkg/cls") → drawable. */
    class Pack(private val resources: Resources, private val packageName: String, private val map: Map<String, String>) {
        fun icon(component: android.content.ComponentName, density: Int): Drawable? {
            val name = map[component.packageName + "/" + component.className] ?: map[component.packageName] ?: return null
            val id = resources.getIdentifier(name, "drawable", packageName).takeIf { it != 0 } ?: return null
            return runCatching { resources.getDrawableForDensity(id, density, null) }.getOrNull()
        }
    }

    fun load(context: Context, packageName: String): Pack? = runCatching {
        val res = context.packageManager.getResourcesForApplication(packageName)
        val parser: XmlPullParser = res.getIdentifier("appfilter", "xml", packageName).takeIf { it != 0 }?.let { res.getXml(it) }
            ?: XmlPullParserFactory.newInstance().newPullParser().apply { setInput(res.assets.open("appfilter.xml"), "UTF-8") }
        val map = HashMap<String, String>()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG && parser.name == "item") {
                val component = parser.getAttributeValue(null, "component") ?: ""
                val drawable = parser.getAttributeValue(null, "drawable")
                // ComponentInfo{com.app/com.app.MainActivity}
                val inner = component.substringAfter("{", "").substringBefore("}", "")
                if (drawable != null && inner.contains('/')) {
                    val pkg = inner.substringBefore('/')
                    var cls = inner.substringAfter('/')
                    if (cls.startsWith(".")) cls = pkg + cls
                    map["$pkg/$cls"] = drawable
                    map.putIfAbsent(pkg, drawable)
                }
            }
            event = parser.next()
        }
        Pack(res, packageName, map)
    }.getOrNull()

    /** A monochrome version of an icon in the given color (themed icon if the app has one, else a tinted silhouette). */
    fun tinted(source: Drawable, size: Int, color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val mono = (source as? AdaptiveIconDrawable)?.monochrome?.mutate()
        if (mono != null) {
            // Monochrome layers are drawn at adaptive scale: enlarge so the glyph fills the icon.
            val inset = (size * .25f).toInt()
            mono.setBounds(-inset, -inset, size + inset, size + inset)
            mono.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.SRC_IN)
            mono.draw(canvas)
        } else {
            val source = source.mutate()
            source.setBounds(0, 0, size, size)
            val gray = ColorMatrix().apply { setSaturation(0f) }
            val tint = ColorMatrix(floatArrayOf(
                android.graphics.Color.red(color) / 255f * .9f, 0f, 0f, 0f, 10f,
                0f, android.graphics.Color.green(color) / 255f * .9f, 0f, 0f, 10f,
                0f, 0f, android.graphics.Color.blue(color) / 255f * .9f, 0f, 10f,
                0f, 0f, 0f, 1f, 0f))
            gray.postConcat(tint)
            source.colorFilter = ColorMatrixColorFilter(gray)
            source.draw(canvas)
            source.colorFilter = null
        }
        return bitmap
    }

}
