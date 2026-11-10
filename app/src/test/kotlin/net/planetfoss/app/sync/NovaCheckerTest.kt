// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.sync

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import net.planetfoss.app.data.FeedRepository
import net.planetfoss.app.data.db.AppDatabase
import net.planetfoss.app.data.prefs.SettingsRepository
import net.planetfoss.core.api.ApiException
import net.planetfoss.core.api.ApiJson
import net.planetfoss.core.api.PlanetFossApi
import net.planetfoss.core.api.Supernova
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NovaCheckerTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var db: AppDatabase
    private lateinit var settings: SettingsRepository
    private lateinit var checker: NovaChecker
    private val shown = mutableListOf<String>()
    private var allowed = true
    private val now = Instant.parse("2026-10-29T18:00:00Z")
    private val clock = Clock.fixed(now, ZoneOffset.UTC)
    private val scope = TestScope(UnconfinedTestDispatcher())

    private val notifier = object : NovaNotifier {
        override fun canNotify() = allowed
        override fun notify(novas: List<Supernova>) {
            shown += novas.map { it.key }
        }
    }

    @Before
    fun setUp() {
        server.start()
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("s.preferences_pb") })
        val feed = FeedRepository(PlanetFossApi(OkHttpClient(), server.url("/"), ApiJson), db.feedDao(), ApiJson, clock)
        checker = NovaChecker(feed, db, settings, notifier, clock)
    }

    @After
    fun tearDown() {
        db.close()
        server.close()
    }

    private fun nova(key: String, minutesAgo: Long, ignition: Long = 1_793_000_000) =
        """{"id":"tag:planetfoss.net,2026:supernova/$key/$ignition","key":"$key","label":"$key",""" +
            """"ignited_at":"${now.minusSeconds(minutesAgo * 60 + 1800)}","announced_at":"${now.minusSeconds(minutesAgo * 60)}",""" +
            """"url":"https://planetfoss.net/thema/$key/","burning":true}"""

    private fun answer(vararg novas: String) =
        server.enqueue(MockResponse.Builder().body("""{"supernovas":[${novas.joinToString(",")}]}""").build())

    @Test
    fun offDoesNotEvenAsk() = runTest {
        assertEquals(NovaChecker.Outcome.OFF, checker.check().first)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun firstRunAfterTurningOnIsSilentThenOnlyNewOnesNotify() = runTest {
        settings.setNovaNotify(true)
        answer(nova("kernel-7-3", 60), nova("gnome-52", 20))
        assertEquals(NovaChecker.Outcome.BASELINE, checker.check().first)
        assertEquals(emptyList<String>(), shown)

        answer(nova("kernel-7-3", 75), nova("gnome-52", 35), nova("firefox-160", 5))
        assertEquals(NovaChecker.Outcome.CHECKED, checker.check().first)
        assertEquals(listOf("firefox-160"), shown)

        // the same answer again: nothing new
        answer(nova("kernel-7-3", 90), nova("gnome-52", 50), nova("firefox-160", 20))
        checker.check()
        answer(nova("kernel-7-3", 90), nova("gnome-52", 50), nova("firefox-160", 20))
        checker.check()
        assertEquals(listOf("firefox-160"), shown)
    }

    @Test
    fun turningOffAndOnStartsANewSilentBaseline() = runTest {
        settings.setNovaNotify(true)
        answer()
        checker.check()
        settings.setNovaNotify(false)
        settings.setNovaNotify(true)
        answer(nova("gnome-52", 10))
        assertEquals(NovaChecker.Outcome.BASELINE, checker.check().first)
        assertEquals(emptyList<String>(), shown)
    }

    @Test
    fun blockedByAndroidMarksSeenWithoutRepeatingLater() = runTest {
        settings.setNovaNotify(true)
        answer()
        checker.check()
        allowed = false
        answer(nova("gnome-52", 10))
        checker.check()
        allowed = true
        answer(nova("gnome-52", 25))
        checker.check()
        assertEquals(emptyList<String>(), shown)
    }

    @Test
    fun networkErrorStoresNothing() = runTest {
        settings.setNovaNotify(true)
        answer()
        checker.check()
        server.enqueue(MockResponse.Builder().code(503).build())
        try {
            checker.check()
            fail()
        } catch (e: ApiException.Http) {
            assertEquals(503, e.code)
        }
        answer(nova("gnome-52", 10))
        checker.check()
        assertEquals(listOf("gnome-52"), shown)
    }

    @Test
    fun checkAlsoRefreshesTheSupernovaTab() = runTest {
        settings.setNovaNotify(true)
        answer(nova("gnome-52", 10))
        checker.check()
        assertTrue(db.seenSupernovaDao().ids().single().contains("gnome-52"))
    }
}
