// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Notices when the phone is online again after it was not. Screens that show “no connection” retry by themselves then
 * (ErrorBanner, CenterMessage) instead of waiting for a tap on “Try again”. Only Android's own network state is
 * watched; nothing is sent for it.
 */
object NetworkWatch {
    private val _backOnline = MutableStateFlow(0)

    /** Counts the moments the phone got a working connection back; a change means “try again now”. */
    val backOnline: StateFlow<Int> = _backOnline.asStateFlow()

    /** Whether the last known state was “no working connection” (a validated default network missing). */
    @Volatile private var offline = false

    fun start(context: Context) {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        offline = cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) != true
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) online() else offline = true
            }

            override fun onLost(network: Network) {
                offline = true
            }
        })
    }

    /** Also called by tests. */
    internal fun online() {
        if (offline) {
            offline = false
            _backOnline.value++
        }
    }

    internal fun lost() {
        offline = true
    }
}
