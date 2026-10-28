// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.offpay.app

import android.app.Application
import android.content.Context
import android.os.StrictMode
import android.util.Log
import com.offpay.app.data.SettingsRepository
import com.offpay.app.di.AppContainer
import com.offpay.app.payment.PaymentSessionManager
import com.offpay.app.telephony.CallStateCoordinator
import kotlinx.coroutines.CoroutineScope

class OffPayApplication : Application() {

    /** The manual composition root; see [AppContainer]. */
    val container: AppContainer by lazy { AppContainer(applicationContext) }

    // Delegating accessors so existing call sites
    // (OffPayApplication.from(context)?.paymentSessionManager, etc.) keep
    // working while construction lives in the container.
    val appScope: CoroutineScope get() = container.appScope
    val settingsRepository: SettingsRepository get() = container.settingsRepository
    val callStateCoordinator: CallStateCoordinator get() = container.callStateCoordinator
    val paymentSessionManager: PaymentSessionManager get() = container.paymentSessionManager

    companion object {
        private const val TAG = "OffPayApplication"

        /** Convenience accessor for receivers/services that only hold a Context. */
        fun from(context: Context): OffPayApplication? =
            context.applicationContext as? OffPayApplication
    }

    override fun onCreate() {
        super.onCreate()
        // Debug-only tripwire: the first database materialisation (SQLCipher
        // open, Keystore unwrap, upgrade migration) must never run on the
        // main thread — it once did, from this very method. penaltyLog keeps
        // debug builds usable while making any regression loudly visible.
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
        }
        // Discard any PENDING rows whose deadline passed while the app was
        // dead. The store resolves lazily on IO (see LazyTransactionStore),
        // so this no longer opens the database on the main thread.
        paymentSessionManager.reconcileStalePending()
        if (BuildConfig.SMS_CONFIRMATION_AVAILABLE) container.paymentWindowObserver.start()
        Log.d(TAG, "OffPayApplication initialized")
    }
}
