// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.freiversum

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.assertCountEquals
import net.planetfoss.app.ui.theme.PlanetFossTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The freiversum card is one unit for TalkBack: title and text, the link as its action, nothing of the sky. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-w411dp-h891dp")
class FreiversumCardTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun oneUnitWithLinkAction() {
        compose.setContent { PlanetFossTheme { FreiversumCard(UfoController(still = true)) } }
        compose.onNode(hasContentDescription("Ein Stern im freiversum. PlanetFOSS ist einer von vier Sternen", substring = true))
            .assertHasClickAction()
        compose.onAllNodesWithText("FV 2 · planetfoss.net").assertCountEquals(0)
        compose.onAllNodesWithText("freiversum.de ansehen").assertCountEquals(0)
    }
}
