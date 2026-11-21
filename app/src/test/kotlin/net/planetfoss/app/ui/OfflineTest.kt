// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsDisplayed
import net.planetfoss.app.NetworkWatch
import net.planetfoss.app.ui.components.ErrorBanner
import net.planetfoss.app.ui.components.UiError
import net.planetfoss.app.ui.components.errorText
import net.planetfoss.app.ui.theme.PlanetFossTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class OfflineTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun aBannerRetriesByItselfWhenTheConnectionIsBack() {
        var retries = 0
        NetworkWatch.lost()
        compose.setContent { PlanetFossTheme { ErrorBanner("offline") { retries++ } } }
        compose.waitForIdle()
        assertEquals("showing the banner is no retry", 0, retries)
        NetworkWatch.online()
        compose.waitForIdle()
        assertEquals(1, retries)
        // online again without having been offline: nothing to do
        NetworkWatch.online()
        compose.waitForIdle()
        assertEquals(1, retries)
    }

    @Test
    fun theHintSaysHowOldTheDataIs() {
        val todayAt1405 = LocalDate.now().atTime(14, 5).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        compose.setContent { PlanetFossTheme { androidx.compose.material3.Text(errorText(UiError.Offline, hasData = true, since = todayAt1405)) } }
        compose.onNodeWithText("No connection to PlanetFOSS. Showing what was loaded at 14:05.").assertIsDisplayed()
    }
}
