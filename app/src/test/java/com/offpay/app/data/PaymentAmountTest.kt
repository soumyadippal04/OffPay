// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Soumyadip Pal

package com.offpay.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentAmountTest {
    companion object {
        private const val KNOWN_AMOUNT = 1250.5
    }

    private fun details(amount: String): PaymentDetails = Transaction(
        transactionId = "qr-attempt",
        amount = amount,
        status = TransactionStatus.UNVERIFIED,
        bankName = "",
        smsExcerpt = "",
        source = TransactionSource.QR,
        upiId = "shop@bank"
    ).toPaymentDetails()

    @Test
    fun `QR amount entered only in bank menu stays unknown in OffPay`() {
        val payment = details("")

        assertNull(payment.amount)
        assertEquals(PaymentStatus.UNVERIFIED, payment.status)
    }

    @Test
    fun `invalid and non-finite amounts never become a fabricated zero`() {
        listOf(" ", "unknown", "NaN", "Infinity", "1e9999").forEach { amount ->
            assertNull("Unknown amount: $amount", details(amount).amount)
        }
    }

    @Test
    fun `known numeric amounts remain available for rupee formatting`() {
        assertEquals(KNOWN_AMOUNT, details("1250.50").amount!!, 0.0)
    }

    @Test
    fun `an explicitly recorded zero remains distinct from an unknown amount`() {
        assertEquals(0.0, details("0").amount!!, 0.0)
        assertNull(details("").amount)
    }
}
