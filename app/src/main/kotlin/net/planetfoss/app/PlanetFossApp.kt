// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import net.planetfoss.app.adblock.AdblockRepository
import net.planetfoss.app.adblock.AdblockScheduler
import net.planetfoss.app.data.prefs.SettingsRepository
import net.planetfoss.app.sync.AndroidNovaNotifier
import net.planetfoss.app.sync.NovaScheduler
import net.planetfoss.app.sync.FollowScheduler
import net.planetfoss.app.data.FollowRepository
import kotlinx.coroutines.flow.combine
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class PlanetFossApp : Application(), Configuration.Provider, SingletonImageLoader.Factory {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var client: OkHttpClient
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var scheduler: NovaScheduler
    @Inject lateinit var notifier: AndroidNovaNotifier
    @Inject lateinit var adblock: AdblockRepository
    @Inject lateinit var adblockScheduler: AdblockScheduler
    @Inject lateinit var follows: FollowRepository
    @Inject lateinit var followScheduler: FollowScheduler
    @Inject lateinit var logbookScheduler: net.planetfoss.app.sync.LogbookScheduler

    /** Work that must outlive a screen (the last-seen time when the app goes to the background). */
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        notifier.createChannel()
        // the periodic supernova check follows the settings: on/off and interval
        scope.launch {
            settings.settings.map { it.novaNotify to it.novaIntervalMinutes }.distinctUntilChanged()
                .collect { (on, minutes) -> scheduler.apply(on, minutes) }
        }
        // the follow check runs while anything is followed and its notifications are on, in the supernova interval
        scope.launch {
            combine(follows.all.map { it.isNotEmpty() }, settings.settings.map { it.followNotify to it.novaIntervalMinutes }) { any, (on, minutes) -> (any && on) to minutes }.distinctUntilChanged()
                .collect { (any, minutes) -> followScheduler.apply(any, minutes) }
        }
        // the morning logbook check runs while its switch is on
        scope.launch {
            settings.settings.map { it.logbookNotify }.distinctUntilChanged().collect { logbookScheduler.apply(it) }
        }
        // list updates follow the tracker switch; off = no list traffic, rules freed
        scope.launch {
            adblock.state.map { Triple(it.active, it.missingLists, it.neededLists) }
                .distinctUntilChanged().collect { (any, missing, _) ->
                    adblockScheduler.apply(any, needsFirstDownload = missing)
                    if (any) adblock.ensureLoaded() else adblock.unload()
                }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    /** Images through the app's OkHttp client (same User-Agent, same cache), never another stack. */
    override fun newImageLoader(context: PlatformContext): ImageLoader = ImageLoader.Builder(context)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { client })) }
        .crossfade(true)
        .build()
}
