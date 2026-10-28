// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.offpay.app.receivers

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat
import com.offpay.app.BuildConfig
import com.offpay.app.R
import com.offpay.app.data.TransactionStatus
import com.offpay.app.utils.CurrencyFormat

/**
 * Offers a user-initiated result screen without covering the bank's dialer.
 * Notifications are optional; confirmed history remains available when they are declined.
 * The compatibility edition declares neither SMS nor notification access.
 */
object PaymentResultNotifier {

    private const val TAG = "PaymentResultNotifier"
    private const val CHANNEL_ID = "payment_results"

    fun notifyResult(context: Context, resultIntent: Intent) {
        if (!BuildConfig.SMS_CONFIRMATION_AVAILABLE) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            val manager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    context.getString(R.string.notif_channel_payment_results),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.notif_channel_payment_results_desc)
                }
            )

            val status = resultIntent.getStringExtra("status") ?: ""
            val amount = resultIntent.getStringExtra("amount") ?: ""
            val bank = resultIntent.getStringExtra("bank_name") ?: ""
            val title = context.getString(statusTitleRes(status))
            val text = listOf("₹${CurrencyFormat.inr(amount)}", bank)
                .filter { it.length > 1 }.joinToString(" — ")

            val contentIntent = PendingIntent.getActivity(
                context,
                // Distinct request code per payment so receipts don't overwrite
                // each other's intents.
                (resultIntent.getStringExtra("transaction_id") ?: "").hashCode(),
                resultIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = android.app.Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_check_circle)
                .setContentTitle(title)
                .setContentText(text)
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .build()

            manager.notify((resultIntent.getStringExtra("transaction_id") ?: "").hashCode(), notification)
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not post payment result notification: ${e.message}")
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Could not post payment result notification: ${e.message}")
        }
    }

    private fun statusTitleRes(status: String): Int = when (status.uppercase()) {
        TransactionStatus.FAILED -> R.string.payment_status_failed
        TransactionStatus.NEEDS_REVIEW -> R.string.payment_status_needs_review
        TransactionStatus.UNVERIFIED -> R.string.payment_status_unverified
        else -> R.string.payment_status_success
    }
}
