// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.list

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.cash.turbine.test
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import mockwebserver3.Dispatcher
import mockwebserver3.MockResponse
import mockwebserver3.MockWebServer
import mockwebserver3.RecordedRequest
import net.planetfoss.app.data.FeedRepository
import net.planetfoss.app.data.FollowRepository
import net.planetfoss.app.data.db.AppDatabase
import net.planetfoss.app.data.prefs.SettingsRepository
import net.planetfoss.core.api.ApiJson
import net.planetfoss.core.api.PlanetFossApi
import okhttp3.OkHttpClient
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Clock
import kotlin.time.Duration.Companion.seconds

/** Latest's extras from 0.4.0: the intro hint for new users and the band of a burning supernova. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ItemListViewModelTest {
    @get:Rule val tmp = TemporaryFolder()

    private val server = MockWebServer()
    private lateinit var db: AppDatabase
    private lateinit var repo: FeedRepository
    private lateinit var settings: SettingsRepository
    private lateinit var follows: FollowRepository

    /** What /supernovas answers; everything else is an empty but valid answer. */
    private var supernovas = """{"supernovas":[]}"""

    /** What /ads answers (the candidates of both slots) and with which status; what /home answers. */
    private var adsBody = """{"slots":{}}"""
    private var adsCode = 200
    private var homeBody = """{"items":[],"has_more":false}"""

    private fun card(id: Int, slot: String, extra: String = "") =
        """{"id":$id,"slot":"$slot","lang":"de","after":4,"kind":"sponsor","label":"Anzeige","title":"Karte $id","click_url":"https://planetfoss.net/a/$id?s=$slot&l=de"$extra}"""

    private fun nova(key: String, burning: Boolean) = """{"id":"tag:planetfoss.net,2026:supernova/$key/1","key":"$key","label":"$key",
        "ignited_at":"2026-10-31T10:00:00Z","announced_at":"2026-10-31T10:30:00Z","url":"https://planetfoss.net/thema/$key/",
        "stats":{"items":5,"sources":5},"burning":$burning,"reports":[]}"""

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val body = when (request.url.encodedPath.removePrefix("/api/app/v1/")) {
                    "supernovas" -> supernovas
                    "topics" -> """{"orbit":[{"key":"gnome-52","label":"GNOME 52","status":"komet"}]}"""
                    "front" -> "{}"
                    "ads" -> adsBody
                    "home" -> homeBody
                    else -> """{"items":[],"has_more":false}"""
                }
                val code = if (request.url.encodedPath.endsWith("/ads")) adsCode else 200
                return MockResponse.Builder().code(code).body(body).build()
            }
        }
        server.start()
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        repo = FeedRepository(PlanetFossApi(OkHttpClient(), server.url("/"), ApiJson), db.feedDao(), ApiJson, Clock.systemUTC())
        settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = TestScope(UnconfinedTestDispatcher())) { tmp.newFile("s.preferences_pb") })
        val widgets = net.planetfoss.app.data.WidgetStore(ApplicationProvider.getApplicationContext(),
            PreferenceDataStoreFactory.create(scope = TestScope(UnconfinedTestDispatcher())) { tmp.newFile("w.preferences_pb") }, ApiJson, Clock.systemUTC())
        follows = FollowRepository(db.followDao(), Clock.systemUTC(), widgets)
    }

    @After
    fun tearDown() {
        db.close()
        server.close()
        Dispatchers.resetMain()
    }

    private fun latest() = ItemListViewModel(repo, settings, follows, SavedStateHandle())

    @Test
    fun burningSupernovasOnTopTheOthersInTheWeeksSky() = runTest {
        // a supernova that has gone out stays in the list for a while: not on top, but in the week's sky
        supernovas = """{"supernovas":[${nova("tor-16", burning = false)},${nova("gnome-52", burning = true)},${nova("kde-7", burning = true)}]}"""
        latest().state.test(timeout = 10.seconds) {
            var ui = awaitItem()
            while (ui.burning.isEmpty()) ui = awaitItem()
            assertEquals(listOf("gnome-52", "kde-7"), ui.burning.map { it.key })
            assertEquals(listOf("tor-16"), ui.cooled.map { it.key })
            assertEquals("komet", ui.orbit.single().status)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun nothingOnTopWhenNothingBurns() = runTest {
        supernovas = """{"supernovas":[${nova("tor-16", burning = false)}]}"""
        latest().state.test(timeout = 10.seconds) {
            // the orbit arrives in the same refresh, right before the supernovas
            var ui = awaitItem()
            while (ui.orbit.isEmpty()) ui = awaitItem()
            server.takeRequestWithPath("supernovas")
            Thread.sleep(500) // the answer is read on OkHttp's thread
            val later = cancelAndConsumeRemainingEvents().filterIsInstance<app.cash.turbine.Event.Item<ItemListUi>>().map { it.value }
            for (u in listOf(ui) + later) assertTrue(u.burning.isEmpty())
        }
    }

    @Test
    fun theSponsorCardComesFromTheCandidates() = runTest {
        adsBody = """{"ttl":300,"slots":{"app_feed":[${card(5, "app_feed")},${card(6, "app_feed", ",\"topics\":[\"kde\"]")}],"app_list":[${card(9, "app_list")}]}}"""
        // the card of the Latest slot; the topic-bound one does not count here and the list slot's card never shows on Latest
        latest().state.test(timeout = 10.seconds) {
            var ui = awaitItem()
            while (ui.ad == null) ui = awaitItem()
            assertEquals(5L, ui.ad!!.id)
            assertEquals("app_feed", ui.ad!!.slot)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun theListsOwnCardIsTheFallbackWhenTheCandidatesFail() = runTest {
        adsCode = 500
        homeBody = """{"items":[],"has_more":false,"ad":${card(7, "app_feed")}}"""
        latest().state.test(timeout = 10.seconds) {
            var ui = awaitItem()
            while (ui.ad == null) ui = awaitItem()
            assertEquals(7L, ui.ad!!.id)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun noCandidatesNoCard() = runTest {
        // ads are off: the candidates are empty, and so is the list's own card
        adsBody = """{"ttl":300,"slots":{"app_feed":[],"app_list":[]}}"""
        latest().state.test(timeout = 10.seconds) {
            var ui = awaitItem()
            server.takeRequestWithPath("ads")
            Thread.sleep(500)
            val later = cancelAndConsumeRemainingEvents().filterIsInstance<app.cash.turbine.Event.Item<ItemListUi>>().map { it.value }
            for (u in listOf(ui) + later) assertNull(u.ad)
        }
    }

    @Test
    fun theIntroStaysClosedOnceClosed() = runTest {
        val vm = latest()
        vm.introOpen.test(timeout = 10.seconds) {
            var open = awaitItem()
            if (!open) open = awaitItem() // the initial value comes before the settings
            assertTrue(open)
            vm.closeIntro()
            assertFalse(awaitItem())
        }
        // the next start: a new view model reads the stored choice
        latest().introOpen.test(timeout = 10.seconds) {
            assertFalse(awaitItem())
            expectNoEvents()
        }
    }

    @Test
    fun theIntroBelongsToLatestOnly() = runTest {
        val rubric = ItemListViewModel(repo, settings, follows, SavedStateHandle(mapOf("kind" to "rubric", "ref" to "gaming")))
        rubric.introOpen.test(timeout = 10.seconds) {
            assertFalse(awaitItem())
            expectNoEvents()
        }
    }

    /** Waits until the app asked for [path], so the answer has been handled before checking for no band. */
    private fun MockWebServer.takeRequestWithPath(path: String) {
        while (true) {
            val r = takeRequest(10, java.util.concurrent.TimeUnit.SECONDS) ?: error("no request for $path")
            if (r.url.encodedPath.endsWith("/$path")) return
        }
    }
}
