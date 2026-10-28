// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Soumyadip Pal

package com.offpay.app.managers

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat

/** Uses the system dialer when direct calls are unavailable; never grants or bypasses permissions. */
class PaymentDialer(
    private val context: Context,
    private val startActivity: (Intent) -> Unit = { context.startActivity(it) }
) {
    enum class Result {
        DIRECT_CALL,
        DIALER_OPENED,
        UNAVAILABLE
    }

    /** A successful handoff does not mean the call connected or that a payment was made. */
    fun launch(uri: Uri): Result {
        if (uri.scheme != "tel" || uri.schemeSpecificPart.isNullOrBlank()) return Result.UNAVAILABLE
        val directCallsAllowed = ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED
        return when {
            directCallsAllowed && tryLaunch(Intent.ACTION_CALL, uri) -> Result.DIRECT_CALL
            tryLaunch(Intent.ACTION_DIAL, uri) -> Result.DIALER_OPENED
            else -> Result.UNAVAILABLE
        }
    }

    private fun tryLaunch(action: String, uri: Uri): Boolean = try {
        startActivity(Intent(action, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (_: SecurityException) {
        false
    } catch (_: ActivityNotFoundException) {
        false
    }
}
