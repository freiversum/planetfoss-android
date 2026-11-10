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
import kotlinx.coroutines.flow.first
import net.planetfoss.app.MainActivity
import net.planetfoss.app.R
import net.planetfoss.app.data.FeedRepository
import net.planetfoss.app.data.FollowRepository
import net.planetfoss.app.data.db.FollowDao
import net.planetfoss.app.data.db.FollowEntity
import net.planetfoss.app.data.prefs.SettingsRepository
import net.planetfoss.core.api.ApiException
import net.planetfoss.core.api.Item
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One check of everything the user follows: page 1 of each topic or source (in the content language), posts newer
 * than the last known one are announced – one notification per topic or source – and become the new mark.
 * A list that fails is skipped this time; the others still get checked.
 */
@Singleton
class FollowChecker @Inject constructor(
    private val dao: FollowDao,
    private val feed: FeedRepository,
    private val settings: SettingsRepository,
    private val notifier: AndroidFollowNotifier,
) {
    /** Returns how many follows had news. Throws only when every request failed (the worker then retries). */
    suspend fun check(): Int {
        val follows = dao.all()
        if (follows.isEmpty()) return 0
        val lang = settings.settings.first().contentLang
        var failed = 0
        var news = 0
        for (f in follows) {
            val target = FollowRepository.targetOf(f.kind, f.ref) ?: continue
            val items = try {
                feed.firstPage(target, lang).items
            } catch (e: ApiException) {
                failed++
                continue
            }
            val fresh = newer(items, f.lastSeen)
            if (fresh.isEmpty()) continue
            dao.seen(f.kind, f.ref, fresh.maxOf { it.publishedAt.toEpochMilli() })
            notifier.notify(f, fresh)
            news++
        }
        if (failed == follows.size) throw ApiException.Network(java.io.IOException("every follow failed"))
        return news
    }

    companion object {
        /** Posts after [lastSeen] (epoch ms), newest first. */
        fun newer(items: List<Item>, lastSeen: Long): List<Item> =
            items.filter { it.publishedAt.toEpochMilli() > lastSeen }.sortedByDescending { it.publishedAt }
    }
}

@HiltWorker
class FollowCheckWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val checker: FollowChecker,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        checker.check()
        Result.success()
    } catch (e: ApiException) {
        if (runAttemptCount < 3) Result.retry() else Result.failure()
    }
}

/**
 * Runs the follow check periodically while anything is followed, in the supernova interval – and, like the
 * supernova check, once right away whenever this is applied (app start, first follow): periodic work only starts
 * after a full interval.
 */
@Singleton
class FollowScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    fun apply(enabled: Boolean, intervalMinutes: Int) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(WORK)
            wm.cancelUniqueWork(NOW)
            return
        }
        val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        val request = PeriodicWorkRequestBuilder<FollowCheckWorker>(intervalMinutes.toLong().coerceAtLeast(15), TimeUnit.MINUTES)
            .setConstraints(network)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
        wm.enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<FollowCheckWorker>().setConstraints(network).build())
    }

    private companion object {
        const val WORK = "follow-check"
        const val NOW = "follow-check-now"
    }
}

/** Notifications for followed topics and sources: own channel, one per topic or source, a tap opens its list. */
@Singleton
class AndroidFollowNotifier @Inject constructor(@ApplicationContext private val context: Context) {
    fun createChannel() {
        val channel = NotificationChannel(CHANNEL, context.getString(R.string.channel_follows), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = context.getString(R.string.channel_follows_text) }
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
    fun notify(follow: FollowEntity, fresh: List<Item>) {
        if (!canNotify()) return
        createChannel()
        val text = if (fresh.size == 1) fresh[0].title else context.resources.getQuantityString(R.plurals.follow_new, fresh.size, fresh.size)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_supernova)
            .setContentTitle(follow.label)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(fresh.take(5).joinToString("\n") { "• " + it.title + " – " + it.source.name }))
            .setContentIntent(open(follow))
            .setAutoCancel(true)
            .setGroup(GROUP)
            .setWhen(fresh.first().publishedAt.toEpochMilli())
            .setShowWhen(true)
            .build()
        NotificationManagerCompat.from(context).notify((follow.kind + ":" + follow.ref).hashCode(), n)
    }

    /** planetfoss://topic/<key> or planetfoss://source/<slug>, only for this package. */
    private fun open(f: FollowEntity): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, "planetfoss://${f.kind}/${f.ref}".toUri(), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(context, (f.kind + f.ref).hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private companion object {
        const val CHANNEL = "follows"
        const val GROUP = "net.planetfoss.FOLLOWS"
    }
}
