// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.data.db

import androidx.room.AutoMigration
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/** One cached item. [json] is the API object as received, so new API fields need no migration. */
@Entity(tableName = "item", indices = [Index("cachedAt")])
data class ItemEntity(
    @PrimaryKey val id: Long,
    val json: String,
    val publishedAt: Long,
    val cachedAt: Long,
)

/** Position of an item in one list (“home:de”, later “rubric:sicherheit:” …). */
@Entity(
    tableName = "list_entry",
    primaryKeys = ["listKey", "position"],
    indices = [Index(value = ["listKey", "itemId"], unique = true), Index("itemId")],
)
data class ListEntryEntity(val listKey: String, val position: Int, val itemId: Long)

/** State of one list: when page 1 was fetched, how far it was paged, its name from the API head. */
@Entity(tableName = "list_meta")
data class ListMetaEntity(
    @PrimaryKey val listKey: String,
    val fetchedAt: Long,
    val pages: Int,
    val hasMore: Boolean,
    @ColumnInfo(defaultValue = "NULL") val title: String? = null,
)

/**
 * A saved article: the item as it was when saved, so the list works offline and
 * survives the item leaving the cache. Only what the API sent – no page content.
 */
@Entity(tableName = "saved_item", indices = [Index("savedAt")])
data class SavedItemEntity(
    @PrimaryKey val id: Long,
    val json: String,
    val savedAt: Long,
    /** How far it got to the reader's bookmark service (RemoteState); 0 = not sent, nothing to send. */
    @ColumnInfo(defaultValue = "0") val remote: Int = 0,
)

/** One row of [SavedDao.observeRemoteCounts]. */
data class RemoteCount(val remote: Int, val n: Int)

/**
 * An item the user starred on planetfoss.net from the app, with the token that takes the star back
 * (like the website's localStorage). Only on the phone; the server keeps a hash of the token, nothing else.
 */
@Entity(tableName = "my_star")
data class MyStarEntity(
    @PrimaryKey val itemId: Long,
    val token: String,
    val starredAt: Long,
)

/**
 * A topic or source the user follows: [kind] `topic` or `source`, [ref] its key or slug, [label] its name, [lastSeen]
 * the newest post (epoch ms) already known, [known] the ids of the list's first page at the last check (comma separated) –
 * a post is announced when it is not among them: one that planetfoss.net picks up late carries an older time than the
 * newest known post and would be missed by the time alone. Only on the phone.
 */
@Entity(tableName = "follow", primaryKeys = ["kind", "ref"])
data class FollowEntity(
    val kind: String,
    val ref: String,
    val label: String,
    val lastSeen: Long,
    val addedAt: Long,
    @androidx.room.ColumnInfo(defaultValue = "") val known: String = "",
) {
    fun knownIds(): Set<Long> = known.split(',').mapNotNull { it.toLongOrNull() }.toSet()
}

/** The supernova list per language, stored as the API answer. */
@Entity(tableName = "nova_list")
data class NovaListEntity(
    @PrimaryKey val listKey: String,
    val json: String,
    val fetchedAt: Long,
)

/** Every supernova id the app has seen (NovaDetector); pruned after 30 days. */
@Entity(tableName = "seen_supernova", indices = [Index("firstSeen")])
data class SeenSupernovaEntity(
    @PrimaryKey val id: String,
    val key: String,
    val firstSeen: Long,
    val notified: Boolean,
)

@Dao
interface FeedDao {
    @Query("SELECT i.json FROM list_entry e JOIN item i ON i.id = e.itemId WHERE e.listKey = :key ORDER BY e.position")
    fun observeList(key: String): Flow<List<String>>

    @Query("SELECT * FROM list_meta WHERE listKey = :key")
    fun observeMeta(key: String): Flow<ListMetaEntity?>

    @Query("SELECT * FROM list_meta WHERE listKey = :key")
    suspend fun meta(key: String): ListMetaEntity?

    @Upsert
    suspend fun upsertItems(items: List<ItemEntity>)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntries(entries: List<ListEntryEntity>)

    @Query("DELETE FROM list_entry WHERE listKey = :key")
    suspend fun clearList(key: String)

    @Upsert
    suspend fun upsertMeta(meta: ListMetaEntity)

    /** Page [page] of a list; page 1 replaces the list. Duplicates (an item moved to the next page) are skipped. */
    @Transaction
    suspend fun storePage(key: String, page: Int, items: List<ItemEntity>, hasMore: Boolean, now: Long, title: String? = null) {
        val old = meta(key)
        if (page == 1) clearList(key)
        upsertItems(items)
        insertEntries(items.mapIndexed { i, item -> ListEntryEntity(key, page * POSITIONS_PER_PAGE + i, item.id) })
        val fetchedAt = if (page == 1) now else old?.fetchedAt ?: now
        upsertMeta(ListMetaEntity(key, fetchedAt, page, hasMore, title ?: old?.title))
    }

    @Query("SELECT itemId FROM list_entry WHERE listKey = :key")
    suspend fun listIds(key: String): List<Long>

    @Query("SELECT MIN(position) FROM list_entry WHERE listKey = :key")
    suspend fun firstPosition(key: String): Int?

