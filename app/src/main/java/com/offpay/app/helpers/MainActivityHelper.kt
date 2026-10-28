// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.offpay.app.helpers

import android.content.Context
import android.content.Intent
import android.util.Log
import com.offpay.app.OffPayApplication
import com.offpay.app.R
import com.offpay.app.SetupActivity
import com.offpay.app.TestConfigurationActivity
import com.offpay.app.constants.AppConstants
import com.offpay.app.managers.CallManager
import com.offpay.app.managers.SmsConfirmation
import com.offpay.app.payment.PaymentInputValidator
import com.offpay.app.payment.Upi123CallStringBuilder
import com.offpay.app.payment.messageFor
import com.offpay.app.states.PaymentState

/**
 * Helper class containing all business logic for MainActivity
 * This separates the UI concerns from the business logic
 *
 * Call-state tracking lives in CallStateCoordinator and payment lifecycle
 * in PaymentSessionManager (both app-scoped) — this class no longer keeps
 * its own PhoneStateListener or duration timers.
 */
class MainActivityHelper(
    private val context: Context,
    private val uiCallback: UICallback
) {
    companion object {
        private const val TAG = "MainActivityHelper"
    }

    // Managers
    private var callManager: CallManager? = null

    /**
     * Interface for UI callbacks
     */
    interface UICallback {
        fun showToast(message: String)
        fun updatePaymentState(paymentState: PaymentState)
        fun navigateToSetup()
        fun navigateToTestConfiguration()
        fun finishActivity()
        fun launchQRScanner(intent: Intent)
    }

    /**
     * Open QR scanner
     */
    private fun openQRScanner() {
        val intent = Intent(context, com.offpay.app.features.qr_scanner.presentation.QRScannerActivity::class.java)
        uiCallback.launchQRScanner(intent)
    }

    /**
     * Start QR scanning - public method for UI access
     */
    fun startQRScanning() {
        Log.d(TAG, "=== STARTING QR SCANNING ===")

        SetupHelper.getScanToPayBlockedMessage(context)?.let { message ->
            Log.d(TAG, "Scan to pay blocked: $message")
            uiCallback.showToast(message)
            return
        }

        uiCallback.showToast(context.getString(R.string.status_opening_scanner))
        openQRScanner()
    }

    /**
     * Initialize all managers and services
     */
    fun initialize() {
        try {
            callManager = CallManager(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error during initialization: ${e.message}")
        }
    }

    /**
     * Check if setup is completed
     */
    fun isSetupCompleted(): Boolean {
        val sharedPreferences = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean(AppConstants.KEY_SETUP_COMPLETED, false)
    }

    /**
     * Check if test configuration is completed
     */
    fun isTestCompleted(): Boolean {
        val sharedPreferences = context.getSharedPreferences(AppConstants.PREFS_NAME, Context.MODE_PRIVATE)
        return sharedPreferences.getBoolean(AppConstants.KEY_TEST_COMPLETED, false)
    }

    /**
     * Initiate transfer with validation and business logic.
     *
     * Records a PENDING transaction via PaymentSessionManager BEFORE dialing,
     * so the database knows about the attempt even if the process dies
     * mid-call. Handoff is stored as UNVERIFIED; the bank provides confirmation.
     */
    /**
     * Validates what the user typed, reporting the first problem found.
     * Returns false when the transfer must not proceed.
     *
     * The amount ceiling is the 123Pay IVR's, not a generic input limit: the
     * IVR rejects anything from ₹5,000 up, and it does so *mid-call*, after
     * the user has already dialled. Catching it here turns a confusing failed
     * call into a clear message before anything is placed.
     */
    @Suppress("ReturnCount") // one guard clause per rule reads clearer than one accumulated condition
    private fun isTransferInputValid(phoneNumber: String, amount: String): Boolean {
        if (phoneNumber.isBlank() || amount.isBlank()) {
            uiCallback.showToast(context.getString(R.string.error_enter_phone_and_amount))
            return false
        }
        if (!PaymentInputValidator.isValidPhoneNumber(phoneNumber)) {
            uiCallback.showToast(context.getString(R.string.error_invalid_phone_number))
            return false
        }
        val amountValue = amount.toDoubleOrNull()
        if (amountValue == null || amountValue < AppConstants.MIN_AMOUNT_VALUE) {
            uiCallback.showToast(context.getString(R.string.error_invalid_amount))
            return false
        }
        if (amountValue > AppConstants.UPI123PAY_MAX_AMOUNT) {
            // Same reason, same wording as the one Upi123CallStringBuilder
            // raises further down the path — see Reason.AMOUNT_ABOVE_CAP.
            uiCallback.showToast(
                Upi123CallStringBuilder.Reason.AMOUNT_ABOVE_CAP.messageFor(context)
            )
            return false
        }
        return true
    }

    fun initiateTransfer(phoneNumber: String, amount: String) {
        Log.d(TAG, "Initiating transfer")

        if (!isTransferInputValid(phoneNumber, amount)) return

        val sessionManager = OffPayApplication.from(context)?.paymentSessionManager
        if (sessionManager == null) {
            Log.e(TAG, "PaymentSessionManager unavailable")
            uiCallback.showToast(context.getString(R.string.error_payment_not_started))
            return
        }

        // Record only an attempt; opening the dialer does not prove money moved.
        val automaticVerification = SmsConfirmation.isAvailable(context)
        TransactionDetector.getInstance(context).stopOperation()
        val transactionId = sessionManager.begin(phoneNumber, amount, automaticVerification = automaticVerification)
        if (automaticVerification) {
            TransactionDetector.getInstance(context).startOperation(
                operationType = "UPI_123",
                expectedAmount = amount,
                phoneNumber = phoneNumber,
                sessionTxnId = transactionId
            )
        }
        val handedOff = callManager?.initiateUPI123Call(phoneNumber, amount) ?: false
        if (handedOff) {
            if (!automaticVerification) sessionManager.onExternalConfirmationRequired()
            uiCallback.showToast(context.getString(R.string.compatibility_check_bank))
        } else {
            sessionManager.onDialFailed("Could not open the payment dialer")
            if (automaticVerification) TransactionDetector.getInstance(context).stopOperation()
        }
    }

    /**
     * Handle app lifecycle events. Call-state listening is owned by the
     * app-scoped CallStateCoordinator, so nothing telephony-related needs
     * registering or unregistering here any more.
     */
    fun onPause() = Unit

    fun onStop() = Unit

    fun onResume() = Unit

    fun onDestroy() {
        try {
            try {
                callManager?.cleanup()
            } catch (e: Exception) {
                Log.w(TAG, "Error cleaning up CallManager: ${e.message}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during cleanup: ${e.message}")
        }
    }

    /**
     * Navigate to setup screen
     */
    fun navigateToSetup() {
        val intent = Intent(context, SetupActivity::class.java)
        context.startActivity(intent)
    }

    /**
     * Navigate to test configuration screen
     */
    fun navigateToTestConfiguration() {
        val intent = Intent(context, TestConfigurationActivity::class.java)
        context.startActivity(intent)
    }

    /**
     * Cleanup resources and stop monitoring
     */
    fun cleanup() {
        Log.d(TAG, "Cleaning up resources")
    }
}
