// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.offpay.app.data

/**
 * Data class representing payment transaction details
 * Used for storing and managing payment information
 */
data class PaymentDetails(
    val id: String,
    val recipientName: String? = null,
    val phoneNumber: String,
    /** Null when the bank/dialer flow has not supplied an amount. */
    val amount: Double?,
    val timestamp: Long,
    val status: PaymentStatus
)
