package com.yuksel.pli

import android.content.Context
import org.json.JSONObject

data class PliSettings(
    val labels: Boolean = true,
    val phoneWallpaper: Boolean = false,
    val parallax: Boolean = true,
    val bubbles: Boolean = true,
    val dockSize: Int = 5,
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
        addNewApps = prefs.getBoolean("addNewApps", true),
        city = prefs.getString("city", "") ?: "",
        latitude = prefs.getString("lat", null)?.toDoubleOrNull(),
        longitude = prefs.getString("lon", null)?.toDoubleOrNull(),
        swipeDownNotifications = prefs.getBoolean("swipeDownNotifications", true),
    )

    fun saveSettings(s: PliSettings) {
        prefs.edit().putBoolean("labels", s.labels).putBoolean("phoneWallpaper", s.phoneWallpaper)
            .putBoolean("parallax", s.parallax).putBoolean("bubbles", s.bubbles).putInt("dockSize", s.dockSize)
            .putBoolean("addNewApps", s.addNewApps).putString("city", s.city)
            .putString("lat", s.latitude?.toString()).putString("lon", s.longitude?.toString())
            .putBoolean("swipeDownNotifications", s.swipeDownNotifications).apply()
    }

    var note: String
        get() = prefs.getString("note", "") ?: ""
        set(value) { prefs.edit().putString("note", value).apply() }
}
