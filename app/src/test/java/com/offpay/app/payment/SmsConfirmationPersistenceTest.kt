// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Soumyadip Pal

package com.offpay.app.payment

import com.offpay.app.data.Transaction
import com.offpay.app.data.TransactionStatus
import com.offpay.app.payment.sms.SimpleTransaction
import com.offpay.app.telephony.CallSessionEvent
import com.offpay.app.telephony.CallStateSource
import com.offpay.app.telephony.DeviceCallState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SmsConfirmationPersistenceTest {
    private class SilentCallSource : CallStateSource {
        override val callState = MutableStateFlow<DeviceCallState>(DeviceCallState.Idle)
        override val sessionEvents = MutableSharedFlow<CallSessionEvent>()
        override fun acquire(tag: String) = Unit
        override fun release(tag: String) = Unit
    }

    private class GatedStore : PaymentTransactionStore {
        val rows = LinkedHashMap<String, Transaction>()
        val insertGates = LinkedHashMap<String, CompletableDeferred<Unit>>()
        var holdInserts = false
        var confirmationGate: CompletableDeferred<Unit>? = null
        var confirmationFailure: IllegalStateException? = null
        var returnNoUpdatedRows = false

        override suspend fun insertPending(transaction: Transaction) {
            if (holdInserts) {
                val gate = CompletableDeferred<Unit>()
                insertGates[transaction.transactionId] = gate
                gate.await()
            }
            rows[transaction.transactionId] = transaction
        }

        override suspend fun transitionStatus(
            transactionId: String,
            expectedStatus: String,
            newStatus: String
        ): Int {
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
        ): Int {
            confirmationGate?.await()
            confirmationFailure?.let { throw it }
            val row = rows[transactionId]
            return if (!returnNoUpdatedRows && row?.status == TransactionStatus.PENDING) {
                rows[transactionId] = row.copy(status = status, bankRef = parsed.transactionId, verifiedAt = verifiedAt)
                1
            } else {
                0
            }
        }

        override suspend fun deleteStalePending(now: Long): Int = 0
        override suspend fun deletePending(transactionId: String): Int {
            val pending = rows[transactionId]?.status == TransactionStatus.PENDING
            if (pending) rows.remove(transactionId)
            return if (pending) 1 else 0
        }
    }

    private fun TestScope.manager(store: GatedStore) = PaymentSessionManager(
        store = store,
        coordinator = SilentCallSource(),
        scope = backgroundScope,
        clock = { testScheduler.currentTime }
    )

    private fun bankSms() = SimpleTransaction(
        transactionId = "BANKREF123",
        amount = "100",
        status = TransactionStatus.SUCCESS,
        bankName = "Example bank",
        smsExcerpt = "Bank-confirmed debit",
        timestamp = 0L,
        transactionType = "DEBIT"
    )

    @Test
    fun `production confirmation does not return or publish before the database write`() = runTest {
        val store = GatedStore()
        val manager = manager(store)
        val id = manager.begin("9876543210", "100")
        runCurrent()
        val gate = CompletableDeferred<Unit>()
        store.confirmationGate = gate
        var notified = false
        val ingestion = async {
            val owner = manager.onSmsConfirmedAndPersisted(bankSms())
            notified = true
            owner
        }
        runCurrent()

        assertFalse(ingestion.isCompleted)
        assertFalse(notified)
        assertEquals(TransactionStatus.PENDING, store.rows[id]!!.status)
        gate.complete(Unit)
        runCurrent()

        assertEquals(id, ingestion.await())
        assertTrue(notified)
        assertEquals(TransactionStatus.SUCCESS, store.rows[id]!!.status)
    }

    @Test
    fun `failed persistence propagates before a caller can notify success`() = runTest {
        val store = GatedStore()
        val manager = manager(store)
        val id = manager.begin("9876543210", "100")
        runCurrent()
        val failure = IllegalStateException("database unavailable")
        store.confirmationFailure = failure
        var notified = false
        val result = runCatching {
            manager.onSmsConfirmedAndPersisted(bankSms())
            notified = true
        }

        assertEquals(failure, result.exceptionOrNull())
        assertFalse(notified)
        assertEquals(TransactionStatus.PENDING, store.rows[id]!!.status)
    }

    @Test
    fun `zero updated rows are not reported as a persisted confirmation`() = runTest {
        val store = GatedStore()
        val manager = manager(store)
        manager.begin("9876543210", "100")
        runCurrent()
        store.returnNoUpdatedRows = true

        val result = runCatching { manager.onSmsConfirmedAndPersisted(bankSms()) }

        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }

    @Test
    fun `a new session cannot replace the insert barrier of an already claimed SMS`() = runTest {
        val store = GatedStore().apply { holdInserts = true }
        val manager = manager(store)
        val first = manager.begin("9876543210", "100")
        runCurrent()
        val confirmation = async { manager.onSmsConfirmedAndPersisted(bankSms()) }
        runCurrent()
        assertFalse(confirmation.isCompleted)

        val second = manager.begin("9123456780", "200")
        runCurrent()
        store.insertGates.getValue(first).complete(Unit)
        runCurrent()

        assertTrue("first confirmation must not wait on the second insert", confirmation.isCompleted)
        assertEquals(first, confirmation.await())
        assertEquals(TransactionStatus.SUCCESS, store.rows[first]!!.status)
        assertNull("second insert is still blocked independently", store.rows[second])
        store.insertGates.getValue(second).complete(Unit)
        runCurrent()
        assertEquals(TransactionStatus.PENDING, store.rows[second]!!.status)
    }
}
