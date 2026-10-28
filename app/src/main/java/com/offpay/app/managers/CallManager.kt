// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.offpay.app.managers

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.PhoneStateListener
import android.telephony.TelephonyManager
import android.util.Log
import android.widget.Toast
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.offpay.app.R
import com.offpay.app.constants.AppConstants
import com.offpay.app.payment.Upi123CallStringBuilder
import com.offpay.app.payment.messageFor
import java.util.concurrent.atomic.AtomicBoolean

enum class CallType {
    USSD, VOICE, UPI123, MANUAL_TRANSFER
}

class CallManager(private val context: Context) {

    companion object {
        private const val TAG = "CallManager"
    }

    private val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager

    // Call state tracking - thread-safe with proper synchronization
    private var _isCallInProgress = mutableStateOf(false)

    @Volatile
    private var currentCallType: CallType? = null
    private var onCallEndedCallback: ((CallType) -> Unit)? = null
    private var onUssdSessionComplete: (() -> Unit)? = null
    private var phoneStateListener: PhoneStateListener? = null

    // Synchronization objects for thread safety
    private val callStateLock = Any()
    private val callbackLock = Any()
    private val listenerLock = Any()

    private val isUSSDDialPending = AtomicBoolean(false)

    /**
     * Thread-safe callback management
     */
    private fun setCallEndedCallback(callback: ((CallType) -> Unit)?) {
        synchronized(callbackLock) {
            onCallEndedCallback = callback
        }
    }

    private fun setUssdSessionCompleteCallback(callback: (() -> Unit)?) {
        synchronized(callbackLock) {
            onUssdSessionComplete = callback
        }
    }

    private fun getCallEndedCallback(): ((CallType) -> Unit)? {
        synchronized(callbackLock) {
            return onCallEndedCallback
        }
    }

    private fun getUssdSessionCompleteCallback(): (() -> Unit)? {
        synchronized(callbackLock) {
            return onUssdSessionComplete
        }
    }

    private fun clearCallbacks() {
        synchronized(callbackLock) {
            onCallEndedCallback = null
            onUssdSessionComplete = null
        }
    }

    /** Hands off to the phone app; launching a call never proves its outcome. */
    fun initiateCall(
        context: Context,
        phoneNumber: String,
        callType: CallType,
        onCallEnded: (CallType) -> Unit,
        onUssdComplete: (() -> Unit)? = null
    ): PaymentDialer.Result {
        if (callType == CallType.USSD && !isUSSDDialPending.compareAndSet(false, true)) {
            return PaymentDialer.Result.UNAVAILABLE
        }
        synchronized(callStateLock) {
            currentCallType = callType
            setCallEndedCallback(onCallEnded)
            setUssdSessionCompleteCallback(onUssdComplete)
            _isCallInProgress.value = true
        }
        registerPhoneStateListener()
        val result = PaymentDialer(context).launch(Uri.fromParts("tel", phoneNumber, null))
        if (result != PaymentDialer.Result.DIRECT_CALL) {
            // A dialer handoff can be cancelled; don't attach later unrelated calls to this attempt.
            endCall()
        }
        explainDialerResult(result)
        return result
    }

    fun isCallStateTrackingAvailable(): Boolean = synchronized(listenerLock) { phoneStateListener != null }

    private fun registerPhoneStateListener() {
        unregisterPhoneStateListener()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        synchronized(listenerLock) {
            try {
                val listener = createPhoneStateListener()
                telephonyManager.listen(listener, PhoneStateListener.LISTEN_CALL_STATE)
                phoneStateListener = listener
            } catch (_: SecurityException) {
                // Some OEMs deny access despite the permission check. Manual confirmation remains available.
                phoneStateListener = null
                Log.w(TAG, "Call-state access unavailable")
            }
        }
    }

    private fun unregisterPhoneStateListener() {
        synchronized(listenerLock) {
            try {
                phoneStateListener?.let { telephonyManager.listen(it, PhoneStateListener.LISTEN_NONE) }
            } catch (_: SecurityException) {
                Log.w(TAG, "Call-state access revoked")
            } finally {
                phoneStateListener = null
            }
        }
    }

