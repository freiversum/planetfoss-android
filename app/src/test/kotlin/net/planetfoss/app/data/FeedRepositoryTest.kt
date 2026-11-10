// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import net.planetfoss.app.data.db.AppDatabase
import net.planetfoss.core.api.ApiException
import net.planetfoss.core.api.ApiJson
import net.planetfoss.core.api.ContentLang
import net.planetfoss.core.api.ListTarget
import net.planetfoss.core.api.PlanetFossApi
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FeedRepositoryTest {
    private val server = MockWebServer()
    private lateinit var db: AppDatabase
    private lateinit var repo: FeedRepository
    private var now = Instant.parse("2026-10-29T18:00:00Z")
    private val clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant() = now
    }

    @Before
    fun setUp() {
        server.start()
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        repo = FeedRepository(PlanetFossApi(OkHttpClient(), server.url("/"), ApiJson), db.feedDao(), ApiJson, clock)
    }

    @After
    fun tearDown() {
        db.close()
        server.close()
    }

    private fun item(id: Long, lang: String = "en") =
        """{"id":$id,"url":"https://example.org/$id","title":"Item $id","lang":"$lang","published_at":"2026-10-29T1${id % 10}:00:00Z","source":{"slug":"s","name":"S"}}"""

    private fun page(ids: List<Long>, more: Boolean, lang: String = "en") =
        server.enqueue(MockResponse.Builder().body("""{"items":[${ids.joinToString(",") { item(it, lang) }}],"has_more":$more}""").build())

    @Test
    fun firstFetchFillsTheList() = runTest {
        val empty = repo.observe(ListTarget.Home, ContentLang.EN).first()
        assertTrue(empty.items.isEmpty())
        assertNull(empty.fetchedAt)
        assertTrue(repo.isStale(ListTarget.Home, ContentLang.EN))

        page(listOf(3, 2, 1), more = true)
        repo.refresh(ListTarget.Home, ContentLang.EN)
        val list = repo.observe(ListTarget.Home, ContentLang.EN).first()
        assertEquals(listOf(3L, 2L, 1L), list.items.map { it.id })
        assertEquals(now.toEpochMilli(), list.fetchedAt)
        assertTrue(list.hasMore)
        assertFalse(repo.isStale(ListTarget.Home, ContentLang.EN))
    }

    @Test
    fun nextPageAppendsAndSkipsItemsThatMovedDown() = runTest {
        page(listOf(5, 4, 3), more = true)
        repo.refresh(ListTarget.Home, ContentLang.EN)
        page(listOf(3, 2, 1), more = false) // 3 slid onto page 2 because something new arrived
        repo.loadMore(ListTarget.Home, ContentLang.EN)
        assertEquals("page=2", server.run { takeRequest(); takeRequest() }.url.query!!.substringAfter("&ui=").substringAfter("&"))
        val list = repo.observe(ListTarget.Home, ContentLang.EN).first()
        assertEquals(listOf(5L, 4L, 3L, 2L, 1L), list.items.map { it.id })
        assertFalse(list.hasMore)
        assertEquals(2, list.pages)
    }

    @Test
    fun refreshReplacesTheList() = runTest {
        page(listOf(3, 2, 1), more = true)
        repo.refresh(ListTarget.Home, ContentLang.EN)
        page(listOf(6, 5), more = false)
        repo.refresh(ListTarget.Home, ContentLang.EN)
        assertEquals(listOf(6L, 5L), repo.observe(ListTarget.Home, ContentLang.EN).first().items.map { it.id })
    }

    @Test
    fun languagesHaveSeparateLists() = runTest {
        page(listOf(1, 2), more = false, lang = "de")
        repo.refresh(ListTarget.Home, ContentLang.DE)
        page(listOf(3), more = false)
        repo.refresh(ListTarget.Home, ContentLang.EN)
        assertEquals(listOf(1L, 2L), repo.observe(ListTarget.Home, ContentLang.DE).first().items.map { it.id })
        assertEquals(listOf(3L), repo.observe(ListTarget.Home, ContentLang.EN).first().items.map { it.id })
    }

    @Test
    fun failedRefreshKeepsTheCache() = runTest {
        page(listOf(2, 1), more = false)
        repo.refresh(ListTarget.Home, ContentLang.EN)
        server.enqueue(MockResponse.Builder().code(503).build())
        try {
            repo.refresh(ListTarget.Home, ContentLang.EN)
            fail()
        } catch (e: ApiException.Http) {
            assertEquals(503, e.code)
        }
        assertEquals(listOf(2L, 1L), repo.observe(ListTarget.Home, ContentLang.EN).first().items.map { it.id })
    }

    @Test
    fun staleAfterFiveMinutes() = runTest {
        page(listOf(1), more = false)
        repo.refresh(ListTarget.Home, ContentLang.EN)
        now = now.plusSeconds(4 * 60)
        assertFalse(repo.isStale(ListTarget.Home, ContentLang.EN))
        now = now.plusSeconds(2 * 60)
        assertTrue(repo.isStale(ListTarget.Home, ContentLang.EN))
    }

    @Test
    fun supernovasAreCachedPerLanguage() = runTest {
        server.enqueue(
            MockResponse.Builder().body(
                """{"supernovas":[{"id":"tag:planetfoss.net,2026:supernova/a/1","key":"a","label":"A","ignited_at":"2026-10-29T15:00:00Z","announced_at":"2026-10-29T15:30:00Z","url":"https://planetfoss.net/thema/a/","burning":true}]}""",
            ).build(),
        )
        repo.refreshSupernovas(ContentLang.ALL)
        val (list, fetched) = repo.observeSupernovas(ContentLang.ALL).first()
        assertEquals(listOf("a"), list.supernovas.map { it.key })
        assertEquals(now.toEpochMilli(), fetched)
        assertTrue(repo.observeSupernovas(ContentLang.DE).first().first.supernovas.isEmpty())
    }

    @Test
    fun topicListKeepsItsTitleAcrossPages() = runTest {
        server.enqueue(MockResponse.Builder().body("""{"topic":{"key":"gnome-52","label":"GNOME 52"},"items":[${item(1)}],"has_more":true}""").build())
        repo.refresh(ListTarget.Topic("gnome-52"), ContentLang.ALL)
        assertEquals("/api/app/v1/topic/gnome-52", server.takeRequest().url.encodedPath)
        server.enqueue(MockResponse.Builder().body("""{"items":[${item(2)}],"has_more":false}""").build())
        repo.loadMore(ListTarget.Topic("gnome-52"), ContentLang.ALL)
        val list = repo.observe(ListTarget.Topic("gnome-52"), ContentLang.ALL).first()
        assertEquals("GNOME 52", list.title)
        assertEquals(listOf(1L, 2L), list.items.map { it.id })
        // a different list with the same item does not disturb this one
        assertTrue(repo.observe(ListTarget.Rubric("desktop"), ContentLang.ALL).first().items.isEmpty())
    }
}
