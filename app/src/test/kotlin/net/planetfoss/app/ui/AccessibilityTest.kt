// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui

import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import net.planetfoss.app.ui.components.ItemActions
import net.planetfoss.app.ui.components.ItemCard
import net.planetfoss.app.ui.components.ListState
import net.planetfoss.app.ui.components.LocalItemActions
import net.planetfoss.app.ui.components.UiError
import net.planetfoss.app.ui.list.ItemListContent
import net.planetfoss.app.ui.theme.PlanetFossTheme
import net.planetfoss.core.api.Category
import net.planetfoss.core.api.Item
import net.planetfoss.core.api.Source
import net.planetfoss.core.api.TopicRef
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "de-w411dp-h891dp")
class AccessibilityTest {
    @get:Rule
    val compose = createComposeRule()

    private val opened = mutableListOf<String>()
    private val actions = ItemActions(
        open = { opened += it }, openFrom = { it, _ -> opened += it.url }, openOther = {}, openOtherLabel = net.planetfoss.app.R.string.open_in_browser,
        toggleSave = {}, toggleStar = {}, rubric = {}, topic = {}, source = {},
    )
    private val item = Item(
        id = 1, url = "https://linuxiac.com/x", title = "Tor Browser 16.0 veröffentlicht", excerpt = "Ein Anriss mit ein paar Worten.",
        lang = "de", publishedAt = Instant.now().minusSeconds(3600), source = Source("linuxiac", "Linuxiac"),
        categories = listOf(Category("sicherheit", "Sicherheit")), topic = TopicRef("tor-16", "Tor 16", supernova = true),
    )

    /** The touch target (not the drawn size) must be at least 48 × 48 dp. */
    private fun SemanticsNodeInteraction.assertTouchTarget(heightOnly: Boolean = false): SemanticsNodeInteraction {
        val node = fetchSemanticsNode()
        val density = node.layoutInfo.density.density
        val w = node.touchBoundsInRoot.width / density
        val h = node.touchBoundsInRoot.height / density
        assertTrue("touch target ${w}×${h} dp", h >= 47.5f && (heightOnly || w >= 47.5f))
        return this
    }

    private fun show(fontScale: Float = 1f, dark: Boolean = false, content: @androidx.compose.runtime.Composable () -> Unit) {
        compose.setContent {
            val d = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(d.density, fontScale), LocalItemActions provides actions) {
                PlanetFossTheme(dark = dark) { content() }
            }
        }
    }

    /** Expressive draws icon buttons at 40 dp; what counts for touch is the 48 dp target around them. */
    @Test
    fun cardButtonsAreLabelledAndLargeEnough() {
        show { ItemCard(item) }
        for (label in listOf("Als Sternstunde markieren", "Speichern", "Weitere Aktionen")) {
            compose.onNodeWithContentDescription(label).assertIsDisplayed().assertTouchTarget()
        }
        compose.onNodeWithText("Sicherheit", useUnmergedTree = false).assertTouchTarget(heightOnly = true) // chip: drawn smaller, touch target 48 dp
        compose.onNodeWithText("Tor Browser 16.0 veröffentlicht").assertIsDisplayed()
    }

    @Test
    fun supernovaChipSaysItIsASupernova() {
        show { ItemCard(item) }
        // the star icon alone tells TalkBack users nothing (device check 30.10.)
        compose.onNodeWithText("Tor 16").assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Supernova"))
    }

    @Test
    fun everyClickableNodeHasALabel() {
        show { ItemCard(item) }
        val clickable = compose.onAllNodes(SemanticsMatcher.keyIsDefined(SemanticsProperties.Role) or SemanticsMatcher("clickable") { it.config.contains(androidx.compose.ui.semantics.SemanticsActions.OnClick) })
            .fetchSemanticsNodes()
        assertTrue(clickable.isNotEmpty())
        for (node in clickable) {
            val c = node.config
            val text = c.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty() +
                c.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty() +
                node.children.joinToString { ch -> ch.config.getOrNull(SemanticsProperties.Text)?.joinToString { it.text }.orEmpty() + ch.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString().orEmpty() }
            assertTrue("unlabelled clickable node ${node.id}", text.isNotBlank() || node.children.isNotEmpty())
        }
    }

    @Test
    fun largeFontAndDarkModeStillShowTheTitleAndActions() {
        show(fontScale = 2f, dark = true) { ItemCard(item) }
        compose.onNodeWithText("Tor Browser 16.0 veröffentlicht").assertIsDisplayed()
        compose.onNodeWithContentDescription("Speichern").assertIsDisplayed()
        // the excerpt gives way at large font sizes
        assertEquals(0, compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Text, listOf(androidx.compose.ui.text.AnnotatedString("Ein Anriss mit ein paar Worten.")))).fetchSemanticsNodes().size)
    }

    @Test
    fun errorAndEmptyStatesExplainThemselves() {
        show {
            ItemListContent(ListState(loaded = true, fetched = false, error = UiError.Offline), rememberLazyStaggeredGridState(), onRetry = {}, emptyText = "leer")
        }
        compose.onNodeWithText("Keine Verbindung zu PlanetFOSS.").assertIsDisplayed()
        compose.onNodeWithText("Erneut versuchen").assertIsDisplayed().assertTouchTarget(heightOnly = true)
    }

    @Test
    fun notFoundOffersNoPointlessRetry() {
        show {
            ItemListContent(ListState(loaded = true, fetched = true, error = UiError.NotFound), rememberLazyStaggeredGridState(), onRetry = {}, emptyText = "leer")
        }
        compose.onNodeWithText("Auf PlanetFOSS nicht gefunden.").assertIsDisplayed()
        assertEquals(0, compose.onAllNodes(SemanticsMatcher.expectValue(SemanticsProperties.Text, listOf(androidx.compose.ui.text.AnnotatedString("Erneut versuchen")))).fetchSemanticsNodes().size)
    }
}
