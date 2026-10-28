// SPDX-License-Identifier: Apache-2.0

package com.offpay.app.receivers

import android.content.Context
import android.content.Intent

/** Compatibility builds have no SMS receiver or notification capability. */
object PaymentResultNotifier {
    @Suppress("UNUSED_PARAMETER") // Same internal API; this edition intentionally cannot post notifications.
    fun notifyResult(context: Context, resultIntent: Intent) = Unit
}
