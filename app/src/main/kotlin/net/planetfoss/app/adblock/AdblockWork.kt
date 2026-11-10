// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.adblock

import android.content.Context
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import net.planetfoss.core.adblock.FilterRequest
import net.planetfoss.core.adblock.ResourceType
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Weekly list update (EasyPrivacy says “expires: 4 days”; weekly keeps traffic low). */
@HiltWorker
class AdblockUpdateWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repo: AdblockRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val st = repo.state.first()
        if (!st.active) return Result.success()
        return if (repo.update() || runAttemptCount >= 3) Result.success() else Result.retry()
    }
}

@Singleton
class AdblockScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    /** On: weekly update plus one now if no list is there yet. Off: no updates, no traffic. */
    fun apply(enabled: Boolean, needsFirstDownload: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(WEEKLY)
            wm.cancelUniqueWork(NOW)
            return
        }
        val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).setRequiresBatteryNotLow(true).build()
        wm.enqueueUniquePeriodicWork(
            WEEKLY, ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<AdblockUpdateWorker>(7, TimeUnit.DAYS).setConstraints(network)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES).build(),
        )
        if (needsFirstDownload) updateNow()
    }

    /** “Update now” in the settings, and the first download after switching on. */
    fun updateNow() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            NOW, ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<AdblockUpdateWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES).build(),
        )
    }

    companion object {
        const val WEEKLY = "adblock-update"
        const val NOW = "adblock-update-now"
    }
}

/**
 * Turns a WebView request into a [FilterRequest]. WebView does not say what a request is
 * for; the Accept header and the file extension tell most of it, the rest is OTHER (rules
 * restricted to other types then do not apply – rather let something through than break a page).
 */
object RequestTypes {
    private val IMAGE = setOf("png", "jpg", "jpeg", "gif", "webp", "svg", "ico", "avif", "bmp")
    private val FONT = setOf("woff", "woff2", "ttf", "otf", "eot")
    private val MEDIA = setOf("mp4", "webm", "mp3", "ogg", "m4a", "m3u8", "mpd")

    fun of(url: String, accept: String?): ResourceType {
        val path = url.substringBefore('?').substringBefore('#').lowercase()
        val ext = path.substringAfterLast('/').substringAfterLast('.', "")
        val a = accept?.lowercase().orEmpty()
        return when {
            a.startsWith("text/css") || ext == "css" -> ResourceType.STYLESHEET
            ext == "js" || ext == "mjs" -> ResourceType.SCRIPT
            a.startsWith("image/") || ext in IMAGE -> ResourceType.IMAGE
            ext in FONT || a.startsWith("font/") -> ResourceType.FONT
            ext in MEDIA || a.startsWith("video/") || a.startsWith("audio/") -> ResourceType.MEDIA
            a.startsWith("text/html") -> ResourceType.SUBDOCUMENT
            else -> ResourceType.OTHER
        }
    }

    fun request(r: WebResourceRequest, pageHost: String?): FilterRequest? {
        val url = r.url.toString()
        val host = r.url.host ?: return null
        if (r.url.scheme != "https" && r.url.scheme != "http") return null
        return FilterRequest(url, host.lowercase(), pageHost, of(url, r.requestHeaders["Accept"]))
    }

    /** Empty answer for a blocked request (no network access happens). */
    fun blockedResponse(): WebResourceResponse = WebResourceResponse("text/plain", "utf-8", 204, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
}
