// SPDX-License-Identifier: Apache-2.0
// Copyright 2026 Soumyadip Pal

package com.offpay.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.offpay.app.ui.theme.LocalOffPayAccentTheme
import com.offpay.app.ui.theme.OffPayOnGradient

/** A cached glass lens: tinted depth, curved reflection and a double refractive edge. */
@Composable
fun Modifier.glassButtonSurface(
    shape: Shape = RoundedCornerShape(20.dp),
    enabled: Boolean = true
): Modifier {
    val accent = LocalOffPayAccentTheme.current
    val colors = MaterialTheme.colorScheme
    val face = if (enabled) {
        listOf(
            lerp(accent.headerGradientStart, Color.White, 0.18f),
            accent.headerGradientStart,
            lerp(accent.headerGradientEnd, OffPayOnGradient, 0.12f)
        )
    } else {
        listOf(colors.surfaceVariant, colors.surface, colors.surfaceVariant)
    }
    val reflection = if (enabled) 0.58f else 0.12f
    val rim = listOf(
        Color.White.copy(alpha = if (enabled) 0.96f else 0.34f),
        colors.outline.copy(alpha = 0.12f),
        Color.White.copy(alpha = if (enabled) 0.66f else 0.18f)
    )
    return shadow(
        elevation = if (enabled) 10.dp else 3.dp,
        shape = shape,
        ambientColor = accent.primary.copy(alpha = 0.14f),
        spotColor = OffPayOnGradient.copy(alpha = 0.22f)
    )
        .clip(shape)
        .background(Brush.verticalGradient(face))
        .drawWithCache {
            val inset = 3.dp.toPx()
            val innerSize = Size(
                (size.width - inset * 2).coerceAtLeast(0f),
                (size.height - inset * 2).coerceAtLeast(0f)
            )
            val innerOutline = shape.createOutline(innerSize, layoutDirection, this)
            val lens = Brush.verticalGradient(
                listOf(Color.White.copy(alpha = reflection), Color.White.copy(alpha = 0.03f)),
                endY = size.height * 0.58f
            )
            val innerRim = Brush.verticalGradient(listOf(Color.White.copy(alpha = reflection), Color.Transparent))
            onDrawBehind {
                drawOval(lens, Offset(-size.width * 0.15f, -size.height * 0.5f), Size(size.width * 1.3f, size.height))
                translate(inset, inset) {
                    drawOutline(innerOutline, innerRim, style = Stroke(width = 0.75.dp.toPx()))
                }
            }
        }
        .border(1.5.dp, Brush.linearGradient(rim), shape)
}

/** Standard click semantics and a reversible spring press; Compose observes the system animation scale. */
@Composable
fun Modifier.glassButton(
    enabled: Boolean = true,
    vibrant: Boolean = true,
    shape: Shape = RoundedCornerShape(20.dp),
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (enabled && pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.68f, stiffness = 550f),
        label = "Glass button press"
    )
    return graphicsLayer {
        scaleX = 1f - 0.035f * press
        scaleY = 1f - 0.045f * press
        translationY = 1.dp.toPx() * press
    }
        .glassButtonSurface(shape = shape, enabled = enabled && vibrant)
        .clickable(
            enabled = enabled,
            role = Role.Button,
            interactionSource = interactionSource,
            indication = LocalIndication.current,
            onClick = onClick
        )
}

/** A generous action target with a consistent glass face and readable, theme-aware content. */
@Composable
fun GlassActionButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean = true,
    vibrant: Boolean = true,
    onClick: () -> Unit
) {
    val contentColor = if (enabled && vibrant) OffPayOnGradient else MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 82.dp)
            .glassButton(enabled = enabled, vibrant = vibrant, shape = RoundedCornerShape(26.dp), onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(contentColor.copy(alpha = 0.06f), CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.35f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(25.dp))
        }
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = contentColor,
            fontSize = 16.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.SemiBold
        )
        Icon(
            Icons.Default.ArrowOutward,
            contentDescription = null,
            tint = contentColor.copy(alpha = 0.72f),
            modifier = Modifier.size(20.dp)
        )
    }
}
