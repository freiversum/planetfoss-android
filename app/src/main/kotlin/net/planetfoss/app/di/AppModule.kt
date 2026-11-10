// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import net.planetfoss.app.BuildConfig
import net.planetfoss.app.data.db.AppDatabase
import net.planetfoss.app.data.db.FeedDao
import net.planetfoss.app.data.db.SavedDao
import net.planetfoss.app.data.db.SeenSupernovaDao
import net.planetfoss.core.api.ApiJson
import net.planetfoss.core.api.PlanetFossApi
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.File
import java.time.Clock
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    /**
     * One client for the API and for images (Coil), with a shared HTTP cache: ETag/304
     * and the images' own cache headers do the work. The User-Agent names the app and
     * its version only – no device model, no id. List requests say whether the app is in front (AppVisibility).
     */
    @Provides
    @Singleton
    fun okHttp(@ApplicationContext context: Context): OkHttpClient = OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, "http"), 20L * 1024 * 1024))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .callTimeout(40, TimeUnit.SECONDS)
        .addNetworkInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", "PlanetFOSS-Android/${BuildConfig.VERSION_NAME}").build())
        }
        // in front or in the background, first request of a visit: for the reach numbers, see AppVisibility
        .addNetworkInterceptor(net.planetfoss.app.AppVisibility.interceptor(BuildConfig.API_BASE_URL.toHttpUrl().host))
        .build()

    @Provides
    @Singleton
    fun json(): Json = ApiJson

    @Provides
    @Singleton
    fun api(client: OkHttpClient, json: Json): PlanetFossApi = PlanetFossApi(client, BuildConfig.API_BASE_URL.toHttpUrl(), json)

    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "planetfoss.db").build()

    @Provides
    fun feedDao(db: AppDatabase): FeedDao = db.feedDao()

    @Provides
    fun savedDao(db: AppDatabase): SavedDao = db.savedDao()

    @Provides
    fun starDao(db: AppDatabase): net.planetfoss.app.data.db.StarDao = db.starDao()

    @Provides
    fun followDao(db: AppDatabase): net.planetfoss.app.data.db.FollowDao = db.followDao()

    @Provides
    fun seenSupernovaDao(db: AppDatabase): SeenSupernovaDao = db.seenSupernovaDao()

    @Provides
    @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    @Provides
    @Singleton
    fun clock(): Clock = Clock.systemUTC()
}