    private fun createPhoneStateListener(): PhoneStateListener {
        return object : PhoneStateListener() {
            private var observedActiveCall = false

            @Deprecated("Deprecated in Java")
            override fun onCallStateChanged(state: Int, phoneNumber: String?) {
                when (state) {
                    TelephonyManager.CALL_STATE_OFFHOOK -> {
                        observedActiveCall = true
                        _isCallInProgress.value = true
                    }
                    TelephonyManager.CALL_STATE_IDLE -> {
                        // Registration immediately emits IDLE. It is not evidence that a test call ended.
                        if (observedActiveCall) {
                            observedActiveCall = false
                            val type = currentCallType
                            val callback = getCallEndedCallback()
                            val ussdCallback = getUssdSessionCompleteCallback()
                            endCall()
                            if (type != null) callback?.invoke(type)
                            if (type == CallType.USSD) ussdCallback?.invoke()
                        }
                    }
                }
            }
        }
    }

    /** Stops this helper's tracking; it does not hang up the system phone call. */
    fun endCall() {
        synchronized(callStateLock) {
            _isCallInProgress.value = false
            currentCallType = null
            isUSSDDialPending.set(false)
            clearCallbacks()
        }
        unregisterPhoneStateListener()
    }

    private fun explainDialerResult(result: PaymentDialer.Result) {
        when (result) {
            PaymentDialer.Result.DIALER_OPENED ->
                Toast.makeText(context, R.string.dialer_finish_in_phone, Toast.LENGTH_LONG).show()
            PaymentDialer.Result.UNAVAILABLE ->
                Toast.makeText(context, R.string.dialer_unavailable, Toast.LENGTH_LONG).show()
            PaymentDialer.Result.DIRECT_CALL -> Unit
        }
    }

    /**
     * Checks if call is in progress
     */
    fun isCallInProgress(): Boolean = _isCallInProgress.value

    /**
     * Constructs the UPI123 call string in the format: tel:<serviceNumber>,,1,<phoneNumber>,,<amount>,,1
     * Validation and construction live in the pure, unit-tested Upi123CallStringBuilder.
     */
    fun constructUPI123CallString(phoneNumber: String, amount: String): String {
        return when (
            val result = Upi123CallStringBuilder.build(
                AppConstants.DEFAULT_UPI_SERVICE_NUMBER,
                phoneNumber,
                amount
            )
        ) {
            is Upi123CallStringBuilder.Result.Valid -> result.callString
            is Upi123CallStringBuilder.Result.Invalid ->
                throw IllegalArgumentException(result.reason.name)
        }
    }

    /**
     * Initiates a UPI123 call with the given phone number and amount
     */
    fun initiateUPI123Call(phoneNumber: String, amount: String): Boolean {
        return try {
            val result = Upi123CallStringBuilder.build(
                AppConstants.DEFAULT_UPI_SERVICE_NUMBER,
                phoneNumber.orEmpty(),
                amount.orEmpty()
            )
            val callString = when (result) {
                is Upi123CallStringBuilder.Result.Invalid -> {
                    Log.e(TAG, "UPI123 call rejected: ${result.reason.name}")
                    Toast.makeText(
                        context,
                        result.reason.messageFor(context),
                        Toast.LENGTH_SHORT
                    ).show()
                    return false
                }
                is Upi123CallStringBuilder.Result.Valid -> result.callString
            }

            val launchResult = PaymentDialer(context).launch(Uri.parse(callString))
            explainDialerResult(launchResult)
            launchResult != PaymentDialer.Result.UNAVAILABLE
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception while initiating call", e)
            Toast.makeText(context, R.string.error_call_permission_denied, Toast.LENGTH_SHORT).show()
            false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error while initiating UPI123 call", e)
            Toast.makeText(context, R.string.error_call_failed, Toast.LENGTH_SHORT).show()
            false
        }
    }

    /** Releases tracking and callbacks without changing call audio or ending the phone call. */
    fun cleanup() {
        endCall()
    }
}
