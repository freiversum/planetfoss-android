// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.data

import kotlinx.coroutines.flow.Flow
import net.planetfoss.app.data.db.FollowDao
import net.planetfoss.app.data.db.FollowEntity
import net.planetfoss.core.api.ListTarget
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Topics and sources the user follows – the app's answer to the website's RSS feeds per topic and source.
 * Stored only on the phone; the follow check asks the public API for their first page, nothing more.
 */
@Singleton
class FollowRepository @Inject constructor(private val dao: FollowDao, private val clock: Clock, private val widgets: WidgetStore) {
    val all: Flow<List<FollowEntity>> = dao.observeAll()

    /**
     * Follow [target] (a topic or source list). The posts on screen ([known], their ids) and everything up to [newest]
     * (epoch ms) count as known, so only later ones are announced.
     */
    suspend fun follow(target: ListTarget, label: String, newest: Long?, known: Collection<Long> = emptyList()) {
        val (kind, ref) = kindRef(target) ?: return
        dao.insert(FollowEntity(kind, ref, label.ifBlank { ref }, newest ?: clock.millis(), clock.millis(), known.joinToString(",")))
        widgets.radarChanged()
    }

    suspend fun unfollow(kind: String, ref: String) {
        dao.delete(kind, ref)
        widgets.radarChanged()
    }

    companion object {
        const val TOPIC = "topic"
        const val SOURCE = "source"

        /** Only topic and source lists can be followed. */
        fun kindRef(target: ListTarget): Pair<String, String>? = when (target) {
            is ListTarget.Topic -> TOPIC to target.topicKey
            is ListTarget.Source -> SOURCE to target.slug
            else -> null
        }

        fun targetOf(kind: String, ref: String): ListTarget? = runCatching {
            when (kind) {
                TOPIC -> ListTarget.Topic(ref).also { it.endpoint }
                SOURCE -> ListTarget.Source(ref).also { it.endpoint }
                else -> null
            }
        }.getOrNull()
    }
}
