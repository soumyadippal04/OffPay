// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Flowpay

package com.offpay.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.offpay.app.OffPayApplication
import com.offpay.app.data.SettingsRepository
import com.offpay.app.utils.findComponentActivity

internal val OffPayDarkColors = darkColorScheme(
    primary = Color(0xFF659AF5),
    onPrimary = Color(0xFF102849),
    secondary = Color(0xFF559CEB),
    tertiary = Color(0xFF4CAF50),
    background = Color(0xFF0A0A0A),
    surface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFF2A2A2A),
    outline = Color(0xFF707070),
    outlineVariant = Color(0xFF333333),
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFAAAAAA)
)

internal val OffPayLightColors = lightColorScheme(
    primary = Color(0xFF235AB9),
    onPrimary = Color.White,
    secondary = Color(0xFF2563EB),
    tertiary = Color(0xFF216E3A),
    background = Color(0xFFF5F7FC),
    surface = Color.White,
    surfaceVariant = Color(0xFFE9EEF7),
    outline = Color(0xFF68788F),
    outlineVariant = Color(0xFFD5DDEB),
    onBackground = Color(0xFF17243B),
    onSurface = Color(0xFF17243B),
    onSurfaceVariant = Color(0xFF58657A)
)

/** All Compose entry points observe the same saved preference, including screens in the back stack. */
@Composable
fun OffPayTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val repository = remember(context) {
        (context.applicationContext as? OffPayApplication)?.settingsRepository
            ?: SettingsRepository(context.applicationContext)
    }
    val settings by repository.settingsFlow.collectAsState()
    val dark = settings.darkModeEnabled
    val colors = if (dark) OffPayDarkColors else OffPayLightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context.findComponentActivity() ?: return@SideEffect
            applyWindowTheme(activity, dark)
        }
    }
    CompositionLocalProvider(LocalOffPayAccentTheme provides if (dark) BlueAccentTheme else LightBlueAccentTheme) {
        MaterialTheme(colorScheme = colors, typography = Typography, content = content)
    }
}
