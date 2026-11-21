// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.planetfoss.app.R
import net.planetfoss.app.ui.theme.Space
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.painter.Painter

/** [since]: when the data on screen was loaded (epoch ms), for “no connection, as of …”; null when unknown. */
@Composable
fun errorText(error: UiError, hasData: Boolean, since: Long? = null): String = when (error) {
    UiError.Offline -> when {
        !hasData -> stringResource(R.string.error_offline_empty)
        since != null -> stringResource(R.string.error_offline_since, loadedAt(since))
        else -> stringResource(R.string.error_offline)
    }
    is UiError.Server -> stringResource(R.string.error_server, error.code)
    UiError.Malformed -> stringResource(R.string.error_malformed)
    UiError.NotFound -> stringResource(R.string.error_not_found)
}

/** “14:05” today, else “20 Nov, 14:05” – in the app's language. */
@Composable
private fun loadedAt(millis: Long): String {
    val locale = androidx.compose.ui.platform.LocalConfiguration.current.locales[0]
    val time = java.time.Instant.ofEpochMilli(millis).atZone(java.time.ZoneId.systemDefault())
    val today = time.toLocalDate() == java.time.LocalDate.now()
    val pattern = android.text.format.DateFormat.getBestDateTimePattern(locale, if (today) "Hm" else "dMMMHm")
    return java.time.format.DateTimeFormatter.ofPattern(pattern, locale).format(time)
}

/**
 * While an error with “Try again” is on screen: try again by itself as soon as the phone is online again
 * (NetworkWatch). The first value is the current state, not a change – it does not retry.
 */
@Composable
fun RetryWhenOnline(onRetry: () -> Unit) {
    val back by net.planetfoss.app.NetworkWatch.backOnline.collectAsState()
    val start = androidx.compose.runtime.remember { back }
    val retry by androidx.compose.runtime.rememberUpdatedState(onRetry)
    androidx.compose.runtime.LaunchedEffect(back) { if (back != start) retry() }
}

/** Full-screen message: loading, empty, or error without cached data. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CenterMessage(
    text: String?,
    loading: Boolean = false,
    onRetry: (() -> Unit)? = null,
    action: Pair<String, () -> Unit>? = null,
    /** Empty states: a large, quiet symbol of what would be here. */
    icon: Painter? = null,
) {
    if (onRetry != null) RetryWhenOnline(onRetry)
    Box(Modifier.fillMaxSize().padding(Space.xl), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.l)) {
            if (loading) LoadingIndicator()
            if (icon != null) Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
            if (text != null) {
                Text(
                    text,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.widthIn(max = 480.dp).semantics { liveRegion = LiveRegionMode.Polite },
                )
            }
            if (onRetry != null) Button(onClick = onRetry, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.retry)) }
            if (action != null) Button(onClick = action.second, shapes = ButtonDefaults.shapes()) { Text(action.first) }
        }
    }
}

/** Error over cached data: a slim banner at the top of the list. */
@Composable
fun ErrorBanner(text: String, onRetry: () -> Unit) {
    RetryWhenOnline(onRetry)
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = Space.l, end = Space.s, top = Space.xs, bottom = Space.xs), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f).semantics { liveRegion = LiveRegionMode.Polite },
            )
            TextButton(onClick = onRetry) { Text(stringResource(R.string.retry)) }
        }
    }
}
