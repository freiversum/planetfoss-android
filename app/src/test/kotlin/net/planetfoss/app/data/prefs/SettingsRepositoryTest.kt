// SPDX-FileCopyrightText: 2026 Christoph Langner
// SPDX-License-Identifier: GPL-3.0-or-later

package net.planetfoss.app.data.prefs

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SettingsRepositoryTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun countingForTheSourcesIsOptIn() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher())
        val repo = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("s.preferences_pb") })
        // F-Droid's Tracking anti-feature: nothing about what the user reads leaves the phone unless switched on
        assertFalse(repo.settings.first().countReach)
        repo.setCountReach(true)
        assertTrue(repo.settings.first().countReach)
    }

    @Test
    fun setupAssistantIsDueUntilFinishedAndRetiresTheOldHint() = runTest {
        val scope = TestScope(UnconfinedTestDispatcher())
        val repo = SettingsRepository(PreferenceDataStoreFactory.create(scope = scope) { tmp.newFile("t.preferences_pb") })
        val first = repo.settings.first()
        assertTrue(first.loaded)
        assertFalse(first.setupDone)
        repo.finishSetup()
        val done = repo.settings.first()
        assertTrue(done.setupDone)
        assertTrue(done.introClosed)
    }
}
