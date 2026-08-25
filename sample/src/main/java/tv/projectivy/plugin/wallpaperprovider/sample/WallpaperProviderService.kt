package tv.projectivy.plugin.wallpaperprovider.sample

import android.app.Service
import android.content.Intent
import android.os.IBinder
import tv.projectivy.plugin.wallpaperprovider.api.Event
import tv.projectivy.plugin.wallpaperprovider.api.IWallpaperProviderService
import tv.projectivy.plugin.wallpaperprovider.api.Wallpaper
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperType

class WallpaperProviderService : Service() {

    private val binder = object : IWallpaperProviderService.Stub() {

        override fun getWallpapers(event: Event?): List<Wallpaper> {

            return when (event) {

                is Event.TimeElapsed -> {

                    listOf(
                        Wallpaper(
                            TEST_VIDEO_URL,
                            WallpaperType.VIDEO
                        )
                    )
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

    companion object {

        private const val TEST_VIDEO_URL =
            "https://drive.google.com/uc?export=download&id=1BDeR95Wsc8o4IQPZ2ME1pkdOs1dvd3ta"
    }
}