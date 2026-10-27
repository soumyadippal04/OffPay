// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Soumyadip Pal

package com.offpay.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.offpay.app.ui.theme.LocalOffPayAccentTheme

/** Layered highlights give buttons a glass finish without an expensive background blur. */
@Composable
fun Modifier.glassButtonSurface(
    shape: Shape = RoundedCornerShape(16.dp),
    enabled: Boolean = true
): Modifier {
    val accent = LocalOffPayAccentTheme.current
    val colors = MaterialTheme.colorScheme
    val faceColors = if (enabled) {
        listOf(accent.headerGradientStart, accent.headerGradientEnd)
    } else {
        listOf(colors.surfaceVariant, colors.surface)
    }
    val highlight = if (enabled) 0.34f else 0.04f
    return shadow(
        elevation = if (enabled) 6.dp else 0.dp,
        shape = shape,
        ambientColor = accent.primary.copy(alpha = 0.10f),
        spotColor = accent.primary.copy(alpha = 0.16f)
    )
        .clip(shape)
        .background(Brush.linearGradient(faceColors))
        .background(
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = highlight), Color.Transparent, Color.White.copy(alpha = 0.06f))
            )
        )
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = if (enabled) 0.65f else 0.08f),
                    colors.outline.copy(alpha = 0.16f),
                    Color.White.copy(alpha = if (enabled) 0.26f else 0.04f)
                )
            ),
            shape = shape
        )
}
