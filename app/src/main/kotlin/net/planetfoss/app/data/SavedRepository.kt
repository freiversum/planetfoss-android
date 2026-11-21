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

/** Told about every article saved by the reader (not by an import): the bookmark service sends it on. */
fun interface SaveHook {
    suspend fun onSaved(id: Long)
}

/**
 * Saved articles, kept on the phone. Nothing about them goes to planetfoss.net; only when the reader has set up their
 * own bookmark service does a newly saved article also go there ([BookmarkRepository]).
 */
@Singleton
class SavedRepository @Inject constructor(
    private val dao: SavedDao,
    private val json: Json,
    private val clock: Clock,
    private val hook: SaveHook = SaveHook {},
) {
    val items: Flow<List<Item>> = dao.observeAll().map { rows ->
        rows.mapNotNull { runCatching { json.decodeFromString(Item.serializer(), it) }.getOrNull() }
    }

    val ids: Flow<Set<Long>> = dao.observeIds().map { it.toSet() }

    /** Save on the phone. [send]: also to the reader's bookmark service, if one is set up (not for an import). */
    suspend fun save(item: Item, send: Boolean = true) {
        dao.insert(SavedItemEntity(item.id, json.encodeToString(Item.serializer(), item), clock.millis()))
        if (send) hook.onSaved(item.id)
    }

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
                save(item, send = false)
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