    /**
     * Page 1 fetched again for a list that is paged further: the posts that are new go on top, everything loaded stays.
     * Replacing the list (as [storePage] does) cut it back to its first page – a reader far down lost the place when the
     * list refreshed itself after a while away (19.11.). False when this cannot be done safely: nothing paged yet, or the
     * whole page is new, so more posts may be missing between it and the loaded ones – then the list is replaced.
     */
    @Transaction
    suspend fun mergeFirstPage(key: String, items: List<ItemEntity>, now: Long, title: String? = null): Boolean {
        val old = meta(key) ?: return false
        if (old.pages < 2) return false
        val have = listIds(key).toSet()
        val fresh = items.filter { it.id !in have }
        if (fresh.size == items.size) return false
        val first = firstPosition(key) ?: return false
        upsertItems(items)
        insertEntries(fresh.mapIndexed { i, item -> ListEntryEntity(key, first - fresh.size + i, item.id) })
        upsertMeta(old.copy(fetchedAt = now, title = title ?: old.title))
        return true
    }

    /** Cached items that no list shows any more and that are older than [before]. */
    @Query("DELETE FROM item WHERE cachedAt < :before AND id NOT IN (SELECT itemId FROM list_entry)")
    suspend fun pruneItems(before: Long): Int

    @Query("SELECT * FROM nova_list WHERE listKey = :key")
    fun observeNovas(key: String): Flow<NovaListEntity?>

    @Upsert
    suspend fun upsertNovas(list: NovaListEntity)

    companion object {
        const val POSITIONS_PER_PAGE = 1000
    }
}

@Dao
interface SavedDao {
    @Query("SELECT json FROM saved_item ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<String>>

    @Query("SELECT id FROM saved_item")
    fun observeIds(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(item: SavedItemEntity)

    @Query("DELETE FROM saved_item WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT COUNT(*) FROM saved_item WHERE id = :id")
    suspend fun count(id: Long): Int

    @Query("SELECT * FROM saved_item WHERE id = :id")
    suspend fun get(id: Long): SavedItemEntity?

    @Query("UPDATE saved_item SET remote = :state WHERE id = :id")
    suspend fun setRemote(id: Long, state: Int)

    @Query("UPDATE saved_item SET remote = :to WHERE remote = :from")
    suspend fun setRemoteWhere(from: Int, to: Int)

    @Query("SELECT id FROM saved_item WHERE remote = :state")
    suspend fun idsWithRemote(state: Int): List<Long>

    @Query("SELECT remote, COUNT(*) AS n FROM saved_item WHERE remote != 0 GROUP BY remote")
    fun observeRemoteCounts(): Flow<List<RemoteCount>>
}

@Dao
interface SeenSupernovaDao {
    @Query("SELECT id FROM seen_supernova")
    suspend fun ids(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(rows: List<SeenSupernovaEntity>): List<Long>

    @Query("DELETE FROM seen_supernova WHERE firstSeen < :before")
    suspend fun prune(before: Long): Int

    @Query("DELETE FROM seen_supernova")
    suspend fun clear()
}

@Dao
interface StarDao {
    @Query("SELECT itemId FROM my_star")
    fun observeIds(): Flow<List<Long>>

    @Query("SELECT token FROM my_star WHERE itemId = :itemId")
    suspend fun token(itemId: Long): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(star: MyStarEntity)

    @Query("DELETE FROM my_star WHERE itemId = :itemId")
    suspend fun delete(itemId: Long)
}

@Dao
interface FollowDao {
    @Query("SELECT * FROM follow ORDER BY label COLLATE NOCASE")
    fun observeAll(): Flow<List<FollowEntity>>

    @Query("SELECT * FROM follow")
    suspend fun all(): List<FollowEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(follow: FollowEntity)

    @Query("DELETE FROM follow WHERE kind = :kind AND ref = :ref")
    suspend fun delete(kind: String, ref: String)

    /** Notifications are switched on (again): everything up to [now] counts as known, nothing old is announced. */
    @Query("UPDATE follow SET lastSeen = :now, known = ''")
    suspend fun restart(now: Long)

    /** After a check: [at] moves the mark forward only, [known] replaces the ids of the first page. */
    @Query("UPDATE follow SET lastSeen = MAX(lastSeen, :at), known = :known WHERE kind = :kind AND ref = :ref")
    suspend fun seen(kind: String, ref: String, at: Long, known: String)
}

@Database(
    entities = [
        ItemEntity::class, ListEntryEntity::class, ListMetaEntity::class, NovaListEntity::class, SeenSupernovaEntity::class,
        SavedItemEntity::class, MyStarEntity::class, FollowEntity::class,
    ],
    version = 6,
    exportSchema = true,
    autoMigrations = [AutoMigration(from = 1, to = 2), AutoMigration(from = 2, to = 3), AutoMigration(from = 3, to = 4), AutoMigration(from = 4, to = 5), AutoMigration(from = 5, to = 6)],
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun feedDao(): FeedDao
    abstract fun savedDao(): SavedDao
    abstract fun starDao(): StarDao
    abstract fun followDao(): FollowDao
    abstract fun seenSupernovaDao(): SeenSupernovaDao
}
