package com.example.saqr.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.saqr.model.PerformanceTier
import com.example.saqr.ui.theme.SaqrColors

@Composable
fun FloatingGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    borderGradient: Brush = SaqrColors.GlassCardBorderGradient,
    backgroundColor: Color = SaqrColors.SurfaceGlass,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.985f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "cardScale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(shape)
            .background(backgroundColor)
            .border(BorderStroke(1.dp, borderGradient), shape)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            )
            .padding(16.dp),
        content = content
    )
}

@Composable
fun AmbientReactiveMeshBackground(
    tier: PerformanceTier,
    modifier: Modifier = Modifier
) {
    if (!tier.enableAmbientParticles && tier == PerformanceTier.LOW) {
        // Fallback lightweight gradient background for thermal/low-tier devices
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(SaqrColors.VoidBlack, SaqrColors.DarkCosmic, SaqrColors.SurfaceDark)
                    )
                )
        )
        return
    }

    val infiniteTransition = rememberInfiniteTransition(label = "ambientMesh")
    val animOffset1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 18000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "animOffset1"
    )

    val animPulse by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "animPulse"
    )

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(SaqrColors.VoidBlack)
    ) {
        val width = size.width
        val height = size.height

        // Ambient Cyan Glow Blob
        val radX1 = (width * 0.35f) + (kotlin.math.sin(Math.toRadians(animOffset1.toDouble())).toFloat() * 120f)
        val radY1 = (height * 0.25f) + (kotlin.math.cos(Math.toRadians(animOffset1.toDouble())).toFloat() * 90f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    SaqrColors.ElectricCyan.copy(alpha = 0.22f * animPulse),
                    SaqrColors.ElectricCyan.copy(alpha = 0.06f),
                    Color.Transparent
                ),
                center = Offset(radX1, radY1),
                radius = width * 0.75f * animPulse
            )
        )

        // Ambient Violet Glow Blob
        val radX2 = (width * 0.7f) - (kotlin.math.cos(Math.toRadians(animOffset1.toDouble())).toFloat() * 100f)
        val radY2 = (height * 0.7f) + (kotlin.math.sin(Math.toRadians(animOffset1.toDouble())).toFloat() * 110f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    SaqrColors.PlasmaViolet.copy(alpha = 0.20f * animPulse),
                    SaqrColors.PlasmaViolet.copy(alpha = 0.05f),
                    Color.Transparent
                ),
                center = Offset(radX2, radY2),
                radius = width * 0.85f * animPulse
            )
        )

        // Emerald matrix faint accent in the center bottom
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    SaqrColors.EmeraldPulse.copy(alpha = 0.12f),
                    Color.Transparent
                ),
                center = Offset(width * 0.5f, height * 0.95f),
                radius = width * 0.5f
            )
        )
    }
}

@Composable
fun GlowingBadge(
    text: String,
    glowColor: Color = SaqrColors.ElectricCyan,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "badgePulse")
    val alphaAnim by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "badgeAlpha"
    )

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(glowColor.copy(alpha = 0.15f))
            .border(BorderStroke(1.dp, glowColor.copy(alpha = 0.4f * alphaAnim)), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(glowColor.copy(alpha = alphaAnim))
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            color = SaqrColors.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun LiveMetricBar(
    progress: Float,
    barColor: Color = SaqrColors.ElectricCyan,
    height: Dp = 8.dp,
    modifier: Modifier = Modifier
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "metricProgress"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(Color(0x30FFFFFF))
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(animatedProgress)
                .clip(CircleShape)
                .background(
                    Brush.horizontalGradient(
                        listOf(barColor.copy(alpha = 0.7f), barColor)
                    )
                )
        )
    }
}

@Composable
fun InteractiveGlassButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    glowColor: Color = SaqrColors.ElectricCyan,
    icon: (@Composable () -> Unit)? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "btnScale"
    )

    Box(
        modifier = modifier
            .scale(scale)
            .clip(RoundedCornerShape(14.dp))
            .background(
                Brush.horizontalGradient(
                    listOf(
                        glowColor.copy(alpha = 0.25f),
                        SaqrColors.PlasmaViolet.copy(alpha = 0.25f)
                    )
                )
            )
            .border(
                BorderStroke(1.dp, Brush.horizontalGradient(listOf(glowColor, SaqrColors.PlasmaViolet))),
                RoundedCornerShape(14.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                icon()
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = text,
                color = SaqrColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
