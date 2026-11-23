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
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions

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

/**
 * The handle between the list and the detail pane (docs/11-tablet-mode.md, T4): drag it to make the list wider or
 * narrower; the mouse pointer turns into a resize arrow over it. TalkBack offers “wider” and “narrower” as actions.
 */
@Composable
fun PaneHandle(width: androidx.compose.ui.unit.Dp, onWidth: (androidx.compose.ui.unit.Dp) -> Unit) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val current by androidx.compose.runtime.rememberUpdatedState(width)
    val drag = androidx.compose.foundation.gestures.rememberDraggableState { px ->
        val dp = with(density) { px.toDp() }
        onWidth(if (rtl) current - dp else current + dp)
    }
    var dragging by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val label = androidx.compose.ui.res.stringResource(net.planetfoss.app.R.string.pane_handle)
    val wider = androidx.compose.ui.res.stringResource(net.planetfoss.app.R.string.pane_wider)
    val narrower = androidx.compose.ui.res.stringResource(net.planetfoss.app.R.string.pane_narrower)
    androidx.compose.foundation.layout.Box(
        Modifier.width(16.dp).fillMaxHeight()
            .draggable(drag, androidx.compose.foundation.gestures.Orientation.Horizontal, onDragStarted = { dragging = true }, onDragStopped = { dragging = false })
            .pointerHoverIcon(androidx.compose.ui.input.pointer.PointerIcon(android.view.PointerIcon.TYPE_HORIZONTAL_DOUBLE_ARROW))
            .semantics {
                contentDescription = label
                customActions = listOf(
                    androidx.compose.ui.semantics.CustomAccessibilityAction(wider) { onWidth(current + 48.dp); true },
                    androidx.compose.ui.semantics.CustomAccessibilityAction(narrower) { onWidth(current - 48.dp); true },
                )
            },
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.width(4.dp).height(if (dragging) 64.dp else 48.dp)
                .background(if (dragging) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, androidx.compose.foundation.shape.CircleShape),
        )
    }
}
