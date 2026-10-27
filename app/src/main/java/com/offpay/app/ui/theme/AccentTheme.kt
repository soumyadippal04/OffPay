// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.offpay.app.ui.theme

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

data class OffPayAccentTheme(
    val primary: Color,
    val primaryDark: Color,
    val headerGradientStart: Color,
    val headerGradientEnd: Color,
    val accent: Color,
    val accentLight: Color
)

val BlueAccentTheme = OffPayAccentTheme(
    primary = Color(0xFF659AF5),
    primaryDark = Color(0xFF237DD8),
    headerGradientStart = Color(0xFF86B5FF),
    headerGradientEnd = Color(0xFF76A5F5),
    accent = Color(0xFF559CEB),
    accentLight = Color(0xFF63AEFF)
)

val LocalOffPayAccentTheme = compositionLocalOf { BlueAccentTheme }
