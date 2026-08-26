package tv.projectivy.plugin.wallpaperprovider.sample

import android.app.Service
import android.content.Intent
import android.os.IBinder
import org.json.JSONArray
import org.json.JSONObject
import tv.projectivy.plugin.wallpaperprovider.api.Event
import tv.projectivy.plugin.wallpaperprovider.api.IWallpaperProviderService
import tv.projectivy.plugin.wallpaperprovider.api.Wallpaper
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperType
import java.net.HttpURLConnection
import java.net.URL

class WallpaperProviderService : Service() {

    private val binder = object : IWallpaperProviderService.Stub() {

        override fun getWallpapers(event: Event?): List<Wallpaper> {

            return when (event) {

                is Event.TimeElapsed -> {
                    runCatching {
                        loadWallpaper()
                    }.getOrElse {
                        emptyList()
                    }
                }

                else -> emptyList()
            }
        }

        override fun getPreferences(): String {
            return "{}"
        }

        override fun setPreferences(params: String) {
        }
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    private fun loadWallpaper(): List<Wallpaper> {

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

            val wallpaper = extractWallpaper(json)
                ?: return emptyList()

            val videoUrl =
                wallpaper.optString("url_1080p")
                    .ifBlank {
                        wallpaper.optString("url")
                    }

            if (videoUrl.isBlank()) {
                return emptyList()
            }

            return listOf(
                Wallpaper(
                    videoUrl,
                    WallpaperType.VIDEO
                )
            )

        } finally {
            connection.disconnect()
        }
    }

    private fun extractWallpaper(json: String): JSONObject? {

        val trimmed = json.trim()

        // Supports:
        //
        // [
        //   {
        //     "title": "Day",
        //     "url_1080p": "https://..."
        //   }
        // ]
        //
        if (trimmed.startsWith("[")) {

            val array = JSONArray(trimmed)

            if (array.length() == 0) {
                return null
            }

            return array.optJSONObject(0)
        }

        // Also supports:
        //
        // {
        //   "wallpapers": [...]
        // }
        //
        // or:
        //
        // {
        //   "items": [...]
        // }

        val root = JSONObject(trimmed)

        val wallpapers =
            root.optJSONArray("wallpapers")
                ?: root.optJSONArray("items")
                ?: return null

        if (wallpapers.length() == 0) {
            return null
        }

        return wallpapers.optJSONObject(0)
    }

    companion object {

        private const val WALLPAPER_JSON_URL =
            "https://jackabytes.github.io/smart-wallpaper/wallpaper.json"
    }
}