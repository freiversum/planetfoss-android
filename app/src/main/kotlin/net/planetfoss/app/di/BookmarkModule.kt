// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import net.planetfoss.app.data.SaveHook
import net.planetfoss.app.data.bookmarks.BookmarkRepository
import net.planetfoss.app.data.bookmarks.BookmarkScheduler
import net.planetfoss.app.data.bookmarks.KeystoreTokenCipher
import net.planetfoss.app.data.bookmarks.TokenCipher
import net.planetfoss.app.sync.WorkBookmarkScheduler
import net.planetfoss.core.bookmarks.BookmarkClient
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BookmarkModule {
    @Binds
    abstract fun cipher(impl: KeystoreTokenCipher): TokenCipher

    @Binds
    abstract fun scheduler(impl: WorkBookmarkScheduler): BookmarkScheduler

    @Binds
    abstract fun saveHook(impl: BookmarkRepository): SaveHook

    companion object {
        /**
         * The app's client without its HTTP cache: nothing of the reader's service is kept on the phone. The User-Agent
         * stays (app and version only); the reach header is only ever added for planetfoss.net.
         */
        @Provides
        @Singleton
        fun bookmarkClient(client: OkHttpClient): BookmarkClient = BookmarkClient(client.newBuilder().cache(null).build())
    }
}
