package tv.projectivy.plugin.wallpaperprovider.sample

import android.app.Service
import android.content.Intent
import android.os.IBinder
import tv.projectivy.plugin.wallpaperprovider.api.Event
import tv.projectivy.plugin.wallpaperprovider.api.IWallpaperProviderService
import tv.projectivy.plugin.wallpaperprovider.api.Wallpaper
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperType
import java.util.Calendar

class WallpaperProviderService : Service() {

    private val binder = object : IWallpaperProviderService.Stub() {

        override fun getWallpapers(event: Event?): List<Wallpaper> {
            return when (event) {
                is Event.TimeElapsed -> {
                    runCatching {
                        listOf(getCurrentWallpaper())
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

    private fun getCurrentWallpaper(): Wallpaper {

        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        /*
         * Daily schedule:
         *
         * 06:00 - 12:00  Morning
         * 12:00 - 18:00  Afternoon
         * 18:00 - 21:00  Evening
         * 21:00 - 06:00  Night
         */

        val videos = when (hour) {

            in 6..11 -> MORNING

            in 12..17 -> AFTERNOON

            in 18..20 -> EVENING

            else -> NIGHT
        }

        /*
         * Keep the same wallpaper throughout each period.
         * The day number gives us a stable daily selection.
         */

        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)

        val videoUrl =
            videos[dayOfYear % videos.size]

        return Wallpaper(
            videoUrl,
            WallpaperType.VIDEO
        )
    }

    companion object {

        private val MORNING = listOf(
            "https://drive.google.com/uc?export=download&id=1t3S5MLsvA223YWPo9-r9VO2TJSHnvYbf",
            "https://drive.google.com/uc?export=download&id=1RIIn3bPRGLQYgZ23tGSkQl-DKfcZb9vq"
        )

        private val AFTERNOON = listOf(
            "https://drive.google.com/uc?export=download&id=1zqvU3GjryGl4F4W4PVr0BLwN_N2vSx1P",
            "https://drive.google.com/uc?export=download&id=1fo3WC0Dzg3dHWEQ8LxHVRANER_cCz8HZ",
            "https://drive.google.com/uc?export=download&id=1_WNs5BPQiL_UL7JQGyurHDzceJXkayGt"
        )

        private val EVENING = listOf(
            "https://drive.google.com/uc?export=download&id=1W1tSjNr87oSOCR1M76yQWAVhx9bbzeL_",
            "https://drive.google.com/uc?export=download&id=1BDeR95Wsc8o4IQPZ2ME1pkdOs1dvd3ta",
            "https://drive.google.com/uc?export=download&id=1oMJKARv4AFt_pq5kflutUOq-mbV9Ku9",
            "https://drive.google.com/uc?export=download&id=1u44NnCDNWHJHe1qIgHzPF49e0Q2NPgrC",
            "https://drive.google.com/uc?export=download&id=1p-zqwF9XX9CLy-aBb7DAPDMSB_uThoUH"
        )

        private val NIGHT = listOf(
            "https://drive.google.com/uc?export=download&id=1DTnNH9h_1L3e_-qlxPFhioLDgy9mzFDp",
            "https://drive.google.com/uc?export=download&id=1LeAlZr4IclCuARoInOte1D5Ato-H91Am",
            "https://drive.google.com/uc?export=download&id=1hDkSF-NxditoJ5nSw68zuu9gfA86o1MW",
            "https://drive.google.com/uc?export=download&id=1iQmbGMDP9gtETnLkuIkUObxwS_CW5syg",
            "https://drive.google.com/uc?export=download&id=1CfWIo-aEUh3XS6U4sqcBdirwxG4odaMY"
        )
    }
}