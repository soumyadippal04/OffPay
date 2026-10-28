// SPDX-License-Identifier: Apache-2.0

package com.offpay.app.managers

import android.Manifest
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import androidx.core.content.edit
import com.offpay.app.helpers.TestConfigurationHelper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.lang.reflect.Proxy

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TestConfigurationHandoffTest {
    private val app: Application = RuntimeEnvironment.getApplication()
    private val callbacks = Proxy.newProxyInstance(
        TestConfigurationHelper.UICallback::class.java.classLoader,
        arrayOf(TestConfigurationHelper.UICallback::class.java)
    ) { _, _, _ -> null } as TestConfigurationHelper.UICallback

    @Before
    fun reset() {
        shadowOf(app).denyPermissions(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE)
        app.getSharedPreferences("offpay_test_results", Context.MODE_PRIVATE).edit { clear() }
    }

    @Test
    fun `UPI setup offers manual confirmation without marking the handoff as verified`() {
        val helper = TestConfigurationHelper(app, callbacks)
        helper.initialize()
        try {
            helper.initiateUpi123Test()
            assertTrue(helper.getTestStates().showUpi123ConfigurationOptions)
            assertFalse(helper.getTestStates().upi123TestCompleted)
            assertFalse(helper.getTestStates().upi123Testing)
            helper.handleUpi123ConfigurationConfirmation(true)
            assertTrue(helper.getTestStates().upi123TestCompleted)
        } finally {
            helper.cleanup()
        }
    }

    @Test
    fun `USSD setup remains confirmable when phone state access is unavailable`() {
        shadowOf(app).grantPermissions(Manifest.permission.CALL_PHONE)
        val helper = TestConfigurationHelper(app, callbacks)
        helper.initialize()
        try {
            helper.initiateCall(CallType.USSD)
            assertTrue(helper.getTestStates().showUssdConfigurationOptions)
            assertFalse(helper.getTestStates().ussdTestCompleted)
            helper.handleUssdConfigurationConfirmation(false)
            assertFalse(helper.getTestStates().ussdTestCompleted)
            assertFalse(helper.getTestStates().ussdTesting)
        } finally {
            helper.cleanup()
        }
    }

    @Test
    fun `unavailable dialer resets setup instead of waiting or claiming verification`() {
        val unavailablePhone = object : ContextWrapper(app) {
            override fun startActivity(intent: Intent) {
                throw ActivityNotFoundException()
            }
        }
        val helper = TestConfigurationHelper(unavailablePhone, callbacks)
        helper.initialize()
        try {
            helper.initiateUpi123Test()
            assertFalse(helper.getTestStates().upi123Testing)
            assertFalse(helper.getTestStates().upi123TestCompleted)
            assertFalse(helper.getTestStates().showUpi123ConfigurationOptions)
        } finally {
            helper.cleanup()
        }
    }
}
