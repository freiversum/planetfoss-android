// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import net.planetfoss.app.data.db.MyStarEntity
import net.planetfoss.app.data.db.StarDao
import net.planetfoss.core.api.PlanetFossApi
import net.planetfoss.core.api.ProofOfWork
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stars for the Sternstunden, the way the website hands them out: fetch a task, solve it on the
 * phone (proof of work, under two seconds), send item, task and solution; planetfoss.net answers with
 * a token that takes the star back. The token stays on the phone ([StarDao]); no account, no identifier.
 */
@Singleton
class StarRepository @Inject constructor(
    private val api: PlanetFossApi,
    private val dao: StarDao,
    private val clock: Clock,
) {
    /** Items starred from this phone. */
    val ids: Flow<Set<Long>> = dao.observeIds().map { it.toSet() }

    private val _counts = MutableStateFlow<Map<Long, Int>>(emptyMap())

    /** Star counts the server reported after a star in this session; they win over the (cached) list until it refreshes. */
    val counts: StateFlow<Map<Long, Int>> = _counts.asStateFlow()

    private val _busy = MutableStateFlow<Set<Long>>(emptySet())

    /** Items whose star is on its way (task being solved or sent). */
    val busy: StateFlow<Set<Long>> = _busy.asStateFlow()

    /**
     * Star the item, or take the star back if it is starred from here. Throws when planetfoss.net
     * refuses or cannot be reached; then nothing changes on the phone.
     */
    suspend fun toggle(itemId: Long) {
        if (itemId in _busy.value) return
        _busy.update { it + itemId }
        try {
            val token = dao.token(itemId)
            val answer = if (token != null) {
                api.unstar(itemId, token).also { if (it.ok || it.error == "unbekannt") dao.delete(itemId) }
            } else {
                val task = api.starChallenge(itemId)
                val nonce = task.nonce ?: throw StarRefused(task.error ?: "no task")
                val solution = withContext(Dispatchers.Default) { ProofOfWork.solve(nonce, task.bits) }
                api.star(itemId, nonce, solution).also { a -> a.token?.takeIf { a.ok }?.let { dao.insert(MyStarEntity(itemId, it, clock.millis())) } }
            }
            answer.stars?.let { n -> _counts.update { it + (itemId to n) } }
            // a token the server no longer knows (star removed there) is simply dropped
            if (!answer.ok && !(token != null && answer.error == "unbekannt")) throw StarRefused(answer.error ?: "refused")
        } finally {
            _busy.update { it - itemId }
        }
    }
}

/** planetfoss.net did not take the star (task expired, item unknown …). */
class StarRefused(reason: String) : Exception(reason)
