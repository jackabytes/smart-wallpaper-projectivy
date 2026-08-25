package tv.projectivy.plugin.wallpaperprovider.sample

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import tv.projectivy.plugin.wallpaperprovider.api.Event
import tv.projectivy.plugin.wallpaperprovider.api.IWallpaperProviderService
import tv.projectivy.plugin.wallpaperprovider.api.Wallpaper
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperDisplayMode
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperType

@Serializable
data class SmartWallpaperItem(
    val title: String = "",
    val url_1080p: String = "",
    val url: String = ""
)

class WallpaperProviderService : Service() {

    private val httpClient = OkHttpClient()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        allowTrailingComma = true
    }

    private val binder = object : IWallpaperProviderService.Stub() {

        override fun getWallpapers(event: Event?): List<Wallpaper> {
            return runBlocking {
                fetchWallpapers()
            }
        }

        override fun getPreferences(): String {
            return "{}"
        }

        override fun setPreferences(params: String) {
        }
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    private suspend fun fetchWallpapers(): List<Wallpaper> {

        val source = withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(WALLPAPER_JSON_URL)
                    .build()

                httpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.e(
                            "SmartWallpaper",
                            "wallpaper.json HTTP ${response.code}"
                        )
                        null
                    } else {
                        response.body.string()
                    }
                }

            } catch (e: Exception) {
                Log.e(
                    "SmartWallpaper",
                    "Failed to download wallpaper.json",
                    e
                )
                null
            }
        }

        if (source.isNullOrBlank()) {
            return emptyList()
        }

        return withContext(Dispatchers.Default) {
            try {

                val items =
                    json.decodeFromString<List<SmartWallpaperItem>>(source)

                items.mapNotNull { item ->

                    val wallpaperUrl =
                        item.url_1080p.ifBlank {
                            item.url
                        }

                    if (wallpaperUrl.isBlank()) {
                        null
                    } else {
                        Wallpaper(
                            uri = wallpaperUrl,
                            type = WallpaperType.VIDEO,
                            displayMode = WallpaperDisplayMode.DEFAULT,
                            title = item.title.ifBlank {
                                "Smart Wallpaper"
                            },
                            source = WALLPAPER_JSON_URL,
                            author = null
                        )
                    }
                }

            } catch (e: Exception) {
                Log.e(
                    "SmartWallpaper",
                    "Failed to parse wallpaper.json",
                    e
                )
                emptyList()
            }
        }
    }

    companion object {
        private const val WALLPAPER_JSON_URL =
            "https://jackabytes.github.io/smart-wallpaper/wallpaper.json"
    }
}
