package com.joaohouto.clusterradio.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joaohouto.clusterradio.ui.theme.LocalClusterAccent
import com.joaohouto.clusterradio.ui.theme.MetallicIntermediate
import com.joaohouto.clusterradio.ui.theme.SurfaceCard
import com.joaohouto.clusterradio.ui.theme.SurfaceCardBorder
import com.joaohouto.clusterradio.ui.theme.TextPrimary

enum class MetallicButtonStyle {
    Standard,
    Accent,
    Outlined
}

private val ButtonShape = RoundedCornerShape(12.dp)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MetallicButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    style: MetallicButtonStyle = MetallicButtonStyle.Standard,
    icon: ImageVector? = null,
    text: String? = null,
    isActive: Boolean = false,
    minSize: Dp = 60.dp,
    iconSize: Dp = 28.dp,
    textSize: TextUnit = 16.sp,
    contentDescription: String? = null
) {
    val accent = LocalClusterAccent.current

    val backgroundBrush = remember(style, isActive, accent) {
        when (style) {
            MetallicButtonStyle.Accent -> Brush.verticalGradient(
                colors = listOf(accent.primary, accent.dark)
            )
            MetallicButtonStyle.Standard -> if (isActive) {
                Brush.verticalGradient(listOf(MetallicIntermediate, SurfaceCard))
            } else {
                Brush.verticalGradient(listOf(SurfaceCard, Color(0xFF101215)))
            }
            MetallicButtonStyle.Outlined -> Brush.verticalGradient(
                colors = listOf(SurfaceCard, SurfaceCard)
            )
        }
    }

    val borderStroke = remember(style, isActive, accent.primary) {
        when {
            isActive -> BorderStroke(1.5.dp, accent.primary)
            style == MetallicButtonStyle.Accent -> BorderStroke(1.dp, accent.primary.copy(alpha = 0.8f))
            else -> BorderStroke(1.dp, SurfaceCardBorder)
        }
    }

    val contentColor = when {
        isActive -> accent.primary
        style == MetallicButtonStyle.Accent -> TextPrimary
        else -> TextPrimary
    }

    val interactionSource = remember { MutableInteractionSource() }
    val rippleIndication = ripple(color = if (style == MetallicButtonStyle.Accent) Color.White else accent.primary)

    val clickableModifier = if (onLongClick != null) {
        Modifier.combinedClickable(
            interactionSource = interactionSource,
            indication = rippleIndication,
            onClick = onClick,
            onLongClick = onLongClick
        )
    } else {
        Modifier.clickable(
            interactionSource = interactionSource,
            indication = rippleIndication,
            onClick = onClick
        )
    }

    Surface(
        modifier = modifier
            .defaultMinSize(minWidth = minSize, minHeight = minSize)
            .clip(ButtonShape)
            .then(clickableModifier),
        shape = ButtonShape,
        border = borderStroke,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .background(backgroundBrush)
                .defaultMinSize(minWidth = minSize, minHeight = minSize)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = contentDescription ?: text,
                        tint = contentColor,
                        modifier = Modifier.size(iconSize)
                    )
                }
                if (icon != null && !text.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.width(8.dp))
                }
                if (!text.isNullOrEmpty()) {
                    Text(
                        text = text,
                        color = contentColor,
                        fontSize = textSize,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        style = androidx.compose.ui.text.TextStyle(
                            platformStyle = androidx.compose.ui.text.PlatformTextStyle(
                                includeFontPadding = false
                            ),
                            lineHeightStyle = androidx.compose.ui.text.style.LineHeightStyle(
                                alignment = androidx.compose.ui.text.style.LineHeightStyle.Alignment.Center,
                                trim = androidx.compose.ui.text.style.LineHeightStyle.Trim.Both
                            )
                        )
                    )
                }
            }
        }
    }
}
