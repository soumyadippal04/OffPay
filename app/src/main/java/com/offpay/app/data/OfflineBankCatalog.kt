// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Soumyadip Pal

package com.offpay.app.data

import java.util.Locale

data class Bank(val id: String, val name: String, val aliases: List<String> = emptyList())

/**
 * Shared bank directory for setup, Settings and the home screen.
 *
 * A directory entry identifies the account bank, not its offline-payment capabilities.
 * Availability must be checked with the bank and the actual service/SIM. See docs/OFFLINE_BANKS.md.
 */
object OfflineBankCatalog {
    val banks: List<Bank> = bankDirectoryEntries.sortedBy { it.name.lowercase(Locale.ROOT) } +
        Bank("other", "Other bank (check availability)")

    private val banksById = banks.associateBy(Bank::id)
    private val separators = Regex("[^a-z0-9]+")
    private val searchableBanks = banks.map { bank ->
        bank to normalize(listOf(bank.id, bank.name).plus(bank.aliases).joinToString(" "))
    }

    fun findById(id: String): Bank? = banksById[id]

    /** Unknown saved IDs remain unknown rather than silently changing the user's bank. */
    fun nameFor(id: String): String? = findById(id)?.name

    fun search(query: String): List<Bank> {
        val normalizedQuery = normalize(query)
        if (normalizedQuery.isEmpty()) return banks
        val terms = normalizedQuery.split(" ")
        return searchableBanks
            .filter { (_, searchableName) -> terms.all { searchableName.contains(it) } }
            .map { (bank, _) -> bank }
            .sortedBy { bank ->
                when {
                    listOf(bank.id, bank.name).plus(bank.aliases).any { normalize(it) == normalizedQuery } -> 0
                    normalize(bank.name).startsWith(normalizedQuery) -> 1
                    else -> 2
                }
            }
    }

    private fun normalize(value: String): String =
        value.lowercase(Locale.ROOT).replace(separators, " ").trim()
}
