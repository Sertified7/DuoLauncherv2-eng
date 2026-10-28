package com.yuksel.pli

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.BatteryManager
import android.provider.CalendarContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.roundToInt

enum class Sky { CLEAR, PARTLY, CLOUDY, FOG, DRIZZLE, RAIN, SNOW, STORM }

data class HourForecast(val hour: Int, val temperature: Int, val sky: Sky)
data class Weather(val place: String, val temperature: Int, val label: String, val sky: Sky,
    val high: Int?, val low: Int?, val hours: List<HourForecast>)
data class AgendaEvent(val title: String, val start: LocalDateTime, val end: LocalDateTime, val allDay: Boolean, val color: Int)
data class BatteryInfo(val level: Int, val charging: Boolean)

private fun skyOf(code: Int): Pair<String, Sky> = when (code) {
    0 -> "Ensoleillé" to Sky.CLEAR
    1, 2 -> "Éclaircies" to Sky.PARTLY
    3 -> "Nuageux" to Sky.CLOUDY
    45, 48 -> "Brouillard" to Sky.FOG
    in 51..57 -> "Bruine" to Sky.DRIZZLE
    in 61..67, in 80..82 -> "Pluie" to Sky.RAIN
    in 71..77, 85, 86 -> "Neige" to Sky.SNOW
    in 95..99 -> "Orage" to Sky.STORM
    else -> "Météo" to Sky.CLOUDY
}

private fun lastKnownPosition(context: Context): Pair<Double, Double>? {
    if (context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) return null
    val manager = context.getSystemService(LocationManager::class.java) ?: return null
    val last = runCatching { manager.getProviders(true).mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.time } }.getOrNull()
        ?: return null
    return last.latitude to last.longitude
}

private fun httpJson(url: String): JSONObject {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 8_000; readTimeout = 8_000 }
    try { return JSONObject(connection.inputStream.bufferedReader().use { it.readText() }) } finally { connection.disconnect() }
}

/** Finds a city with Open-Meteo's geocoder: (name, latitude, longitude). */
suspend fun findCity(name: String): Triple<String, Double, Double>? = withContext(Dispatchers.IO) {
    runCatching {
        val json = httpJson("https://geocoding-api.open-meteo.com/v1/search?count=1&language=fr&name=" + URLEncoder.encode(name.trim(), "UTF-8"))
        val first = json.optJSONArray("results")?.optJSONObject(0) ?: return@runCatching null
        Triple(first.optString("name", name), first.getDouble("latitude"), first.getDouble("longitude"))
    }.getOrNull()
}

/** Current weather and the next hours from Open-Meteo (free, no account). Null when offline or no place is known. */
suspend fun loadWeather(context: Context, settings: PliSettings): Weather? = withContext(Dispatchers.IO) {
    val (lat, lon) = if (settings.latitude != null && settings.longitude != null) settings.latitude to settings.longitude
        else lastKnownPosition(context) ?: return@withContext null
    runCatching {
        val json = httpJson("https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,weather_code&hourly=temperature_2m,weather_code" +
            "&daily=temperature_2m_max,temperature_2m_min&forecast_days=2&timezone=auto")
        val current = json.getJSONObject("current")
        val daily = json.optJSONObject("daily")
        val (label, sky) = skyOf(current.getInt("weather_code"))
        val hourly = json.optJSONObject("hourly")
        val hours = ArrayList<HourForecast>()
        if (hourly != null) {
            val times = hourly.getJSONArray("time"); val temps = hourly.getJSONArray("temperature_2m"); val codes = hourly.getJSONArray("weather_code")
            val now = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0)
            for (i in 0 until times.length()) {
                val t = runCatching { LocalDateTime.parse(times.getString(i)) }.getOrNull() ?: continue
                if (t.isAfter(now) && (t.hour - now.hour + 24) % 3 == 0) {
                    hours += HourForecast(t.hour, temps.getDouble(i).roundToInt(), skyOf(codes.getInt(i)).second)
                    if (hours.size == 4) break
                }
            }
        }
        Weather(settings.city.ifBlank { "Ici" }, current.getDouble("temperature_2m").roundToInt(), label, sky,
            daily?.optJSONArray("temperature_2m_max")?.optDouble(0)?.takeUnless(Double::isNaN)?.roundToInt(),
            daily?.optJSONArray("temperature_2m_min")?.optDouble(0)?.takeUnless(Double::isNaN)?.roundToInt(), hours)
    }.getOrNull()
}

fun hasCalendarAccess(context: Context) =
    context.checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

/** Events in the coming 24 hours (ongoing ones included), if calendar access was given. */
suspend fun loadEvents(context: Context, max: Int = 4): List<AgendaEvent> = withContext(Dispatchers.IO) {
    if (!hasCalendarAccess(context)) return@withContext emptyList()
    runCatching {
        val now = System.currentTimeMillis()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath((now - 12 * 3_600_000L).toString()).appendPath((now + 24 * 3_600_000L).toString()).build()
        val projection = arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END, CalendarContract.Instances.ALL_DAY, CalendarContract.Instances.DISPLAY_COLOR)
        val result = ArrayList<AgendaEvent>()
        context.contentResolver.query(uri, projection, null, null, "${CalendarContract.Instances.BEGIN} ASC")?.use { cursor ->
            while (cursor.moveToNext() && result.size < max) {
                val begin = cursor.getLong(1); val end = cursor.getLong(2); val allDay = cursor.getInt(3) == 1
                if (end <= now) continue
                val zone = if (allDay) ZoneId.of("UTC") else ZoneId.systemDefault()
                result += AgendaEvent(cursor.getString(0)?.takeIf { it.isNotBlank() } ?: "Sans titre",
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(begin), zone),
                    LocalDateTime.ofInstant(Instant.ofEpochMilli(end), zone), allDay, cursor.getInt(4))
            }
        }
        result
    }.getOrDefault(emptyList())
}

fun readBattery(context: Context): BatteryInfo {
    val intent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
    val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
    return BatteryInfo(if (level >= 0 && scale > 0) level * 100 / scale else 0,
        status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL)
}
