// SPDX-License-Identifier: Apache-2.0

package com.offpay.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineBankCatalogTest {
    private companion object {
        const val REVIEWED_ISSUER_COUNT = 714
    }

    @Test
    fun `existing saved bank IDs still resolve to the same names`() {
        val existingBanks = mapOf(
            "sbi" to "State Bank of India",
            "hdfc" to "HDFC Bank",
            "icici" to "ICICI Bank",
            "axis" to "Axis Bank",
            "kotak" to "Kotak Mahindra Bank",
            "pnb" to "Punjab National Bank",
            "bob" to "Bank of Baroda",
            "yes" to "Yes Bank",
            "idbi" to "IDBI Bank",
            "canara" to "Canara Bank"
        )
        existingBanks.forEach { (id, name) -> assertEquals(name, OfflineBankCatalog.nameFor(id)) }
    }

    @Test
    fun `search accepts aliases and prioritizes the intended bank`() {
        assertEquals("central", OfflineBankCatalog.search(" CBI ").first().id)
        assertEquals("central", OfflineBankCatalog.search("CENTRAL BANK").first().id)
        assertEquals("bob", OfflineBankCatalog.search("BoB").first().id)
        assertEquals("sbi", OfflineBankCatalog.search("SBI").first().id)
        assertEquals("idfc_first", OfflineBankCatalog.search("IDFC-first").first().id)
        assertEquals("Central Bank of India", OfflineBankCatalog.nameFor("central"))
        assertEquals("West Bengal Gramin Bank", OfflineBankCatalog.search("Bangiya Gramin Vikash").first().name)
    }

    @Test
    fun `unlisted banks remain selectable without substituting a different bank`() {
        assertNull(OfflineBankCatalog.nameFor("not_a_known_bank"))
        assertTrue(OfflineBankCatalog.search("not_a_known_bank").isEmpty())
        assertEquals("other", OfflineBankCatalog.banks.last().id)
        assertTrue(OfflineBankCatalog.findById("other")!!.name.contains("check availability"))
    }

    @Test
    fun `directory is complete for the reviewed issuer snapshot and excludes PPI wallets`() {
        assertEquals(REVIEWED_ISSUER_COUNT, bankDirectoryEntries.size)
        assertEquals(OfflineBankCatalog.banks.size, OfflineBankCatalog.banks.map(Bank::id).distinct().size)
        assertEquals(REVIEWED_ISSUER_COUNT, bankDirectoryEntries.map(Bank::name).distinct().size)
        assertFalse(bankDirectoryEntries.any { it.name == "Amazon" || it.name.contains("(Ppi)") })
        assertFalse(bankDirectoryEntries.any { it.name == "HDFC Bank PPI" })
        assertEquals(OfflineBankCatalog.banks, OfflineBankCatalog.search("   "))
    }
}
