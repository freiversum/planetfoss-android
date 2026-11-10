// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
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
import net.planetfoss.app.MainActivity
import net.planetfoss.app.R
import net.planetfoss.app.data.FeedRepository
import net.planetfoss.app.data.prefs.SettingsRepository
import net.planetfoss.core.api.ApiException
import net.planetfoss.core.api.LogbookEntry
import net.planetfoss.core.api.Use
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The morning check: asks planetfoss.net for the newest logbook entry (a background request, never a view) and announces
 * it once when its day is newer than the last one known. The first check after turning the notification on only stores
 * the current entry; quiet days are stored without a notification.
 */
@Singleton
class LogbookChecker @Inject constructor(
    private val feed: FeedRepository,
    private val settings: SettingsRepository,
    private val notifier: AndroidLogbookNotifier,
) {
    /** True when a notification went out. */
    suspend fun check(): Boolean {
        val s = settings.settings.first()
        if (!s.logbookNotify) return false
        val entry = feed.logbook(null, s.contentLang, Use.BACKGROUND)
        val (store, notify) = decide(settings.logbookSeen(), entry.day, entry.quiet)
        if (store) settings.setLogbookSeen(entry.day)
        if (notify) notifier.notify(entry)
        return notify
    }

    companion object {
        /**
         * What to do with the newest entry [day] given the last known day [seen] (null = turned on just now):
         * (store the day, send a notification). Days are YYYY-MM-DD, so they compare as strings.
         */
        fun decide(seen: String?, day: String, quiet: Boolean): Pair<Boolean, Boolean> = when {
            seen == null -> true to false
            day > seen -> true to !quiet
            else -> false to false
        }
    }
}

@HiltWorker
class LogbookCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val checker: LogbookChecker,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        checker.check()
        Result.success()
    } catch (e: ApiException) {
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}

/**
 * Runs the morning check every hour while the switch is on – an entry is out from 6:00 Berlin time, so the notification
 * comes within the hour after (Android may delay it in Doze). Like the other checks once right away when applied.
 */
@Singleton
class LogbookScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    fun apply(enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(WORK)
            wm.cancelUniqueWork(NOW)
            return
        }
        val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = PeriodicWorkRequestBuilder<LogbookCheckWorker>(INTERVAL_MINUTES, TimeUnit.MINUTES)
            .setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        wm.enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<LogbookCheckWorker>().setConstraints(network).build())
    }

    private companion object {
        const val WORK = "logbook-check"
        const val NOW = "logbook-check-now"
        const val INTERVAL_MINUTES = 60L
    }
}

/** The logbook notification: own channel, one at a time (a new entry replaces yesterday's), a tap opens the entry. */
@Singleton
class AndroidLogbookNotifier @Inject constructor(@ApplicationContext private val context: Context) {
    fun createChannel() {
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.channel_logbook), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = context.getString(R.string.channel_logbook_text) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun canNotify(): Boolean {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        val channel = nm.getNotificationChannel(CHANNEL)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    @android.annotation.SuppressLint("MissingPermission") // checked in canNotify()
    fun notify(e: LogbookEntry) {
        if (!canNotify()) return
        createChannel()
        val novas = e.supernovas.size
        val title = context.getString(R.string.logbook_kicker, e.label)
        val text = if (novas > 0) context.resources.getQuantityString(R.plurals.logbook_notify_text, novas, e.headline, novas) else e.headline
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_supernova)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(e.lede.ifEmpty { text }))
            .setContentIntent(open(e.day))
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(ID, n)
    }

    /** planetfoss://logbook/<day>, only for this package. */
    private fun open(day: String): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, "planetfoss://logbook/$day".toUri(), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, ID, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private companion object {
        const val CHANNEL = "logbook"
        const val ID = 0x10_6B00
    }
}
