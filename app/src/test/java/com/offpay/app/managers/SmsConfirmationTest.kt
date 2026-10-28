// SPDX-License-Identifier: Apache-2.0

package com.offpay.app.managers

import android.Manifest
import android.app.Application
import com.offpay.app.BuildConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SmsConfirmationTest {
    private val app: Application = RuntimeEnvironment.getApplication()

    @Test
    fun `denied SMS access disables automatic confirmation in either edition`() {
        shadowOf(app).denyPermissions(Manifest.permission.RECEIVE_SMS)
        assertFalse(SmsConfirmation.isAvailable(app))
    }

    @Test
    fun `granted SMS access enables confirmation only if the capability was packaged`() {
        shadowOf(app).grantPermissions(Manifest.permission.RECEIVE_SMS)
        assertEquals(BuildConfig.SMS_CONFIRMATION_AVAILABLE, SmsConfirmation.isAvailable(app))
    }

    @Test
    fun `QR codes without a finite positive amount never enable SMS confirmation`() {
        shadowOf(app).grantPermissions(Manifest.permission.RECEIVE_SMS)
        val invalidAmounts = listOf(null, "", " ", "NaN", "Infinity", "-Infinity", "0", "-0.01", "invalid")
        for (amount in invalidAmounts) {
            assertFalse(
                "Invalid QR amount must remain unverified: $amount",
                SmsConfirmation.isAvailableForQr(app, amount)
            )
        }
    }

    @Test
    fun `QR amount confirmation still requires packaged and granted SMS access`() {
        shadowOf(app).denyPermissions(Manifest.permission.RECEIVE_SMS)
        assertFalse(SmsConfirmation.isAvailableForQr(app, "12.50"))
        shadowOf(app).grantPermissions(Manifest.permission.RECEIVE_SMS)
        assertEquals(BuildConfig.SMS_CONFIRMATION_AVAILABLE, SmsConfirmation.isAvailableForQr(app, "12.50"))
    }
}
