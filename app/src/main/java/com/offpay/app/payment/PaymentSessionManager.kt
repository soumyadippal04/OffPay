// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.offpay.app.payment

import android.util.Log
import com.offpay.app.data.Transaction
import com.offpay.app.data.TransactionSource
import com.offpay.app.data.TransactionStatus
import com.offpay.app.payment.sms.SimpleTransaction
import com.offpay.app.states.PaymentState
import com.offpay.app.states.TimeoutType
import com.offpay.app.telephony.CallSessionEvent
import com.offpay.app.telephony.CallStateSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Minimal persistence surface the session manager needs. Implemented by
 * TransactionRepository; faked in unit tests.
 */
interface PaymentTransactionStore {
    suspend fun insertPending(transaction: Transaction)

    /** Atomically move a row from [expectedStatus] to [newStatus]. Returns rows changed. */
    suspend fun transitionStatus(transactionId: String, expectedStatus: String, newStatus: String): Int

    /**
     * Fill in bank-confirmed details from [parsed] on the session row,
     * recording it as [status] at [verifiedAt]. Returns rows changed.
     */
    suspend fun confirmTransaction(
        transactionId: String,
        status: String,
        parsed: SimpleTransaction,
        verifiedAt: Long
    ): Int

    /** Discards PENDING rows past their deadline. Returns rows removed. */
    suspend fun deleteStalePending(now: Long): Int

    /** Discards one row, only while it is still PENDING. Returns rows removed. */
    suspend fun deletePending(transactionId: String): Int
}

/**
 * The only writer of payment lifecycle state.
 *
 * A legacy automatically verified UPI 123Pay transfer flows through here:
 *  1. [begin] inserts a PENDING row *before* anything is dialled, so the
 *     database always knows a payment was attempted — even if the process
 *     dies mid-call.
 *  2. Call activity (from [CallStateCoordinator]) moves the in-memory
 *     [PaymentState]: OFFHOOK -> InProgress, short hang-up -> Cancelled,
 *     normal call end -> WaitingForVerification.
 *  3. A confirming bank SMS ([onSmsConfirmed]) is the only path to SUCCESS.
 *     Call duration is never treated as proof of payment.
 *  4. If no SMS arrives before the deadline the row is discarded. A payment
 *     the bank never confirmed is one this app cannot report on, so it
 *     leaves no record rather than an outcome the user can't act on.
 *
 * Stale PENDING rows from a killed process are discarded lazily via
 * [reconcileStalePending] (app start, history open, next begin()).
 *
 * The public app uses external bank confirmation: [begin] with automatic
 * verification disabled records the attempted payment without observing
 * calls or SMS. After the dialer accepts the handoff,
 * [onExternalConfirmationRequired] records UNVERIFIED. A dialer launch is
 * never evidence that money moved; the user must check with their bank.
 */
