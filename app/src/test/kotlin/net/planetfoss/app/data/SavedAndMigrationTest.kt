// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import net.planetfoss.app.data.db.AppDatabase
import net.planetfoss.app.ui.list.ItemListViewModel
import net.planetfoss.core.api.ApiJson
import net.planetfoss.core.api.Item
import net.planetfoss.core.api.ListTarget
import net.planetfoss.core.api.Source
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class SavedAndMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java)

    private fun item(id: Long) = Item(id, "https://example.org/$id", "Item $id", lang = "en", publishedAt = Instant.parse("2026-10-29T10:00:00Z"), source = Source("s", "S"))

    @Test
    fun savedItemsToggleAndComeNewestFirst() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        var t = Instant.parse("2026-10-29T18:00:00Z")
        val clock = object : Clock() {
            override fun getZone() = ZoneOffset.UTC
            override fun withZone(zone: java.time.ZoneId?) = this
            override fun instant() = t
        }
        val repo = SavedRepository(db.savedDao(), ApiJson, clock)
        assertTrue(repo.toggle(item(1)))
        t = t.plusSeconds(60)
        assertTrue(repo.toggle(item(2)))
        assertEquals(listOf(2L, 1L), repo.items.first().map { it.id })
        assertEquals(setOf(1L, 2L), repo.ids.first())
        assertFalse(repo.toggle(item(1)))
        assertEquals(listOf(2L), repo.items.first().map { it.id })
        // saved items survive the list cache being pruned
        db.feedDao().pruneItems(Long.MAX_VALUE)
        assertEquals(listOf(2L), repo.items.first().map { it.id })
        db.close()
    }

    @Test
    fun migrationToVersion4AddsFollows() {
        helper.createDatabase("m4.db", 3).apply {
            execSQL("INSERT INTO my_star (itemId, token, starredAt) VALUES (7, 'tok', 2)")
            close()
        }
        helper.runMigrationsAndValidate("m4.db", 4, true).apply {
            query("SELECT COUNT(*) FROM my_star").use { c -> assertTrue(c.moveToFirst() && c.getInt(0) == 1) }
            execSQL("INSERT INTO follow (kind, ref, label, lastSeen, addedAt) VALUES ('topic', 'gnome', 'GNOME', 1, 1)")
            close()
        }
    }

    @Test
    fun migrationToVersion5KeepsFollowsAndAddsKnownIds() {
        helper.createDatabase("m5.db", 4).apply {
            execSQL("INSERT INTO follow (kind, ref, label, lastSeen, addedAt) VALUES ('topic', 'gnome', 'GNOME', 1, 1)")
            close()
        }
        helper.runMigrationsAndValidate("m5.db", 5, true).apply {
            query("SELECT known FROM follow WHERE ref = 'gnome'").use { c -> assertTrue(c.moveToFirst() && c.getString(0) == "") }
            close()
        }
    }

    /** 19.11.: the English sister of a post was announced; the German original reached planetfoss.net later, with an older time. */
    @Test
    fun followCheckAnnouncesAPostThatArrivesLateWithAnOlderTime() {
        val newer = net.planetfoss.app.sync.FollowChecker::newer
        val english = item(10).copy(publishedAt = Instant.parse("2026-11-18T16:38:48Z"))
        val german = item(11).copy(publishedAt = Instant.parse("2026-11-18T16:37:03Z"))
        val archive = item(12).copy(publishedAt = Instant.parse("2026-10-01T08:00:00Z"))
        val mark = english.publishedAt.toEpochMilli()
        // by time alone the late post is missed – by the ids of the last check it is new
        assertEquals(emptyList<Long>(), newer(listOf(english, german), mark, emptySet()).map { it.id })
        assertEquals(listOf(11L), newer(listOf(english, german), mark, setOf(10L)).map { it.id })
        // nothing twice, and no post from a source's archive
        assertEquals(emptyList<Long>(), newer(listOf(english, german), mark, setOf(10L, 11L)).map { it.id })
        assertEquals(emptyList<Long>(), newer(listOf(english, archive), mark, setOf(10L)).map { it.id })
    }

    @Test
    fun followCheckAnnouncesOnlyNewerPosts() {
        val old = item(1).copy(publishedAt = Instant.parse("2026-10-29T10:00:00Z"))
        val new = item(2).copy(publishedAt = Instant.parse("2026-10-29T12:00:00Z"))
        val mark = Instant.parse("2026-10-29T11:00:00Z").toEpochMilli()
        assertEquals(listOf(2L), net.planetfoss.app.sync.FollowChecker.newer(listOf(old, new), mark).map { it.id })
        assertEquals(ListTarget.Source("heise-online"), ItemListViewModel.targetFrom(null, null, null, "heise-online"))
        assertEquals(net.planetfoss.app.data.FollowRepository.SOURCE to "heise-online",
            net.planetfoss.app.data.FollowRepository.kindRef(ListTarget.Source("heise-online")))
        assertEquals(null, net.planetfoss.app.data.FollowRepository.kindRef(ListTarget.Home))
    }

    @Test
    fun migrationToVersion3KeepsSavedItemsAndAddsStars() {
        helper.createDatabase("m3.db", 2).apply {
            execSQL("INSERT INTO saved_item (id, json, savedAt) VALUES (7, '{}', 1)")
            close()
        }
        helper.runMigrationsAndValidate("m3.db", 3, true).apply {
            query("SELECT COUNT(*) FROM saved_item").use { c -> assertTrue(c.moveToFirst() && c.getInt(0) == 1) }
            execSQL("INSERT INTO my_star (itemId, token, starredAt) VALUES (7, 'tok', 2)")
            query("SELECT token FROM my_star WHERE itemId = 7").use { c -> assertTrue(c.moveToFirst() && c.getString(0) == "tok") }
            close()
        }
    }

    @Test
    fun migrationFromVersion1KeepsCachedLists() {
        helper.createDatabase("m.db", 1).apply {
            execSQL("INSERT INTO list_meta (listKey, fetchedAt, pages, hasMore) VALUES ('home:', 1, 1, 0)")
            execSQL("INSERT INTO seen_supernova (id, `key`, firstSeen, notified) VALUES ('tag:x', 'x', 1, 1)")
            close()
        }
        helper.runMigrationsAndValidate("m.db", 2, true).apply {
            query("SELECT title FROM list_meta WHERE listKey = 'home:'").use { c ->
                assertTrue(c.moveToFirst())
                assertTrue(c.isNull(0))
            }
            query("SELECT COUNT(*) FROM seen_supernova").use { c -> c.moveToFirst(); assertEquals(1, c.getInt(0)) }
            query("SELECT COUNT(*) FROM saved_item").use { c -> c.moveToFirst(); assertEquals(0, c.getInt(0)) }
            close()
        }
    }

    @Test
    fun navigationArgumentsBecomeListTargets() {
        assertEquals(ListTarget.Home, ItemListViewModel.targetFrom(null, null, null))
        assertEquals(ListTarget.Videos, ItemListViewModel.targetFrom("videos", "", null))
        assertEquals(ListTarget.Starred, ItemListViewModel.targetFrom("starred", "", null))
        assertEquals(ListTarget.Rubric("desktop"), ItemListViewModel.targetFrom("rubric", "desktop", null))
        assertEquals(ListTarget.Topic("gnome-52"), ItemListViewModel.targetFrom(null, null, "gnome-52"))
        assertEquals(ListTarget.Source("linuxiac"), ItemListViewModel.targetFrom("source", "linuxiac", null))
        // broken deep links and unknown kinds are no list
        assertNull(ItemListViewModel.targetFrom(null, null, "../admin"))
        assertNull(ItemListViewModel.targetFrom("rubric", "", null))
        assertNull(ItemListViewModel.targetFrom("evil", "x", null))
    }

    @Test
    fun exportAndImportKeepWhatIsAlreadySaved() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        val repo = SavedRepository(db.savedDao(), ApiJson, Clock.systemUTC())
        repo.save(item(1))
        repo.save(item(2))
        val file = repo.export()
        // another phone with one of the two already saved: only the other is added
        val other = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        val repo2 = SavedRepository(other.savedDao(), ApiJson, Clock.systemUTC())
        repo2.save(item(2))
        assertEquals(1, repo2.import(file))
        assertEquals(setOf(1L, 2L), repo2.ids.first())
        // anything else is refused
        assertEquals(null, repo2.import("{\"items\":[]}"))
        assertEquals(null, repo2.import("kein JSON"))
        db.close()
        other.close()
    }
}
