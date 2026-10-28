// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Soumyadip Pal

package com.offpay.app.payment

import com.offpay.app.data.Transaction
import com.offpay.app.data.TransactionSource
import com.offpay.app.data.TransactionStatus
import com.offpay.app.payment.sms.SimpleTransaction
import com.offpay.app.states.PaymentState
import com.offpay.app.telephony.CallSessionEvent
import com.offpay.app.telephony.CallStateSource
import com.offpay.app.telephony.DeviceCallState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ExternalConfirmationTest {
    companion object {
        private const val SHORT_CALL_MS = 1_000L
        private const val NORMAL_CALL_MS = 40_000L
    }

    private class FakeCallStateSource : CallStateSource {
        override val callState = MutableStateFlow<DeviceCallState>(DeviceCallState.Idle)
        override val sessionEvents = MutableSharedFlow<CallSessionEvent>()
        var acquireCount = 0
        var releaseCount = 0
        override fun acquire(tag: String) { acquireCount++ }
        override fun release(tag: String) { releaseCount++ }

        fun callStarted(at: Long) {
            callState.value = DeviceCallState.OffHook
            sessionEvents.tryEmit(CallSessionEvent.Started(at))
        }

        fun callEnded(durationMs: Long) {
            callState.value = DeviceCallState.Idle
            sessionEvents.tryEmit(CallSessionEvent.Ended(durationMs))
        }
    }

    private class AttemptStore : PaymentTransactionStore {
        val rows = LinkedHashMap<String, Transaction>()
        var transitionAttempts = 0
        var deletePendingAttempts = 0

        override suspend fun insertPending(transaction: Transaction) {
            rows[transaction.transactionId] = transaction
        }

        override suspend fun transitionStatus(
            transactionId: String,
            expectedStatus: String,
            newStatus: String
        ): Int {
            transitionAttempts++
            val row = rows[transactionId]
            return if (row != null && row.status == expectedStatus) {
                rows[transactionId] = row.copy(status = newStatus)
                1
            } else {
                0
            }
        }

        override suspend fun confirmTransaction(
            transactionId: String,
            status: String,
            parsed: SimpleTransaction,
            verifiedAt: Long
        ): Int = error("External confirmation must never manufacture a bank receipt")

        override suspend fun deleteStalePending(now: Long): Int {
            val stale = rows.filterValues {
                it.status == TransactionStatus.PENDING && it.deadlineAt?.let { deadline -> deadline < now } == true
            }.keys
            stale.forEach { rows.remove(it) }
            return stale.size
        }

        override suspend fun deletePending(transactionId: String): Int {
            deletePendingAttempts++
            val pending = rows[transactionId]?.status == TransactionStatus.PENDING
            if (pending) rows.remove(transactionId)
            return if (pending) 1 else 0
        }
    }

    private fun TestScope.newManager(): Triple<PaymentSessionManager, AttemptStore, FakeCallStateSource> {
        val store = AttemptStore()
        val source = FakeCallStateSource()
        val manager = PaymentSessionManager(
            store = store,
            coordinator = source,
            scope = backgroundScope,
            clock = { testScheduler.currentTime }
        )
        return Triple(manager, store, source)
    }

    private fun bankSms() = SimpleTransaction(
        transactionId = "BANK123456",
        amount = "100",
        status = TransactionStatus.SUCCESS,
        bankName = "Example bank",
        smsExcerpt = "Example bank payment confirmation",
        timestamp = 0L,
        transactionType = "DEBIT"
    )

    @Test
    fun `external confirmation needs no call observer and never waits for SMS`() = runTest {
        val (manager, store, source) = newManager()
        val txnId = manager.begin("9876543210", "100", automaticVerification = false)
        runCurrent()

        assertEquals(TransactionStatus.PENDING, store.rows[txnId]!!.status)
        assertEquals(0, source.acquireCount)
        assertEquals(0, source.sessionEvents.subscriptionCount.value)
        source.callStarted(at = testScheduler.currentTime)
        source.callEnded(durationMs = SHORT_CALL_MS)
        manager.onCallNeverStarted()
        assertNull(manager.onSmsConfirmed(bankSms()))
        advanceTimeBy(PaymentSessionManager.DEFAULT_VERIFICATION_DEADLINE_MS + SHORT_CALL_MS)
        runCurrent()

        assertTrue(manager.paymentState.value is PaymentState.Initiating)
        assertEquals(
            "no verification watchdog runs in this mode",
            TransactionStatus.PENDING,
            store.rows[txnId]!!.status
        )
        assertEquals(0, store.deletePendingAttempts)
    }

    @Test
    fun `dialer handoff records an unverified attempt without a bank receipt`() = runTest {
        val (manager, store, source) = newManager()
        val txnId = manager.begin(
            phoneNumber = "",
            amount = "",
            upiId = "kirana@okhdfcbank",
            source = TransactionSource.QR,
            automaticVerification = false
        )
        // Handoff can complete before the asynchronous pending insert.
        manager.onExternalConfirmationRequired()
        runCurrent()

        val row = store.rows[txnId]!!
        assertEquals(TransactionStatus.UNVERIFIED, row.status)
        assertEquals("kirana@okhdfcbank", row.upiId)
        assertEquals(TransactionSource.QR, row.source)
        assertNull("a handoff is not a bank receipt", row.bankRef)
        assertNull(row.verifiedAt)
        assertEquals(PaymentState.Unverified(txnId, "", ""), manager.paymentState.value)
        assertTrue(manager.paymentState.value.isTerminal())
        assertFalse("unknown outcome must not recommend retry", manager.paymentState.value.canRetry())
        assertEquals(0, source.acquireCount)
        assertEquals(0, source.releaseCount)
    }

    @Test
    fun `late callbacks and deadlines cannot turn an external attempt into success`() = runTest {
        val (manager, store, source) = newManager()
        val txnId = manager.begin("9876543210", "100", automaticVerification = false)
        manager.onExternalConfirmationRequired()
        runCurrent()
        val writes = store.transitionAttempts

        assertNull(manager.onSmsConfirmed(bankSms()))
        source.callStarted(at = testScheduler.currentTime)
        source.callEnded(durationMs = NORMAL_CALL_MS)
        manager.onExternalConfirmationRequired()
        manager.onCallNeverStarted()
        manager.onUserCancelled()
        advanceTimeBy(PaymentSessionManager.DEFAULT_VERIFICATION_DEADLINE_MS + SHORT_CALL_MS)
        manager.reconcileStalePending()
        runCurrent()

        assertEquals(PaymentState.Unverified(txnId, "9876543210", "100"), manager.paymentState.value)
        assertEquals(TransactionStatus.UNVERIFIED, store.rows[txnId]!!.status)
        assertEquals("terminal attempts accept no later writes", writes, store.transitionAttempts)
        assertEquals(0, store.deletePendingAttempts)
    }

    @Test
    fun `failed external handoff is cancelled without phone state access`() = runTest {
        val (manager, store, source) = newManager()
        val txnId = manager.begin("9876543210", "100", automaticVerification = false)

        manager.onDialFailed("No dialer available")
        manager.onExternalConfirmationRequired()
        runCurrent()

        assertTrue(manager.paymentState.value is PaymentState.Cancelled)
        assertEquals(TransactionStatus.CANCELLED, store.rows[txnId]!!.status)
        assertEquals(0, source.acquireCount)
        assertEquals(0, source.releaseCount)
    }

    @Test
    fun `a later payment preserves the earlier unverified attempt`() = runTest {
        val (manager, store, source) = newManager()
        val first = manager.begin("9876543210", "100", automaticVerification = false)
        manager.onExternalConfirmationRequired()
        runCurrent()

        manager.acknowledgeResult()
        assertEquals(PaymentState.Idle, manager.paymentState.value)
        val second = manager.begin("9123456780", "200", automaticVerification = false)
        manager.onExternalConfirmationRequired()
        runCurrent()

        assertNotEquals(first, second)
        assertEquals(TransactionStatus.UNVERIFIED, store.rows[first]!!.status)
        assertEquals(TransactionStatus.UNVERIFIED, store.rows[second]!!.status)
        assertEquals(0, source.acquireCount)
    }
}
