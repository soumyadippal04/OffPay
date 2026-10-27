// SPDX-License-Identifier: Apache-2.0
package com.offpay.app.ui.theme

import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ThemeContrastTest {
    @Test
    fun `both modes have readable body text and secondary labels`() {
        for (scheme in listOf(OffPayDarkColors, OffPayLightColors)) {
            for (background in listOf(scheme.background, scheme.surface, scheme.surfaceVariant)) {
                for (text in listOf(scheme.onSurface, scheme.onSurfaceVariant)) {
                    assertTrue(ColorUtils.calculateContrast(text.toArgb(), background.toArgb()) >= 4.5)
                }
            }
        }
    }

    @Test
    fun `gradient text and light mode controls have sufficient contrast`() {
        for (background in listOf(BlueAccentTheme.headerGradientStart, BlueAccentTheme.headerGradientEnd)) {
            assertTrue(ColorUtils.calculateContrast(OffPayOnGradient.toArgb(), background.toArgb()) >= 4.5)
        }
        assertTrue(
            ColorUtils.calculateContrast(OffPayLightColors.primary.toArgb(), OffPayLightColors.surface.toArgb()) >= 4.5
        )
        assertTrue(
            ColorUtils.calculateContrast(OffPayLightColors.tertiary.toArgb(), OffPayLightColors.surface.toArgb()) >= 4.5
        )
    }
}
