// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import net.planetfoss.app.ui.components.OrbitRow
import net.planetfoss.app.ui.theme.PlanetFossTheme
import net.planetfoss.core.api.OrbitTopic
import net.planetfoss.core.api.TopicRef
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** “Im Orbit” on Latest: comets and supernovas as on the website's trend bar. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-w411dp-h891dp")
class OrbitTest {
    @get:Rule
    val compose = createComposeRule()

    private val opened = mutableListOf<TopicRef>()

    private fun show(vararg topics: OrbitTopic) {
        compose.setContent { PlanetFossTheme { OrbitRow(topics.toList(), onTopic = { opened += it }, onAll = {}) } }
    }

    private fun state(word: String) = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, word)

    @Test
    fun aCometIsMarkedAndOpensItsTopic() {
        show(OrbitTopic("gnome-52", "GNOME 52", status = "komet"), OrbitTopic("rust", "Rust"))
        compose.onNodeWithText("IM ORBIT").assertIsDisplayed()
        // TalkBack hears the word, not just the ☄
        compose.onNodeWithText("☄ GNOME 52").assert(state("Komet")).performClick()
        assertEquals(listOf(TopicRef("gnome-52", "GNOME 52", false)), opened)
        compose.onNodeWithText("Rust").assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.StateDescription))
    }

    @Test
    fun aSupernovaOutranksTheCometStatus() {
        // a topic that ignited keeps “komet” for a while; the chip shows only the stronger star
        show(OrbitTopic("kde-7", "KDE 7", supernova = true, status = "komet"))
        compose.onNodeWithText("KDE 7").assert(state("Supernova")).performClick()
        assertEquals(listOf(TopicRef("kde-7", "KDE 7", true)), opened)
    }
}
