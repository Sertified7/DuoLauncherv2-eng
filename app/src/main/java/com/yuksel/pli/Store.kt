package com.yuksel.pli

import android.content.Context
import org.json.JSONObject

data class PliSettings(
    val labels: Boolean = true,
    val phoneWallpaper: Boolean = false,
    val parallax: Boolean = true,
    val bubbles: Boolean = true,
    val dockSize: Int = 5,
    val innerDockSize: Int = 5,
    val coverColumns: Int = 0,
    val coverRows: Int = 0,
    val innerColumns: Int = 0,
    val innerRows: Int = 0,
    val discover: Boolean = true,
    val addNewApps: Boolean = true,
    val city: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val swipeDownNotifications: Boolean = true,
    val iconPack: String = "",
    val tintedIcons: Boolean = false,
    val doubleTap: String = "lock",
    val twoFingerUp: String = "none",
    val twoFingerDown: String = "notifications",
    val lockLayout: Boolean = false,
    val recentInDock: Int = 2,
)

/** Saves the layout, the settings and the quick note in private preferences. */
class Store(context: Context) {
    private val prefs = context.getSharedPreferences("pli", Context.MODE_PRIVATE)

    fun hasLayout() = prefs.contains("layout")

    fun loadLayout(): HomeLayout = prefs.getString("layout", null)
        ?.let { runCatching { HomeLayout.fromJson(JSONObject(it)) }.getOrNull() } ?: HomeLayout()

    fun saveLayout(layout: HomeLayout) { prefs.edit().putString("layout", layout.toJson().toString()).apply() }

    fun loadSettings() = PliSettings(
        labels = prefs.getBoolean("labels", true),
        phoneWallpaper = prefs.getBoolean("phoneWallpaper", false),
        parallax = prefs.getBoolean("parallax", true),
        bubbles = prefs.getBoolean("bubbles", true),
        dockSize = prefs.getInt("dockSize", 5),
        innerDockSize = prefs.getInt("innerDockSize", prefs.getInt("dockSize", 5)),
        coverColumns = prefs.getInt("coverColumns", 0),
        coverRows = prefs.getInt("coverRows", 0),
        innerColumns = prefs.getInt("innerColumns", 0),
        innerRows = prefs.getInt("innerRows", 0),
        discover = prefs.getBoolean("discover", true),
        addNewApps = prefs.getBoolean("addNewApps", true),
        city = prefs.getString("city", "") ?: "",
        latitude = prefs.getString("lat", null)?.toDoubleOrNull(),
        longitude = prefs.getString("lon", null)?.toDoubleOrNull(),
        swipeDownNotifications = prefs.getBoolean("swipeDownNotifications", true),
        iconPack = prefs.getString("iconPack", "") ?: "",
        tintedIcons = prefs.getBoolean("tintedIcons", false),
        doubleTap = prefs.getString("doubleTap", "lock") ?: "lock",
        twoFingerUp = prefs.getString("twoFingerUp", "none") ?: "none",
        twoFingerDown = prefs.getString("twoFingerDown", "notifications") ?: "notifications",
        lockLayout = prefs.getBoolean("lockLayout", false),
        recentInDock = prefs.getInt("recentInDock", 2),
    )

    fun saveSettings(s: PliSettings) {
        prefs.edit().putBoolean("labels", s.labels).putBoolean("phoneWallpaper", s.phoneWallpaper)
            .putBoolean("parallax", s.parallax).putBoolean("bubbles", s.bubbles).putInt("dockSize", s.dockSize)
            .putInt("innerDockSize", s.innerDockSize).putInt("coverColumns", s.coverColumns).putInt("coverRows", s.coverRows)
            .putInt("innerColumns", s.innerColumns).putInt("innerRows", s.innerRows).putBoolean("discover", s.discover)
            .putBoolean("addNewApps", s.addNewApps).putString("city", s.city)
            .putString("lat", s.latitude?.toString()).putString("lon", s.longitude?.toString())
            .putBoolean("swipeDownNotifications", s.swipeDownNotifications)
            .putString("iconPack", s.iconPack).putBoolean("tintedIcons", s.tintedIcons).putString("doubleTap", s.doubleTap)
            .putString("twoFingerUp", s.twoFingerUp).putString("twoFingerDown", s.twoFingerDown)
            .putBoolean("lockLayout", s.lockLayout).putInt("recentInDock", s.recentInDock).apply()
    }

    /** Everything Pli saved (layout, settings, note) as one JSON document. Android widgets are left out: they belong to this phone. */
    fun export(): JSONObject {
        val layout = loadLayout()
        return JSONObject().put("app", "Pli").put("version", 1)
            .put("layout", layout.copy(today = layout.today.filterNot { it.isAndroid }).toJson())
            .put("settings", JSONObject().apply {
                prefs.all.forEach { (key, value) -> if (key != "layout" && key != "note" && value != null) put(key, value) }
            })
            .put("note", note)
    }

    /** Restores a document from [export]. Returns false when it is not a Pli backup. */
    fun import(json: JSONObject, keepAndroidWidgets: List<TodayItem>): Boolean {
        if (json.optString("app") != "Pli") return false
        val layout = HomeLayout.fromJson(json.optJSONObject("layout") ?: return false)
        val settings = json.optJSONObject("settings") ?: JSONObject()
        val edit = prefs.edit()
        settings.keys().forEach { key ->
            when (val v = settings.get(key)) {
                is Boolean -> edit.putBoolean(key, v)
                is Int -> edit.putInt(key, v)
                is Long -> edit.putInt(key, v.toInt())
                is Double -> edit.putString(key, v.toString())
                is String -> edit.putString(key, v)
            }
        }
        edit.putString("layout", layout.copy(today = layout.today.filterNot { it.isAndroid } + keepAndroidWidgets).toJson().toString())
        edit.putString("note", json.optString("note", note))
        edit.apply()
        return true
    }

    var note: String
        get() = prefs.getString("note", "") ?: ""
        set(value) { prefs.edit().putString("note", value).apply() }
}
