// SPDX-License-Identifier: Apache-2.0
package com.offpay.app.data

import android.content.Context
import androidx.core.content.edit
import com.offpay.app.ui.theme.isDarkMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AppearanceSettingsTest {
    private val context: Context = RuntimeEnvironment.getApplication()

    @Before
    fun reset() {
        context.getSharedPreferences("offpay_settings", Context.MODE_PRIVATE).edit { clear() }
    }

    @Test
    fun `existing installs keep dark mode until the user changes it`() {
        assertTrue(SettingsRepository(context).settingsFlow.value.darkModeEnabled)
        assertTrue(isDarkMode(context))
    }

    @Test
    fun `light mode updates observable state and survives a new repository instance`() {
        val repository = SettingsRepository(context)
        repository.saveSettings(
            repository.settingsFlow.value.copy(darkModeEnabled = false, bankId = "sbi", setupCompleted = true)
        )
        assertFalse(repository.settingsFlow.value.darkModeEnabled)
        val restored = SettingsRepository(context).settingsFlow.value
        assertFalse(restored.darkModeEnabled)
        assertEquals("sbi", restored.bankId)
        assertTrue(restored.setupCompleted)
        assertFalse(isDarkMode(context))
    }

    @Test
    fun `changing another setting preserves appearance and reset restores the default`() {
        val repository = SettingsRepository(context)
        repository.saveSettings(repository.settingsFlow.value.copy(darkModeEnabled = false))
        repository.saveSettings(repository.settingsFlow.value.copy(bankId = "icici"))
        assertFalse(SettingsRepository(context).settingsFlow.value.darkModeEnabled)
        repository.clearAllData()
        assertTrue(SettingsRepository(context).settingsFlow.value.darkModeEnabled)
    }
}
