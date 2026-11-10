// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.planetfoss.app.sync.AndroidNovaNotifier
import net.planetfoss.app.sync.NovaNotifier

@Module
@InstallIn(SingletonComponent::class)
abstract class SyncModule {
    @Binds
    abstract fun notifier(impl: AndroidNovaNotifier): NovaNotifier
}
