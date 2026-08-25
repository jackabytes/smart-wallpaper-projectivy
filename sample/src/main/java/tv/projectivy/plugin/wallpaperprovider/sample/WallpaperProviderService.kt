package tv.projectivy.plugin.wallpaperprovider.sample

import android.app.Service
import android.content.Intent
import android.os.IBinder
import tv.projectivy.plugin.wallpaperprovider.api.Event
import tv.projectivy.plugin.wallpaperprovider.api.IWallpaperProviderService
import tv.projectivy.plugin.wallpaperprovider.api.Wallpaper
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperDisplayMode
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperType

class WallpaperProviderService : Service() {

    private val binder = object : IWallpaperProviderService.Stub() {

        override fun getWallpapers(event: Event?): List<Wallpaper> {

            return listOf(
                Wallpaper(
                    uri = TEST_VIDEO_URL,
                    type = WallpaperType.VIDEO,
                    displayMode = WallpaperDisplayMode.DEFAULT,
                    title = "Smart Wallpaper Test",
                    source = TEST_VIDEO_URL,
                    author = null
                )
            )
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

    companion object {

        private const val TEST_VIDEO_URL =
            "https://drive.google.com/uc?export=download&id=1BDeR95Wsc8o4IQPZ2ME1pkdOs1dvd3ta"
    }
}