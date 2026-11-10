// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import net.planetfoss.app.ui.components.AdCard
import net.planetfoss.app.ui.theme.PlanetFossTheme
import net.planetfoss.core.api.Ad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The sponsor card in both formats and its “Why am I seeing this ad?” sheet (German UI). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-w411dp-h891dp")
class AdCardTest {
    @get:Rule
    val compose = createComposeRule()

    private fun ad(format: String, by: String = "Musterhost (Demo)") = Ad(
        id = 9, slot = if (format == "compact") "app_list" else "app_feed", lang = "de", after = 4, format = format, label = "Anzeige", by = by,
        title = "Managed Linux-Hosting", body = "NVMe und Backups.", cta = "Zum Angebot", clickUrl = "https://planetfoss.net/a/9?s=app_feed&l=de",
    )

    @Test
    fun standardCardShowsLabelTitleTextAndButton() {
        compose.setContent { PlanetFossTheme { AdCard(ad("standard"), onSeen = {}) } }
        compose.onNodeWithText("ANZEIGE").assertIsDisplayed()
        compose.onNodeWithText("Musterhost (Demo)").assertIsDisplayed()
        compose.onNodeWithText("Managed Linux-Hosting").assertIsDisplayed()
        compose.onNodeWithText("NVMe und Backups.").assertIsDisplayed()
        compose.onNodeWithText("Zum Angebot →").assertIsDisplayed()
    }

    @Test
    fun compactCardShowsTitleOneLineAndButton() {
        compose.setContent { PlanetFossTheme { AdCard(ad("compact"), onSeen = {}) } }
        compose.onNodeWithText("ANZEIGE").assertIsDisplayed()
        compose.onNodeWithText("Managed Linux-Hosting").assertIsDisplayed()
        compose.onNodeWithText("Zum Angebot").assertIsDisplayed()
    }

    @Test
    fun theInfoSheetNamesWhoPays() {
        compose.setContent { PlanetFossTheme { AdCard(ad("standard"), onSeen = {}) } }
        compose.onNodeWithContentDescription("Über diese Anzeige").performClick()
        compose.onNodeWithText("Warum sehe ich diese Anzeige?").assertIsDisplayed()
        compose.onNodeWithText("Bezahlt von Musterhost (Demo). So finanziert sich PlanetFOSS – ohne Werbenetzwerk.").assertIsDisplayed()
        compose.onNodeWithText("Anzeige melden").assertIsDisplayed()
    }

    @Test
    fun theInfoButtonHasATouchTargetOf48dp() {
        compose.setContent { PlanetFossTheme { AdCard(ad("compact"), onSeen = {}) } }
        val node = compose.onNodeWithContentDescription("Über diese Anzeige").fetchSemanticsNode()
        val density = node.layoutInfo.density.density
        val w = node.touchBoundsInRoot.width / density
        val h = node.touchBoundsInRoot.height / density
        assertTrue("touch target ${w}×$h dp", w >= 47.5f && h >= 47.5f)
    }

    @Test
    fun theSeenCallbackFiresOnceWhenTheCardIsOnScreen() {
        val seen = mutableListOf<Long>()
        compose.setContent { PlanetFossTheme { AdCard(ad("standard"), onSeen = { seen += it.id }) } }
        compose.waitForIdle()
        assertEquals(listOf(9L), seen)
    }

    /** What TalkBack reaches: the card as one stop that says “Anzeige: title” and can be activated, and the (i) as its own stop. */
    @Test
    fun theCardIsOneAnnouncedStopAndTheInfoButtonItsOwn() {
        compose.setContent { PlanetFossTheme { AdCard(ad("standard"), onSeen = {}) } }
        val tree = compose.onRoot(useUnmergedTree = false).printToString()
        // the card: description with label and title, and it opens the ad
        compose.onNodeWithContentDescription("Anzeige: Managed Linux-Hosting").assertHasClickAction()
        // the info button: its own node with its own description, not swallowed by the card
        compose.onNodeWithContentDescription("Über diese Anzeige").assertHasClickAction()
        // exactly two stops with a click action: the card and the (i)
        val clickable = compose.onAllNodes(hasClickAction()).fetchSemanticsNodes().size
        assertEquals("tree:\n$tree", 2, clickable)
    }
}
