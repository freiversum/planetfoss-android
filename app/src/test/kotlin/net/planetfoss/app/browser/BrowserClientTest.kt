// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.browser

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import net.planetfoss.app.data.prefs.BrowserMode
import net.planetfoss.app.data.prefs.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BrowserClientTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private val asked = mutableListOf<String>()
    private val errors = mutableListOf<PageError?>()
    private val events = object : BrowserEvents {
        override fun onPage(pageUrl: String, isLoading: Boolean) = Unit
        override fun onProgress(percent: Int) = Unit
        override fun onTitle(pageTitle: String) = Unit
        override fun onHistory(back: Boolean, forward: Boolean) = Unit
        override fun onError(pageError: PageError?) { errors += pageError }
        override fun onAskOutside(target: String, scheme: String) { asked += scheme }
    }
    private val client = BrowserClient(events)

    @Test
    fun webLinksStayInTheWebView() {
        assertFalse(client.handle("https://linuxiac.com/a", mainFrame = true, userGesture = true))
        assertFalse(client.handle("http://example.org/", mainFrame = true, userGesture = false))
        assertTrue(asked.isEmpty() && errors.isEmpty())
    }

    @Test
    fun otherAppsOnlyAskAfterATap() {
        assertTrue(client.handle("mailto:a@example.org", mainFrame = true, userGesture = true))
        assertEquals(listOf("mailto"), asked)
        // a page redirecting by script to another app gets nothing, not even the question
        assertTrue(client.handle("market://details?id=x", mainFrame = true, userGesture = false))
        assertTrue(client.handle("tel:123", mainFrame = false, userGesture = true))
        assertEquals(listOf("mailto"), asked)
    }

    @Test
    fun blockedLinksNeverLoad() {
        assertTrue(client.handle("javascript:alert(1)", mainFrame = true, userGesture = true))
        assertTrue(client.handle("file:///etc/hosts", mainFrame = true, userGesture = false))
        assertTrue(client.handle("intent://x/#Intent;package=com.evil;end", mainFrame = true, userGesture = true))
        assertEquals(listOf(PageError.BLOCKED, PageError.BLOCKED), errors)
        assertTrue(asked.isEmpty())
    }

    @Test
    fun browserModeAndJavaScriptPersist() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher())
        val file = tmp.newFile("s.preferences_pb")
        val repo = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { file })
        assertEquals(BrowserMode.CUSTOM_TAB, repo.settings.first().browserMode)
        assertTrue(repo.settings.first().browserJavaScript)
        repo.setBrowserMode(BrowserMode.INTERNAL)
        repo.setBrowserJavaScript(false)
        assertEquals(BrowserMode.INTERNAL, repo.settings.first().browserMode)
        assertFalse(repo.settings.first().browserJavaScript)
    }
}
