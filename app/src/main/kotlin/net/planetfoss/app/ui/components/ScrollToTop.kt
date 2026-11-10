// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Taps on the tab whose page is already shown (as in the Reddit app); empty on pushed pages. */
val LocalTabTaps = staticCompositionLocalOf<Flow<Unit>> { emptyFlow() }

/** Far down, the list jumps to this item first and glides only the last stretch to the top. */
private const val GlideFrom = 8

/** Back to the top of the list when the tab of this page is tapped again; the collapsed [topBar] comes back too. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScrollToTopOnTabTap(list: LazyListState, topBar: TopAppBarScrollBehavior? = null) {
    val taps = LocalTabTaps.current
    LaunchedEffect(taps, list) {
        taps.collect {
            topBar?.state?.heightOffset = 0f
            if (list.firstVisibleItemIndex > GlideFrom) list.scrollToItem(GlideFrom)
            list.animateScrollToItem(0)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScrollToTopOnTabTap(list: LazyStaggeredGridState, topBar: TopAppBarScrollBehavior? = null) {
    val taps = LocalTabTaps.current
    LaunchedEffect(taps, list) {
        taps.collect {
            topBar?.state?.heightOffset = 0f
            if (list.firstVisibleItemIndex > GlideFrom) list.scrollToItem(GlideFrom)
            list.animateScrollToItem(0)
        }
    }
}
