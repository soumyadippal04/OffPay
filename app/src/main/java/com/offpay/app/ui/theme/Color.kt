// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.offpay.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

// ─────────────────────────────────────────────────────────────────────────
// OffPay semantic design tokens — resolved from the selected light/dark theme.
//
// Every Compose screen draws from these; inline Color(0x…) literals outside
// this package are a CI failure (see the palette-gate step in build.yml).
// The ramp consolidates the near-duplicate greys that had accreted across
// screens (0x1E1E1E vs 0x1A1A1A, 0x8A8A8A vs 0x888888, …) into one value
// per visual role, so "card grey" or "secondary text" can be changed in
// exactly one place.
// ─────────────────────────────────────────────────────────────────────────

// Semantic surfaces; legacy token names remain for existing screen call sites.
val OffPayBlack: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.background

/** Screen background behind cards/lists. */
val OffPaySurfaceDim: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.background

/** Card / dialog surface. */
val OffPayDarkGray: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.surface

/** Elevated surface: input fields, chips, avatars. */
val OffPayMediumGray: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.surfaceVariant

/** Borders, dividers, inactive track. */
val OffPayLightGray: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.outlineVariant

/** Stronger outline / disabled container. */
val OffPayOutlineGray: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.outline

/** Disabled content / faint hint. */
val OffPayDisabledGray: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

// Text (dimmest → brightest)
/** Placeholder / hint text. */
val OffPayTextGray: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.onSurfaceVariant

/** Secondary text: captions, labels, timestamps. */
val OffPayTextLightGray: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.onSurfaceVariant

/** Long-form body text on dark dialogs. */
val OffPayTextPale: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.onSurface

val OffPayTextWhite: Color
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme.onSurface

// Card Colors (light card variant)
val OffPayCardBackground = Color(0xFFE8E8E8)
val OffPayCardText = Color(0xFF000000)
val OffPayCardSubtext = Color(0xFF4A4A4A)

// Accents
val OffPayAccentBlue = Color(0xFF4A90E2)
val OffPayAccentGreen = Color(0xFF4CAF50)

/** Bright green used as the light end of success gradients. */
val OffPayAccentGreenBright = Color(0xFF43E97B)

// ─────────────────────────────────────────────────────────────────────────
// Transaction status palette. One color per outcome, used identically in
// the history list, detail dialog and result screen so a status never
// changes meaning between screens.
// ─────────────────────────────────────────────────────────────────────────

/** SUCCESS — bank confirmed. */
val OffPayStatusSuccess = OffPayAccentGreen

/** FAILED / declined, and destructive actions (delete, clear). */
val OffPayStatusError = Color(0xFFF44336)

/** NEEDS_REVIEW / PENDING — user attention required. */
val OffPayStatusWarning = Color(0xFFFF9800)

/** UNVERIFIED / CANCELLED — outcome unknown or nothing happened. Neutral:
 *  deliberately neither success-green nor failure-red. */
val OffPayStatusNeutral = Color(0xFF9E9E9E)

/**
 * The single mapping from a [com.offpay.app.data.TransactionStatus] string
 * to its display color. Replaces the byte-identical getStatusColor()
 * functions that had been copy-pasted into multiple screens.
 */
fun statusColor(status: String): Color = when (status.uppercase()) {
    "SUCCESS", "SUCCESSFUL", "COMPLETED" -> OffPayStatusSuccess
    "FAILED", "DECLINED" -> OffPayStatusError
    "PENDING", "NEEDS_REVIEW" -> OffPayStatusWarning
    else -> OffPayStatusNeutral // UNVERIFIED, CANCELLED, unknown
}

/** Dark ink stays legible on the bright blue brand gradients in either theme. */
val OffPayOnGradient = Color(0xFF102849)
