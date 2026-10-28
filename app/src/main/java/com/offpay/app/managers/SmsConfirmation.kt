// SPDX-License-Identifier: Apache-2.0

package com.offpay.app.managers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.offpay.app.BuildConfig

/** SMS confirmation is an optional capability, never a requirement for payment handoff. */
object SmsConfirmation {
    fun isAvailable(context: Context): Boolean = BuildConfig.SMS_CONFIRMATION_AVAILABLE &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == PackageManager.PERMISSION_GRANTED

    /** Without a QR amount, a later debit cannot be correlated to this payment attempt. */
    fun isAvailableForQr(context: Context, amount: String?): Boolean {
        val encodedAmount = amount?.toDoubleOrNull()
        return encodedAmount != null && encodedAmount.isFinite() && encodedAmount > 0 && isAvailable(context)
    }
}
