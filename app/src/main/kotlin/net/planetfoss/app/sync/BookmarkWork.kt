// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import net.planetfoss.app.data.bookmarks.BookmarkRepository
import net.planetfoss.app.data.bookmarks.BookmarkScheduler
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Sends one saved article to the reader's bookmark service; retries while the server cannot be reached. */
@HiltWorker
class BookmarkSendWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repo: BookmarkRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val id = inputData.getLong(ITEM, -1L).takeIf { it >= 0 } ?: return Result.failure()
        val done = repo.send(id, lastAttempt = runAttemptCount >= MAX_ATTEMPTS - 1)
        return if (done) Result.success() else Result.retry()
    }

    companion object {
        const val ITEM = "item"
        /** With the backoff below the last try is roughly a day after saving: a server that was down overnight still gets it. */
        const val MAX_ATTEMPTS = 8
    }
}

/** One unique job per article, only with a network; no periodic work. */
@Singleton
class WorkBookmarkScheduler @Inject constructor(@ApplicationContext private val context: Context) : BookmarkScheduler {
    override fun send(id: Long) {
        val request = OneTimeWorkRequestBuilder<BookmarkSendWorker>()
            .setInputData(workDataOf(BookmarkSendWorker.ITEM to id))
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .addTag(TAG)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("$TAG-$id", ExistingWorkPolicy.REPLACE, request)
    }

    override fun cancelAll() {
        WorkManager.getInstance(context).cancelAllWorkByTag(TAG)
    }

    private companion object {
        const val TAG = "bookmark-send"
    }
}
