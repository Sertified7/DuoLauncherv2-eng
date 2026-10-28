package com.yuksel.pli

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File

/** Icons chosen by hand for one app: from an icon pack, or from a picture. */
object CustomIcons {
    private fun dir(context: Context) = File(context.filesDir, "icons").apply { mkdirs() }

    fun load(context: Context, spec: String, size: Int): ImageBitmap? = runCatching {
        when {
            spec.startsWith("pack:") -> {
                val pkg = spec.removePrefix("pack:").substringBefore('/')
                val name = spec.substringAfter('/')
                val res = context.packageManager.getResourcesForApplication(pkg)
                val id = res.getIdentifier(name, "drawable", pkg)
                res.getDrawableForDensity(id, context.resources.displayMetrics.densityDpi, null)?.toBitmap(size, size, Bitmap.Config.ARGB_8888)?.asImageBitmap()
            }
            spec.startsWith("file:") -> BitmapFactory.decodeFile(File(dir(context), spec.removePrefix("file:")).path)?.asImageBitmap()
            else -> null
        }
    }.getOrNull()

    /** Copies a picture into Pli as a square icon. Returns its spec. */
    fun saveImage(context: Context, uri: Uri, appId: String): String? = runCatching {
        val source = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
        val side = minOf(source.width, source.height)
        val square = Bitmap.createBitmap(source, (source.width - side) / 2, (source.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(square, 256, 256, true)
        val name = appId.hashCode().toUInt().toString(16) + "-" + System.currentTimeMillis() + ".png"
        File(dir(context), name).outputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it) }
        "file:$name"
    }.getOrNull()

    /** Every drawable an icon pack offers (names from its drawable.xml, else its appfilter.xml). */
    fun packIcons(context: Context, pkg: String): List<String> = runCatching {
        val res = context.packageManager.getResourcesForApplication(pkg)
        fun parser(name: String): XmlPullParser? = res.getIdentifier(name, "xml", pkg).takeIf { it != 0 }?.let { res.getXml(it) }
            ?: runCatching { XmlPullParserFactory.newInstance().newPullParser().apply { setInput(res.assets.open("$name.xml"), "UTF-8") } }.getOrNull()
        val names = LinkedHashSet<String>()
        for (file in listOf("drawable", "appfilter")) {
            val p = parser(file) ?: continue
            var event = p.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && p.name == "item") p.getAttributeValue(null, "drawable")?.let(names::add)
                event = p.next()
            }
            if (names.isNotEmpty()) break
        }
        names.filter { res.getIdentifier(it, "drawable", pkg) != 0 }
    }.getOrDefault(emptyList())
}
