package tv.projectivy.plugin.wallpaperprovider.sample

import android.app.Service
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import tv.projectivy.plugin.wallpaperprovider.api.Event
import tv.projectivy.plugin.wallpaperprovider.api.IWallpaperProviderService
import tv.projectivy.plugin.wallpaperprovider.api.Wallpaper
import tv.projectivy.plugin.wallpaperprovider.api.WallpaperType
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit

class WallpaperProviderService : Service() {

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .build()

    override fun onCreate() {
        super.onCreate()
        PreferencesManager.init(this)
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    private val binder = object : IWallpaperProviderService.Stub() {

        override fun getWallpapers(event: Event?): List<Wallpaper> {
            return when (event) {
                is Event.TimeElapsed -> {
                    listOf(getCurrentWallpaper())
                }

                is Event.NowPlayingChanged -> emptyList()
                is Event.CardFocused -> emptyList()
                is Event.ProgramCardFocused -> emptyList()

                is Event.LauncherIdleModeChanged -> {
                    if (event.isIdle) {
                        emptyList()
                    } else {
                        listOf(getCurrentWallpaper())
                    }
                }

                else -> emptyList()
            }
        }

        override fun getPreferences(): String {
            return PreferencesManager.export()
        }

        override fun setPreferences(params: String) {
            PreferencesManager.import(params)
        }

        fun getDrawableUri(drawableId: Int): Uri {
            return Uri.Builder()
                .scheme(ContentResolver.SCHEME_ANDROID_RESOURCE)
                .authority(resources.getResourcePackageName(drawableId))
                .appendPath(resources.getResourceTypeName(drawableId))
                .appendPath(resources.getResourceEntryName(drawableId))
                .build()
        }
    }

    private fun getCurrentWallpaper(): Wallpaper {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        /*
         * Smart Wallpaper schedule:
         *
         * 06:00 - 11:59  Morning
         * 12:00 - 17:59  Afternoon
         * 18:00 - 20:59  Evening
         * 21:00 - 05:59  Night
         */

        val videos = when (hour) {
            in 6..11 -> MORNING
            in 12..17 -> AFTERNOON
            in 18..20 -> EVENING
            else -> NIGHT
        }

        val period = when (hour) {
            in 6..11 -> "morning"
            in 12..17 -> "afternoon"
            in 18..20 -> "evening"
            else -> "night"
        }

        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)

        /*
         * One deterministic video for the day.
         *
         * Projectivy can request this repeatedly without causing
         * the wallpaper itself to randomly change.
         */

        val videoUrl = videos[dayOfYear % videos.size]

        /*
         * This key identifies the actual wallpaper that should be
         * cached. Repeated Projectivy refreshes during the same
         * period therefore reuse the existing local file.
         */

        val cacheKey = "$dayOfYear-$period-${videoUrl.hashCode()}"

        val cachedFile = ensureCachedVideo(
            videoUrl = videoUrl,
            cacheKey = cacheKey
        )

        if (cachedFile != null) {
            return wallpaperForFile(cachedFile, period)
        }

        /*
         * Download failed.
         *
         * Keep whatever wallpaper was already working rather than
         * handing Projectivy a broken Drive URL.
         */

        val existingFile = findExistingCachedVideo()

        if (existingFile != null) {
            return wallpaperForFile(existingFile, period)
        }

        /*
         * First-ever launch, before a local video exists.
         */

        return Wallpaper(
            videoUrl,
            WallpaperType.VIDEO
        )
    }

    private fun wallpaperForFile(
        file: File,
        period: String
    ): Wallpaper {
        val baseUri = FileProvider.getUriForFile(
            this,
            "$packageName.fileprovider",
            file
        )

        /*
         * Give Projectivy a different wallpaper identity when the
         * time period changes, while still pointing to the same
         * local current.mp4 file.
         */

        val wallpaperUri = baseUri.buildUpon()
            .appendQueryParameter("period", period)
            .build()

        grantUriPermission(
            "com.spocky.projengmenu",
            wallpaperUri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )

        return Wallpaper(
            wallpaperUri.toString(),
            WallpaperType.VIDEO
        )
    }

    private fun ensureCachedVideo(
        videoUrl: String,
        cacheKey: String
    ): File? {
        val wallpaperCacheDir = File(
            cacheDir,
            "wallpaper"
        )

        if (!wallpaperCacheDir.exists()) {
            wallpaperCacheDir.mkdirs()
        }

        val finalFile = File(
            wallpaperCacheDir,
            "current.mp4"
        )

        val keyFile = File(
            wallpaperCacheDir,
            "current.key"
        )

        /*
         * Already have exactly the video we need.
         */

        if (
            finalFile.exists() &&
            finalFile.length() > 0L &&
            keyFile.exists() &&
            keyFile.readText() == cacheKey
        ) {
            return finalFile
        }

        /*
         * Download to a temporary file.
         */

        val tempFile = File(
            wallpaperCacheDir,
            "download.tmp"
        )

        return try {
            if (tempFile.exists()) {
                tempFile.delete()
            }

            val downloaded = runBlocking {
                withContext(Dispatchers.IO) {
                    downloadVideo(
                        videoUrl,
                        tempFile
                    )
                }
            }

            if (!downloaded) {
                tempFile.delete()
                return null
            }

            if (finalFile.exists()) {
                finalFile.delete()
            }

            if (!tempFile.renameTo(finalFile)) {
                tempFile.delete()
                return null
            }

            keyFile.writeText(cacheKey)

            finalFile
        } catch (_: Exception) {
            tempFile.delete()
            null
        }
    }

    private fun downloadVideo(
        url: String,
        destination: File
    ): Boolean {
        val request = Request.Builder()
            .url(url)
            .get()
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return false
            }

            val body = response.body
                ?: return false

            val contentType = body.contentType()
                ?.toString()
                .orEmpty()

            /*
             * Google Drive can return an HTML quota/error page
             * instead of the actual video.
             */

            if (
                contentType.contains(
                    "text/html",
                    ignoreCase = true
                )
            ) {
                return false
            }

            body.byteStream().use { input ->
                destination.outputStream().use { output ->
                    input.copyTo(
                        output,
                        bufferSize = 64 * 1024
                    )
                }
            }

            return destination.exists() &&
                destination.length() > 0L
        }
    }

    private fun findExistingCachedVideo(): File? {
        val wallpaperCacheDir = File(
            cacheDir,
            "wallpaper"
        )

        val file = File(
            wallpaperCacheDir,
            "current.mp4"
        )

        return if (
            file.exists() &&
            file.length() > 0L
        ) {
            file
        } else {
            null
        }
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