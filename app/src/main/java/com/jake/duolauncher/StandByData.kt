package com.jake.duolauncher

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

internal data class StandByWeather(val temperature: Int, val label: String, val symbol: String, val high: Int?, val low: Int?)
internal data class StandByEvent(val title: String, val start: LocalDateTime, val allDay: Boolean)

/** Uses the place set for the light/dark schedule, else the phone's last approximate position. */
private fun standByLocation(context: Context): Pair<Double, Double>? {
    val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    val lat = prefs.getString("lat", null)?.toDoubleOrNull()
    val lon = prefs.getString("lon", null)?.toDoubleOrNull()
    if (lat != null && lon != null) return lat to lon
    if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
    val manager = context.getSystemService(LocationManager::class.java) ?: return null
    val last = runCatching {
        manager.getProviders(true).mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.time }
    }.getOrNull() ?: return null
    return last.latitude to last.longitude
}

/** WMO weather codes → short French label and a symbol. */
private fun describe(code: Int): Pair<String, String> = when (code) {
    0 -> "Ensoleillé" to "☀️"
    1, 2 -> "Éclaircies" to "🌤️"
    3 -> "Nuageux" to "☁️"
    45, 48 -> "Brouillard" to "🌫️"
    in 51..57 -> "Bruine" to "🌦️"
    in 61..67, in 80..82 -> "Pluie" to "🌧️"
    in 71..77, 85, 86 -> "Neige" to "❄️"
    in 95..99 -> "Orage" to "⛈️"
    else -> "Météo" to "🌡️"
}

/** Current weather from Open-Meteo (free, no account). Null when offline or no location is known. */
internal suspend fun loadStandByWeather(context: Context): StandByWeather? = withContext(Dispatchers.IO) {
    val (lat, lon) = standByLocation(context) ?: return@withContext null
    runCatching {
        val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&forecast_days=1&timezone=auto")
        val connection = (url.openConnection() as HttpURLConnection).apply { connectTimeout = 8_000; readTimeout = 8_000 }
        try {
            val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            val current = json.getJSONObject("current")
            val daily = json.optJSONObject("daily")
            val (label, symbol) = describe(current.getInt("weather_code"))
            StandByWeather(current.getDouble("temperature_2m").roundToInt(), label, symbol,
                daily?.optJSONArray("temperature_2m_max")?.optDouble(0)?.takeUnless(Double::isNaN)?.roundToInt(),
                daily?.optJSONArray("temperature_2m_min")?.optDouble(0)?.takeUnless(Double::isNaN)?.roundToInt())
        } finally { connection.disconnect() }
    }.getOrNull()
}

internal fun hasCalendarAccess(context: Context) =
    context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

/** The next calendar event in the coming 24 hours (an ongoing all-day event counts), if calendar access was given. */
internal suspend fun loadNextEvent(context: Context): StandByEvent? = withContext(Dispatchers.IO) {
    if (!hasCalendarAccess(context)) return@withContext null
    runCatching {
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath(now.toString()).appendPath((now + 24 * 3_600_000L).toString()).build()
        val projection = arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY)
        context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { cursor ->
            while (cursor.moveToNext()) {
                val title = cursor.getString(0)?.takeIf { it.isNotBlank() } ?: "Sans titre"
                val begin = cursor.getLong(1); val end = cursor.getLong(2); val allDay = cursor.getInt(3) == 1
                if (end <= now) continue
                // All-day instances are stored in UTC.
                val zone = if (allDay) ZoneId.of("UTC") else ZoneId.systemDefault()
                return@runCatching StandByEvent(title, LocalDateTime.ofInstant(Instant.ofEpochMilli(begin), zone), allDay)
            }
            null
        }
    }.getOrNull()
}
