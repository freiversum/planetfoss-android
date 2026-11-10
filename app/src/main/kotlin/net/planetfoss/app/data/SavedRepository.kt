// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.planetfoss.app.data.db.SavedDao
import net.planetfoss.app.data.db.SavedItemEntity
import net.planetfoss.core.api.Item
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton

/** Saved articles, local only. Nothing about them is sent anywhere. */
@Singleton
class SavedRepository @Inject constructor(
    private val dao: SavedDao,
    private val json: Json,
    private val clock: Clock,
) {
    val items: Flow<List<Item>> = dao.observeAll().map { rows ->
        rows.mapNotNull { runCatching { json.decodeFromString(Item.serializer(), it) }.getOrNull() }
    }

    val ids: Flow<Set<Long>> = dao.observeIds().map { it.toSet() }

    suspend fun save(item: Item) = dao.insert(SavedItemEntity(item.id, json.encodeToString(Item.serializer(), item), clock.millis()))

    suspend fun remove(id: Long) = dao.delete(id)

    /**
     * All saved articles as a file (JSON) for a backup or another phone – the app itself has Android's backup switched off.
     * The file holds the articles as the API delivered them, nothing else.
     */
    suspend fun export(): String {
        val items = items.first()
        return json.encodeToString(SavedExport.serializer(), SavedExport(SavedExport.FORMAT, 1, items))
    }

    /** Reads a file from [export]; articles already saved stay as they are. Returns how many were added, null if it is no such file. */
    suspend fun import(text: String): Int? {
        val file = runCatching { json.decodeFromString(SavedExport.serializer(), text) }.getOrNull()?.takeIf { it.format == SavedExport.FORMAT } ?: return null
        var added = 0
        for (item in file.items.reversed()) {
            if (dao.count(item.id) == 0) {
                save(item)
                added++
            }
        }
        return added
    }

    /** Save if not saved, else remove. Returns the new state. */
    suspend fun toggle(item: Item): Boolean = if (dao.count(item.id) > 0) {
        remove(item.id)
        false
    } else {
        save(item)
        true
    }
}

/** The export file: format tag and version (both required, so any other JSON is refused), then the saved articles (newest saved first). */
@Serializable
data class SavedExport(val format: String, val version: Int, val items: List<Item>) {
    companion object {
        const val FORMAT = "planetfoss-saved"
    }
}
