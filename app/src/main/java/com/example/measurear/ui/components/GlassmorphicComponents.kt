package com.example.measurear.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.measurear.ui.theme.ArGreen
import com.example.measurear.ui.theme.ArYellow
import com.example.measurear.ui.theme.GlassBorderLight
import com.example.measurear.ui.theme.GlassSurfaceDark
import com.example.measurear.ui.theme.TabularMeasureTextStyle

@Composable
fun GlassmorphicCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 24.dp,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable BoxScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Surface(
        modifier = modifier
            .clip(shape)
            .border(
                BorderStroke(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.28f),
                            Color.White.copy(alpha = 0.08f)
                        )
                    )
                ),
                shape = shape
            ),
        color = GlassSurfaceDark,
        shape = shape
    ) {
        Box(modifier = Modifier.padding(contentPadding), content = content)
    }
}

@Composable
fun StatusInstructionPill(
    text: String,
    isWarning: Boolean,
    modifier: Modifier = Modifier
) {
    val borderColor by animateColorAsState(
        targetValue = if (isWarning) ArYellow.copy(alpha = 0.6f) else GlassBorderLight,
        label = "statusBorder"
    )
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, borderColor, RoundedCornerShape(50)),
        color = Color(0xCC121216),
        shape = RoundedCornerShape(50)
    ) {
        Text(
            text = text,
            color = if (isWarning) ArYellow else Color.White,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
        )
    }
}

@Composable
fun DistanceBadgePill(
    text: String,
    isLive: Boolean,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isLive) Color(0xE618181C) else ArYellow
    val textColor = if (isLive) Color.White else Color.Black

    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .border(
                width = 1.dp,
                color = if (isLive) ArYellow.copy(alpha = 0.7f) else Color.White.copy(alpha = 0.4f),
                shape = RoundedCornerShape(50)
            ),
        color = bgColor,
        shape = RoundedCornerShape(50),
        shadowElevation = 6.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = text,
                style = TabularMeasureTextStyle.copy(
                    color = textColor,
                    fontSize = 15.sp
                )
            )
        }
    }
}

@Composable
fun AnimatedCenterReticle(
    isLockedOnSurface: Boolean,
    isPointReady: Boolean,
    modifier: Modifier = Modifier
) {
    val reticleColor by animateColorAsState(
        targetValue = when {
            isLockedOnSurface -> ArGreen
            isPointReady -> ArYellow
            else -> Color.White.copy(alpha = 0.75f)
        },
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "reticleColor"
    )

    val scale by animateFloatAsState(
        targetValue = if (isLockedOnSurface) 1.0f else 1.24f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "reticleScale"
    )

    Canvas(
        modifier = modifier
            .size(64.dp)
            .scale(scale)
            .semantics {
                contentDescription = if (isLockedOnSurface) {
                    "Center reticle locked onto surface"
                } else {
                    "Center reticle searching for surface"
                }
            }
    ) {
        val radius = size.minDimension / 2.4f
        // Outer translucent ring
        drawCircle(
            color = reticleColor.copy(alpha = 0.22f),
            radius = radius
        )
        // Crisp ring border
        drawCircle(
            color = reticleColor,
            radius = radius,
            style = Stroke(width = 2.5.dp.toPx())
        )
        // Crosshair ticks
        val tickLen = 5.dp.toPx()
        val cx = center.x
        val cy = center.y
        drawLine(
            color = reticleColor,
            start = Offset(cx - radius - tickLen, cy),
            end = Offset(cx - radius + tickLen, cy),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            color = reticleColor,
            start = Offset(cx + radius - tickLen, cy),
            end = Offset(cx + radius + tickLen, cy),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round
        )
        // Center dot
        drawCircle(
            color = Color.White,
            radius = 3.5.dp.toPx()
        )
    }
}

@Composable
fun ShutterAddPointButton(
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = when {
            !enabled -> 0.9f
            isPressed -> 0.88f
            else -> 1.0f
        },
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "shutterScale"
    )

    Box(
        modifier = modifier
            .size(78.dp)
            .scale(scale)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = if (enabled) 0.22f else 0.08f))
            .border(
                width = 3.dp,
                color = if (enabled) Color.White else Color.White.copy(alpha = 0.3f),
                shape = CircleShape
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick
            )
            .semantics {
                contentDescription = "Add measurement point at center reticle"
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .background(if (enabled) Color.White else Color.White.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.size(34.dp)
            )
        }
    }
}
