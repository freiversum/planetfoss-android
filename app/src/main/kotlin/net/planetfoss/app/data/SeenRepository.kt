// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import net.planetfoss.app.data.prefs.SettingsRepository
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * “New since you last opened the app”: the time the app last went to the background, kept on the phone only (DataStore) –
 * the server never learns it, so no consent is needed (STRATEGIE.md, section 4.3). Posts published after [mark] carry a “new”
 * marker. A visit only counts as over after [AWAY_MS] in the background: a quick look at an article in the browser and back
 * keeps the markers.
 */
@Singleton
class SeenRepository @Inject constructor(
    private val store: DataStore<Preferences>,
    settings: SettingsRepository,
    private val clock: Clock,
) {
    /** Epoch ms of the previous visit's end; null when switched off or on the very first start. */
    val mark: Flow<Long?> = combine(store.data.map { it[MARK] }, settings.settings.map { it.markNew }) { m, on -> m.takeIf { on } }
        .distinctUntilChanged()

    /** The app came to the front: after a real absence the end of the last visit becomes the new mark. */
    suspend fun started() {
        val now = clock.millis()
        store.edit { p ->
            val left = p[LEFT] ?: return@edit
            if (now - left >= AWAY_MS) p[MARK] = left
        }
    }

    /** The app went to the background. */
    suspend fun stopped() {
        val now = clock.millis()
        store.edit { it[LEFT] = now }
    }

    /** “All seen”: everything up to now counts as known. */
    suspend fun allSeen() {
        val now = clock.millis()
        store.edit { it[MARK] = now }
    }

    companion object {
        const val AWAY_MS = 30_000L
        private val MARK = longPreferencesKey("seen_mark")
        private val LEFT = longPreferencesKey("seen_left")

        /** True when [publishedMs] is after [mark] – no mark, nothing is new. */
        fun isNew(publishedMs: Long, mark: Long?): Boolean = mark != null && publishedMs > mark
    }
}
