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
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import net.planetfoss.app.MainActivity
import net.planetfoss.app.R
import net.planetfoss.core.api.ApiException
import net.planetfoss.core.api.Supernova
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Periodic supernova check. No foreground service: WorkManager runs it when Android allows. */
@HiltWorker
class NovaCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val checker: NovaChecker,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        checker.check()
        Result.success()
    } catch (e: ApiException.Malformed) {
        Result.failure() // a retry would get the same answer; the next period tries again
    } catch (e: ApiException) {
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}

/**
 * Starts, changes and stops the periodic check. With notifications on, every call also runs
 * one check right away: the first run after turning on sets the silent baseline at once
 * (periodic work would only start after a full interval, and whatever ignited meanwhile
 * would end up in the baseline), and opening the app gives a current check.
 */
@Singleton
class NovaScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    fun apply(enabled: Boolean, intervalMinutes: Int) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(WORK)
            wm.cancelUniqueWork(NOW)
            return
        }
        val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = PeriodicWorkRequestBuilder<NovaCheckWorker>(intervalMinutes.toLong().coerceAtLeast(15), TimeUnit.MINUTES)
            .setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()
        // UPDATE keeps the schedule when nothing changed and applies a new interval otherwise
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        // KEEP: a check that is already waiting (e.g. for network) is not queued twice
        wm.enqueueUniqueWork(
            NOW,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<NovaCheckWorker>().setConstraints(network)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES).build(),
        )
    }

    private companion object {
        const val WORK = "supernova-check"
        const val NOW = "supernova-check-now"
    }
}

/** Android notifications for new supernovas: own channel, one per supernova, grouped from two on. */
@Singleton
class AndroidNovaNotifier @Inject constructor(@ApplicationContext private val context: Context) : NovaNotifier {
    fun createChannel() {
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.channel_supernovas), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = context.getString(R.string.channel_supernovas_text) }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun canNotify(): Boolean {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val channel = nm.getNotificationChannel(CHANNEL)
        return channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE
    }

    @android.annotation.SuppressLint("MissingPermission") // checked in canNotify()
    override fun notify(novas: List<Supernova>) {
        if (!canNotify()) return
        createChannel()
        val nm = NotificationManagerCompat.from(context)
        novas.forEach { nova ->
            val text = listOfNotNull(nova.summary, nova.quote?.let { context.getString(R.string.quote, it.text) + " – " + it.source }).joinToString("\n")
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_supernova)
                .setContentTitle(context.getString(R.string.notify_title, nova.label))
                .setContentText(nova.summary ?: context.getString(R.string.notify_text_fallback))
                .setStyle(NotificationCompat.BigTextStyle().bigText(text.ifEmpty { context.getString(R.string.notify_text_fallback) }))
                .setContentIntent(openNova(nova.key))
                .setAutoCancel(true)
                .setGroup(GROUP)
                .setWhen(nova.announcedAt.toEpochMilli())
                .setShowWhen(true)
                .build()
            nm.notify(nova.id.hashCode(), n)
        }
        if (novas.size >= 2 || nm.activeNotifications.count { it.notification.group == GROUP } >= 2) {
            nm.notify(
                SUMMARY_ID,
                NotificationCompat.Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_supernova)
                    .setContentTitle(context.getString(R.string.channel_supernovas))
                    .setGroup(GROUP)
                    .setGroupSummary(true)
                    .setAutoCancel(true)
                    .build(),
            )
        }
    }

    /** Deep link into the app (planetfoss://supernova/<key>), only for this package. */
    private fun openNova(key: String): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, "planetfoss://supernova/$key".toUri(), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, key.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    companion object {
        const val CHANNEL = "supernova"
        private const val GROUP = "net.planetfoss.SUPERNOVAS"
        private const val SUMMARY_ID = 1
    }
}
