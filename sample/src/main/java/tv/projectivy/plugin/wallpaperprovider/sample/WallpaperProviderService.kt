package tv.projectivy.plugin.wallpaperprovider.sample

import android.app.Service
import android.content.Intent
import android.os.IBinder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import tv.projectivy.plugin.wallpaperprovider.api.Event
import tv.projectivy.plugin.wallpaperprovider.api.IWallpaperProviderService
import tv.projectivy.plugin.wallpaperprovider.api.Wallpaper
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperDisplayMode
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperType
import java.net.HttpURLConnection
import java.net.URL

class WallpaperProviderService : Service() {

    private val binder = object : IWallpaperProviderService.Stub() {

        override fun getWallpapers(event: Event?): List<Wallpaper> {

            // Projectivy controls the update timing.
            // We deliberately do not create our own clock.
            if (event !is Event.TimeElapsed) {
                return emptyList()
            }

            return runCatching {
                runBlocking {
                    getCurrentWallpaper()
                }
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

    private suspend fun getCurrentWallpaper(): List<Wallpaper> =
        withContext(Dispatchers.IO) {

            runCatching {

                val connection =
                    URL(WALLPAPER_JSON_URL)
                        .openConnection() as HttpURLConnection

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
                        return@runCatching emptyList()
                    }

                    val json =
                        connection.inputStream
                            .bufferedReader()
                            .use { it.readText() }

                    val items = parseWallpaperJson(json)

                    if (items.isEmpty()) {
                        return@runCatching emptyList()
                    }

                    val item = items.first()

                    val wallpaperUrl =
                        item.optString("url_1080p")
                            .ifBlank {
                                item.optString("url")
                            }

                    if (wallpaperUrl.isBlank()) {
                        return@runCatching emptyList()
                    }

                    val title =
                        item.optString("title")
                            .ifBlank {
                                "Smart Wallpaper"
                            }

                    listOf(
                        Wallpaper(
                            uri = wallpaperUrl,
                            type = WallpaperType.VIDEO,
                            displayMode = WallpaperDisplayMode.DEFAULT,
                            title = title,
                            source = "Smart Wallpaper",
                            author = null
                        )
                    )

                } finally {
                    connection.disconnect()
                }

            }.getOrElse {
                emptyList()
            }
        }

    private fun parseWallpaperJson(
        json: String
    ): List<JSONObject> {

        val trimmed = json.trim()

        if (trimmed.isEmpty()) {
            return emptyList()
        }

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
