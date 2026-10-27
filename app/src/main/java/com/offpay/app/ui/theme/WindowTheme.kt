// SPDX-License-Identifier: Apache-2.0
package com.offpay.app.ui.theme

import android.app.Activity
import android.content.Context
import android.graphics.drawable.ColorDrawable
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import com.offpay.app.OffPayApplication
import com.offpay.app.data.SettingsRepository

/** Shared by Compose and classic View screens, so navigation never forces dark system bars. */
fun isDarkMode(context: Context): Boolean {
    val repository = (context.applicationContext as? OffPayApplication)?.settingsRepository
        ?: SettingsRepository(context.applicationContext)
    return repository.settingsFlow.value.darkModeEnabled
}

fun savedColorScheme(context: Context) = if (isDarkMode(context)) OffPayDarkColors else OffPayLightColors

fun applyWindowTheme(activity: Activity, dark: Boolean = isDarkMode(activity)) {
    val background = (if (dark) OffPayDarkColors else OffPayLightColors).background.toArgb()
    activity.window.apply {
        statusBarColor = background
        navigationBarColor = background
        setBackgroundDrawable(ColorDrawable(background))
        decorView.setBackgroundColor(background)
        WindowCompat.getInsetsController(this, decorView).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}
