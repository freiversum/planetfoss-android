// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/** Pull-to-refresh with the planet spinner from the logo; one look on every list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RefreshBox(isRefreshing: Boolean, onRefresh: () -> Unit, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val state = rememberPullToRefreshState()
    // A refresh is often done in a blink (cached list, fast API): the spinner stays for a moment so the pull is answered.
    var shown by remember { mutableStateOf(false) }
    var since by remember { mutableLongStateOf(0L) }
    fun start() { since = SystemClock.elapsedRealtime(); shown = true }
    LaunchedEffect(isRefreshing) { if (isRefreshing) start() }
    LaunchedEffect(shown, isRefreshing) {
        if (shown && !isRefreshing) {
            delay((MIN_SHOWN_MS - (SystemClock.elapsedRealtime() - since)).coerceAtLeast(0))
            shown = false
        }
    }
    val refreshing = isRefreshing || shown
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { start(); onRefresh() },
        state = state,
        modifier = modifier,
        indicator = {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .graphicsLayer {
                        val f = if (refreshing) 1f else state.distanceFraction.coerceAtLeast(0f)
                        translationY = f * 80.dp.toPx() - size.height
                        alpha = f.coerceAtMost(1f)
                    },
            ) {
                PlanetSpinner(
                    progress = state.distanceFraction,
                    refreshing = refreshing,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        },
    ) { Box(content = content) }
}

private const val MIN_SHOWN_MS = 1200L