class PaymentSessionManager(
    private val store: PaymentTransactionStore,
    private val coordinator: CallStateSource,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
    private val verificationDeadlineMs: Long = DEFAULT_VERIFICATION_DEADLINE_MS,
    private val minRealCallDurationMs: Long = DEFAULT_MIN_REAL_CALL_DURATION_MS
) {

    companion object {
        private const val TAG = "PaymentSessionManager"
        const val DEFAULT_VERIFICATION_DEADLINE_MS = 10 * 60 * 1000L // 10 minutes
        const val DEFAULT_MIN_REAL_CALL_DURATION_MS = 5_000L
        private const val COORDINATOR_TAG = "payment-session"
    }

    private val _paymentState = MutableStateFlow<PaymentState>(PaymentState.Idle)
    val paymentState: StateFlow<PaymentState> = _paymentState.asStateFlow()

    private val sessionLock = Any()

    private var collectJob: Job? = null
    private var watchdogJob: Job? = null

    /** Published under sessionLock before its matching state becomes visible. */
    private var pendingWrite: PendingWrite? = null

    private data class PendingWrite(val transactionId: String, val insert: Deferred<Unit>)

    private data class SmsClaim(val write: PendingWrite, val status: String, val verifiedAt: Long)

    private var coordinatorAcquired = false
    private var automaticVerificationEnabled = true

    /**
     * Starts a new payment session and records a PENDING row. Always succeeds.
     *
     * A new payment SUPERSEDES any session still in flight. This used to be
     * refused with "a payment is already in progress", which stranded the user:
     * a session the bank never confirmed stayed in progress for its full
     * verification deadline, and every attempt to pay in the meantime was
     * blocked — including a retry of the very payment that had failed.
     *
     * Superseding is safe because a session is not what decides an outcome —
     * the bank's SMS is. The superseded PENDING row is discarded (nothing ever
     * confirmed it, so under the "no confirmation, no record" rule it must
     * leave no trace), and the caller re-arms the SMS window for the new
     * amount, so a confirmation matching what the user just entered still
     * lands on the new session exactly as before.
     *
     * [upiId] and [source] let the QR flow record what it actually knows (a
     * payee VPA, provenance QR) — the QR flow used to bypass the session
     * entirely, so an unconfirmed QR payment left no trace at all.
     *
     * With [automaticVerification] disabled, no phone-state permission or
     * SMS access is needed. The caller must report a failed dial or call
     * [onExternalConfirmationRequired] after a successful handoff.
     */
    fun begin(
        phoneNumber: String,
        amount: String,
        upiId: String? = null,
        source: String = TransactionSource.MANUAL,
        automaticVerification: Boolean = true
    ): String {
        val initiating = PaymentState.Initiating(phoneNumber, amount)
        val write = PendingWrite(initiating.transactionId, preparePendingInsert(initiating, upiId, source))
        val superseded = synchronized(sessionLock) {
            val previous = if (_paymentState.value.isInProgress()) {
                Log.w(TAG, "begin() supersedes the session still in flight")
                cleanupLocked()
                pendingWrite
            } else {
                null
            }
            // Capture the insert barrier before publishing the new state. A
            // confirming SMS must always await this session's specific write.
            pendingWrite = write
            automaticVerificationEnabled = automaticVerification
            _paymentState.value = initiating
            if (automaticVerification) {
                if (!coordinatorAcquired) {
                    coordinator.acquire(COORDINATOR_TAG)
                    coordinatorAcquired = true
                }
                observeAutomaticVerification()
            }
            previous
        }

        if (superseded != null) {
            scope.launch {
                superseded.insert.await()
                store.deletePending(superseded.transactionId)
            }
        }
        write.insert.start()
        Log.d(TAG, "Payment session started")
        return write.transactionId
    }

    private fun preparePendingInsert(
        initiating: PaymentState.Initiating,
        upiId: String?,
        source: String
    ): Deferred<Unit> {
        val now = clock()
        return scope.async<Unit>(start = CoroutineStart.LAZY) {
            runCatching { store.deleteStalePending(now) }
            store.insertPending(
                Transaction(
                    transactionId = initiating.transactionId,
                    amount = initiating.amount,
                    status = TransactionStatus.PENDING,
                    bankName = "",
                    smsExcerpt = "",
                    timestamp = now,
                    transactionType = "DEBIT",
                    phoneNumber = initiating.phoneNumber,
                    upiId = upiId,
                    deadlineAt = now + verificationDeadlineMs,
                    source = source
                )
            )
            Log.d(TAG, "PENDING row recorded for session")
        }
    }

    private fun observeAutomaticVerification() {
        collectJob = scope.launch {
            coordinator.sessionEvents.collect { event -> onCallSessionEvent(event) }
        }
        watchdogJob = scope.launch {
            delay(verificationDeadlineMs)
            onVerificationDeadline()
        }
    }

    /** The dial intent could not be launched; the session never really started. */
    fun onDialFailed(reason: String) {
        finishSession(TransactionStatus.CANCELLED) { phone, amount, txnId ->
            PaymentState.Cancelled(phone, amount, txnId, reason)
        }
    }

    /**
     * The system accepted the dialer/call handoff, but OffPay cannot verify
     * the bank's outcome. Keep an explicitly unverified attempt in history;
     * this is neither a receipt nor a reason to automatically retry payment.
     */
    fun onExternalConfirmationRequired() {
        finishSession(TransactionStatus.UNVERIFIED) { phone, amount, txnId ->
            PaymentState.Unverified(txnId, phone, amount)
        }
    }

    /** The user aborted from the in-call overlay. */
    fun onUserCancelled() {
        finishSession(TransactionStatus.CANCELLED) { phone, amount, txnId ->
            PaymentState.Cancelled(phone, amount, txnId, "Cancelled by user")
        }
    }

    /**
     * The dial intent launched but no call ever went OFFHOOK (watchdog from
     * the overlay service). Only acts while the session is still Initiating.
     */
    fun onCallNeverStarted() {
        synchronized(sessionLock) {
            if (!automaticVerificationEnabled || _paymentState.value !is PaymentState.Initiating) return
        }
        finishSession(TransactionStatus.CANCELLED) { phone, amount, txnId ->
            PaymentState.Cancelled(phone, amount, txnId, "The payment call was never connected")
        }
    }

    /**
     * Claims the session and waits for its exact database write. Production
     * ingestion must use this API before notifying or finishing goAsync().
     * Missing/failed writes propagate to the receiver, never as success.
     */
    suspend fun onSmsConfirmedAndPersisted(parsed: SimpleTransaction): String? {
        val claim = claimSmsConfirmation(parsed) ?: return null
        return persistSmsConfirmation(claim, parsed)
    }

    /** Legacy synchronous seam for lifecycle/race tests; production ingestion awaits persistence. */
    internal fun onSmsConfirmed(parsed: SimpleTransaction): String? {
        val claim = claimSmsConfirmation(parsed) ?: return null
        scope.launch { persistSmsConfirmation(claim, parsed) }
        return claim.write.transactionId
    }

    private fun claimSmsConfirmation(parsed: SimpleTransaction): SmsClaim? {
        if (parsed.transactionType == "CREDIT") {
            Log.d(TAG, "CREDIT SMS during DEBIT session - not a confirmation")
            return null
        }
        val status = when {
            parsed.status.equals(TransactionStatus.FAILED, ignoreCase = true) -> TransactionStatus.FAILED
            parsed.status.equals(TransactionStatus.NEEDS_REVIEW, ignoreCase = true) -> TransactionStatus.NEEDS_REVIEW
            else -> TransactionStatus.SUCCESS
        }
        val verifiedAt = clock()
        return synchronized(sessionLock) {
            val current = _paymentState.value
            val write = pendingWrite
            if (!automaticVerificationEnabled || !current.isInProgress() || write == null) {
                null
            } else {
                // Claim before writing: cancellation, a duplicate SMS and the
                // watchdog must not compete to change the same session's outcome.
                _paymentState.value = confirmedState(current, parsed, status, verifiedAt)
                cleanupLocked()
                SmsClaim(write, status, verifiedAt)
            }
        }
    }

    private fun confirmedState(
        current: PaymentState,
        parsed: SimpleTransaction,
        status: String,
        verifiedAt: Long
    ): PaymentState {
        val txnId = requireNotNull(current.getTransactionIdValue())
        val phone = current.getPhoneNumberValue() ?: parsed.phoneNumber ?: ""
        val amount = current.getAmountValue() ?: parsed.amount
        return when (status) {
            TransactionStatus.FAILED -> PaymentState.Failed(
                error = "Bank reported the payment as failed",
                phoneNumber = phone,
                amount = amount,
                transactionId = txnId,
                canRetry = true
            )
            TransactionStatus.NEEDS_REVIEW -> PaymentState.NeedsReview(txnId, phone, amount)
            else -> PaymentState.Success(
                transactionId = txnId,
                phoneNumber = phone,
                amount = amount,
                bankReference = parsed.transactionId,
                timestamp = verifiedAt
            )
        }
    }

    private suspend fun persistSmsConfirmation(claim: SmsClaim, parsed: SimpleTransaction): String {
        claim.write.insert.await()
        val updated = store.confirmTransaction(
            transactionId = claim.write.transactionId,
            status = claim.status,
            parsed = parsed,
            verifiedAt = claim.verifiedAt
        )
        check(updated == 1) { "The claimed payment confirmation could not be persisted" }
        Log.d(TAG, "Session row confirmed as ${claim.status}")
        return claim.write.transactionId
    }

    /** UI acknowledges a terminal result and returns the session to Idle. */
    fun acknowledgeResult() {
        synchronized(sessionLock) {
            val state = _paymentState.value
            if (state.isTerminal() || state is PaymentState.Timeout) {
                _paymentState.value = PaymentState.Idle
            }
        }
    }

    /** Discards PENDING rows whose deadline passed while we weren't running. */
    fun reconcileStalePending() {
        scope.launch {
            val discarded = runCatching { store.deleteStalePending(clock()) }.getOrDefault(0)
            if (discarded > 0) {
                Log.d(TAG, "Discarded $discarded unconfirmed stale PENDING transaction(s)")
            }
        }
    }

    private fun onCallSessionEvent(event: CallSessionEvent) {
        when (event) {
            is CallSessionEvent.Started -> {
                synchronized(sessionLock) {
                    val state = _paymentState.value
                    if (automaticVerificationEnabled && state is PaymentState.Initiating) {
                        _paymentState.value = PaymentState.InProgress(
                            step = "On the line with the UPI service",
                            progress = 0.4f,
                            phoneNumber = state.phoneNumber,
                            amount = state.amount,
                            transactionId = state.transactionId
                        )
                    }
                }
            }
            is CallSessionEvent.Ended -> {
                val state = synchronized(sessionLock) {
                    if (!automaticVerificationEnabled) return
                    _paymentState.value
                }
                when {
                    state is PaymentState.InProgress && event.durationMs < minRealCallDurationMs -> {
                        finishSession(TransactionStatus.CANCELLED) { phone, amount, txnId ->
                            PaymentState.Cancelled(
                                phone,
                                amount,
                                txnId,
                                "Call ended before the payment flow could complete"
                            )
                        }
                    }
                    state is PaymentState.InProgress -> {
                        synchronized(sessionLock) {
                            // Re-check: an SMS may have confirmed while we were deciding.
                            val s = _paymentState.value
                            if (automaticVerificationEnabled && s is PaymentState.InProgress) {
                                _paymentState.value = PaymentState.WaitingForVerification(
                                    timeout = verificationDeadlineMs,
                                    phoneNumber = s.phoneNumber,
                                    amount = s.amount,
                                    transactionId = s.transactionId
                                )
                            }
                        }
                    }
                    else -> { /* Initiating without OFFHOOK, or already terminal - nothing to do */ }
                }
            }
        }
    }

    /**
     * The deadline passed with no confirming bank SMS, so the row is
     * discarded and nothing is shown: no confirmation means no payment to
     * report. A genuine confirmation arriving in the SMS window's remaining
     * grace still surfaces — with the row gone there is nothing to adopt, so
     * the ingestion pipeline records it as a standalone transaction.
     */
    private fun onVerificationDeadline() {
        // Claim FIRST, write second. Queueing the delete before the claim let a
        // confirming SMS win the lock (showing "Payment successful") while the
        // already-queued deletePending still ran, removing the very row that
        // confirmation had just landed on — success on screen, nothing in
        // history. Claiming inside the lock means only the winner writes.
        val write = synchronized(sessionLock) {
            val s = _paymentState.value
            if (!automaticVerificationEnabled || !s.isInProgress()) return
            val pending = pendingWrite ?: return
            val id = pending.transactionId
            _paymentState.value = PaymentState.Timeout(
                timeoutType = TimeoutType.BANK_VERIFICATION,
                phoneNumber = s.getPhoneNumberValue() ?: "",
                amount = s.getAmountValue() ?: "",
                transactionId = id
            )
            cleanupLocked()
            pending
        }

        scope.launch {
            write.insert.await()
            store.deletePending(write.transactionId)
        }
        Log.w(TAG, "No bank SMS before deadline - unconfirmed session discarded")
    }

    private fun finishSession(
        rowStatus: String,
        terminalState: (phone: String, amount: String, txnId: String) -> PaymentState
    ) {
        // Claim FIRST, write second — same invariant as onSmsConfirmed. When
        // the write was queued before the claim, a cancel landing at the same
        // instant as the bank's SMS could show "Payment successful" while the
        // already-queued PENDING->CANCELLED transition still ran, leaving
        // history contradicting the screen the user was looking at. Only the
        // caller that actually claims the terminal state may write.
        val write = synchronized(sessionLock) {
            val s = _paymentState.value
            if (!s.isInProgress()) return
            val pending = pendingWrite ?: return
            val id = pending.transactionId
            _paymentState.value = terminalState(
                s.getPhoneNumberValue() ?: "",
                s.getAmountValue() ?: "",
                id
            )
            cleanupLocked()
            pending
        }

        scope.launch {
            write.insert.await()
            store.transitionStatus(write.transactionId, TransactionStatus.PENDING, rowStatus)
        }
    }

    private fun cleanupLocked() {
        collectJob?.cancel()
        collectJob = null
        watchdogJob?.cancel()
        watchdogJob = null
        if (coordinatorAcquired) {
            coordinator.release(COORDINATOR_TAG)
            coordinatorAcquired = false
        }
    }
}
