// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * What the detail pane beside the list shows on wide screens (docs/11-tablet-mode.md), as a key: `topic:<key>`,
 * `source:<slug>`, `rubric:<slug>`, `bundle:<id>`, `starred:`, `videos:`, `supernovas`, `logbook`, `weekly`;
 * null while the pane shows the situation or there is no pane.
 */
val LocalPaneOpen = compositionLocalOf<String?> { null }

/**
 * Marks the row whose target is open in the detail pane: a tint over it and a bar on its start edge, drawn over the
 * content so it works on list rows and cards alike; TalkBack hears “selected”.
 */
@Composable
fun Modifier.openBeside(key: String): Modifier {
    if (LocalPaneOpen.current != key) return this
    val tint = MaterialTheme.colorScheme.primary
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    return semantics { selected = true }.drawWithContent {
        drawContent()
        drawRect(tint.copy(alpha = 0.10f))
        val bar = 4.dp.toPx()
        drawRect(tint, topLeft = Offset(if (rtl) size.width - bar else 0f, 0f), size = Size(bar, size.height))
    }
}
