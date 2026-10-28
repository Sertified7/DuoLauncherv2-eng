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
    )

    fun saveSettings(s: PliSettings) {
        prefs.edit().putBoolean("labels", s.labels).putBoolean("phoneWallpaper", s.phoneWallpaper)
            .putBoolean("parallax", s.parallax).putBoolean("bubbles", s.bubbles).putInt("dockSize", s.dockSize)
            .putInt("innerDockSize", s.innerDockSize).putInt("coverColumns", s.coverColumns).putInt("coverRows", s.coverRows)
            .putInt("innerColumns", s.innerColumns).putInt("innerRows", s.innerRows).putBoolean("discover", s.discover)
            .putBoolean("addNewApps", s.addNewApps).putString("city", s.city)
            .putString("lat", s.latitude?.toString()).putString("lon", s.longitude?.toString())
            .putBoolean("swipeDownNotifications", s.swipeDownNotifications).apply()
    }

    var note: String
        get() = prefs.getString("note", "") ?: ""
        set(value) { prefs.edit().putString("note", value).apply() }
}
