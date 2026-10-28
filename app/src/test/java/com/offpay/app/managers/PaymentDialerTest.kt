// SPDX-License-Identifier: Apache-2.0

package com.offpay.app.managers

import android.Manifest
import android.app.Application
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PaymentDialerTest {
    private val app: Application = RuntimeEnvironment.getApplication()

    @Before
    fun denyPhonePermissions() {
        shadowOf(app).denyPermissions(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE)
    }

    @Test
    fun `missing call permission opens the dialer and preserves USSD encoding`() {
        val intents = mutableListOf<Intent>()
        val uri = Uri.fromParts("tel", "*99*1*3#", null)
        val result = PaymentDialer(app) { intents += it }.launch(uri)

        assertEquals(PaymentDialer.Result.DIALER_OPENED, result)
        assertEquals(listOf(Intent.ACTION_DIAL), intents.map { it.action })
        assertEquals(uri, intents.single().data)
        assertEquals("*99*1*3#", intents.single().data?.schemeSpecificPart)
    }

    @Test
    fun `direct calling needs no phone state permission`() {
        shadowOf(app).grantPermissions(Manifest.permission.CALL_PHONE)
        val intents = mutableListOf<Intent>()
        val result = PaymentDialer(app) { intents += it }.launch(Uri.parse("tel:08045163666"))

        assertEquals(PaymentDialer.Result.DIRECT_CALL, result)
        assertEquals(listOf(Intent.ACTION_CALL), intents.map { it.action })
    }

    @Test
    fun `OEM refusal falls back to dialer without changing payment digits`() {
        shadowOf(app).grantPermissions(Manifest.permission.CALL_PHONE)
        val intents = mutableListOf<Intent>()
        val uri = Uri.parse("tel:08045163666,,1,9876543210,,123,,1")
        val result = PaymentDialer(app) {
            intents += it
            if (it.action == Intent.ACTION_CALL) throw SecurityException("Direct calls blocked")
        }.launch(uri)

        assertEquals(PaymentDialer.Result.DIALER_OPENED, result)
        assertEquals(listOf(Intent.ACTION_CALL, Intent.ACTION_DIAL), intents.map { it.action })
        assertTrue(intents.all { it.data == uri })
    }

    @Test
    fun `missing direct call handler falls back to dialer`() {
        shadowOf(app).grantPermissions(Manifest.permission.CALL_PHONE)
        val intents = mutableListOf<Intent>()
        val result = PaymentDialer(app) {
            intents += it
            if (it.action == Intent.ACTION_CALL) throw ActivityNotFoundException()
        }.launch(Uri.parse("tel:08045163666"))

        assertEquals(PaymentDialer.Result.DIALER_OPENED, result)
        assertEquals(2, intents.size)
    }

    @Test
    fun `blocked or unavailable phone app reports unavailable without a success result`() {
        val result = PaymentDialer(app) { throw SecurityException("Dialer blocked") }
            .launch(Uri.parse("tel:08045163666"))
        assertEquals(PaymentDialer.Result.UNAVAILABLE, result)
    }

    @Test
    fun `non phone URIs are never launched`() {
        var launches = 0
        val result = PaymentDialer(app) { launches++ }.launch(Uri.parse("https://example.com"))
        assertEquals(PaymentDialer.Result.UNAVAILABLE, result)
        assertEquals(0, launches)
    }
}
