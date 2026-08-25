package tv.projectivy.plugin.wallpaperprovider.sample

import android.app.Service
import android.content.Intent
import android.os.IBinder
import tv.projectivy.plugin.wallpaperprovider.api.Event
import tv.projectivy.plugin.wallpaperprovider.api.IWallpaperProviderService
import tv.projectivy.plugin.wallpaperprovider.api.Wallpaper
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperType
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray
import org.json.JSONObject

class WallpaperProviderService : Service() {

    private val binder = object : IWallpaperProviderService.Stub() {

        override fun getWallpapers(event: Event?): List<Wallpaper> {

            // Projectivy tells us when the time-based cache expires.
            // We don't run our own clock.
            if (event !is Event.TimeElapsed) {
                return emptyList()
            }

            return runCatching {
                getCurrentWallpaper()
            }.getOrElse {
                emptyList()
            }
        }

        override fun getPreferences(): String {
            return "{}"
        }

        override fun setPreferences(params: String) {
            // No provider-side settings required for v1.
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private fun getCurrentWallpaper(): List<Wallpaper> {

        val connection =
            URL(WALLPAPER_JSON_URL).openConnection() as HttpURLConnection

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 15000
            connection.useCaches = false

            connection.setRequestProperty(
                "Accept",
                "application/json"
            )

            connection.setRequestProperty(
                "User-Agent",
                "SmartWallpaper/1.0"
            )

            if (connection.responseCode !in 200..299) {
                return emptyList()
            }

            val json =
                connection.inputStream
                    .bufferedReader()
                    .use { it.readText() }

            val items = parseWallpaperJson(json)

            if (items.isEmpty()) {
                return emptyList()
            }

            val item = items[0]

            val wallpaperUrl =
                item.optString("url_1080p")
                    .ifBlank {
                        item.optString("url")
                    }

            if (wallpaperUrl.isBlank()) {
                return emptyList()
            }

            val title =
                item.optString("title")
                    .ifBlank {
                        "Smart Wallpaper"
                    }

            return listOf(
                Wallpaper(
                    uri = wallpaperUrl,
                    type = WallpaperType.VIDEO,
                    title = title,
                    source = WALLPAPER_JSON_URL
                )
            )

        } finally {
            connection.disconnect()
        }
    }

    private fun parseWallpaperJson(json: String): List<JSONObject> {

        val trimmed = json.trim()

        if (trimmed.startsWith("[")) {

            val array = JSONArray(trimmed)

            return List(array.length()) { index ->
                array.getJSONObject(index)
            }
        }

        val root = JSONObject(trimmed)

        val wallpapers =
            root.optJSONArray("wallpapers")
                ?: root.optJSONArray("items")
                ?: JSONArray()

        return List(wallpapers.length()) { index ->
            wallpapers.getJSONObject(index)
        }
    }

    companion object {

        private const val WALLPAPER_JSON_URL =
            "https://jackabytes.github.io/smart-wallpaper/wallpaper.json"
    }
}
