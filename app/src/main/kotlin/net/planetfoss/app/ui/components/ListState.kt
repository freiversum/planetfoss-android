// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.ui.components

import net.planetfoss.core.api.ApiException

/** What went wrong, as the UI explains it. */
sealed interface UiError {
    data object Offline : UiError
    data class Server(val code: Int) : UiError
    data object Malformed : UiError
    data object NotFound : UiError

    companion object {
        fun from(e: Throwable): UiError = when (e) {
            is ApiException.Http -> if (e.code == 404) NotFound else Server(e.code)
            is IllegalArgumentException -> NotFound
            is ApiException.Malformed -> Malformed
            else -> Offline
        }
    }
}

/** State of a list screen: cached items are shown right away, refresh and errors on top. */
data class ListState<T>(
    val items: List<T> = emptyList(),
    /** False until the cache answered once – avoids flashing the empty state. */
    val loaded: Boolean = false,
    /** The list was fetched at least once (else an empty list means “not yet”, not “nothing there”). */
    val fetched: Boolean = false,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val hasMore: Boolean = false,
    val error: UiError? = null,
)
