// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.data.bookmarks

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.junit4.MockWebServerRule
import net.planetfoss.app.data.SavedRepository
import net.planetfoss.app.data.db.AppDatabase
import net.planetfoss.core.api.ApiJson
import net.planetfoss.core.api.Category
import net.planetfoss.core.api.Item
import net.planetfoss.core.api.Source
import net.planetfoss.core.api.TopicRef
import net.planetfoss.core.bookmarks.BookmarkClient
import net.planetfoss.core.bookmarks.BookmarkResult
import net.planetfoss.core.bookmarks.BookmarkService
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BookmarkRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()
    @get:Rule val web = MockWebServerRule()

    /** Not the Keystore (Robolectric has none): reversible, and never the plain token. */
    private val cipher = object : TokenCipher {
        override fun encrypt(plain: String) = "enc:" + plain.reversed()
        override fun decrypt(stored: String) = stored.removePrefix("enc:").reversed()
    }
    private val queued = mutableListOf<Long>()
    private val scheduler = object : BookmarkScheduler {
        override fun send(id: Long) { queued += id }
        override fun cancelAll() { queued.clear() }
    }
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
    private val store = PreferenceDataStoreFactory.create(scope = TestScope(UnconfinedTestDispatcher())) { tmp.newFile("b.preferences_pb") }
    // the client accepts plain HTTP; only the address the reader types must be HTTPS – the test stores a fake one
    private val repo = BookmarkRepository(store, cipher, BookmarkClient(OkHttpClient()), db.savedDao(), ApiJson, scheduler).apply { allowHttp = false }
    private val saved = SavedRepository(db.savedDao(), ApiJson, Clock.systemUTC(), repo)

    private fun item(id: Long) = Item(
        id, "https://example.org/$id", "Item $id", lang = "en", publishedAt = Instant.parse("2026-11-21T10:00:00Z"), source = Source("s", "S"),
        categories = listOf(Category("sicherheit", "Security")), topic = TopicRef("gnome-49", "GNOME 49"),
    )

    private fun answer(code: Int, body: String) = web.server.enqueue(MockResponse.Builder().code(code).body(body).build())

    /** Points the stored service at the mock server (the settings page would refuse plain HTTP). */
    private suspend fun connectToMock() {
        repo.connect(BookmarkService.LINKDING, "https://placeholder.example", "tok")
        store.updateData { p ->
            p.toMutablePreferences().apply { set(androidx.datastore.preferences.core.stringPreferencesKey("bookmark_address"), web.server.url("/").toString()) }
        }
    }

    @After fun close() = db.close()

    @Test
    fun withoutServiceNothingIsQueued() = runTest {
        saved.save(item(1))
        assertTrue(queued.isEmpty())
        assertEquals(0 to 0, repo.pendingAndFailed.first())
    }

    @Test
    fun tokenIsStoredEncryptedAndNeverInTheConfig() = runTest {
        repo.connect(BookmarkService.KARAKEEP, "keep.example.org", "secret-token")
        val raw = store.data.first().asMap().values.map { it.toString() }
        assertFalse(raw.any { it.contains("secret-token") })
        val c = repo.config.first()
        assertTrue(c.active)
        assertEquals("https://keep.example.org/", c.address)
        // a blank token on “change” keeps the stored one
        repo.connect(BookmarkService.KARAKEEP, "keep2.example.org", "")
        assertTrue(repo.config.first().hasToken)
        repo.disconnect()
        assertFalse(repo.config.first().hasToken)
    }

    @Test
    fun savedArticleIsSentWithTagsAndMarkedSent() = runTest {
        connectToMock()
        repo.setTagTopic(true)
        saved.save(item(1))
        assertEquals(listOf(1L), queued)
        assertEquals(1 to 0, repo.pendingAndFailed.first())
        answer(201, """{"id":1}""")
        assertTrue(repo.send(1, lastAttempt = false))
        val body = web.server.takeRequest().body!!.utf8()
        assertEquals("""{"url":"https://example.org/1","title":"Item 1","tag_names":["planetfoss","gnome-49","security"]}""", body)
        assertEquals(0 to 0, repo.pendingAndFailed.first())
    }

    @Test
    fun serverDownIsRetriedThenFailsAndCanBeRetried() = runTest {
        connectToMock()
        saved.save(item(2))
        answer(503, "")
        assertFalse(repo.send(2, lastAttempt = false))
        answer(503, "")
        assertTrue(repo.send(2, lastAttempt = true))
        assertEquals(0 to 1, repo.pendingAndFailed.first())
        queued.clear()
        repo.retryFailed()
        assertEquals(listOf(2L), queued)
        assertEquals(1 to 0, repo.pendingAndFailed.first())
    }

    @Test
    fun wrongTokenFailsAtOnce() = runTest {
        connectToMock()
        saved.save(item(3))
        answer(401, """{"detail":"Invalid token."}""")
        assertTrue(repo.send(3, lastAttempt = false))
        assertEquals(0 to 1, repo.pendingAndFailed.first())
    }

    @Test
    fun importDoesNotSend() = runTest {
        connectToMock()
        val file = """{"format":"planetfoss-saved","version":1,"items":[${ApiJson.encodeToString(Item.serializer(), item(9))}]}"""
        assertEquals(1, saved.import(file))
        assertTrue(queued.isEmpty())
    }

    @Test
    fun homeNetworkAddressesAreLocal() {
        fun local(ip: String) = isLocalAddress(java.net.InetAddress.getByName(ip))
        assertTrue(local("192.168.0.5"))
        assertTrue(local("10.0.2.2"))
        assertTrue(local("172.20.1.1"))
        assertTrue(local("fd12:3456::1"))
        assertTrue(local("fe80::1"))
        assertFalse(local("46.225.198.172"))
        assertFalse(local("2a01:4f8::1"))
    }

    @Test
    fun plainHttpAddressIsRefused() = runTest {
        assertEquals(BookmarkResult.WrongAddress, repo.test(BookmarkService.READECK, "http://192.168.0.5:8000", "t"))
    }
}
