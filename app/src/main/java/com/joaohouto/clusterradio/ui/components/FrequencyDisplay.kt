package com.joaohouto.clusterradio.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joaohouto.clusterradio.data.model.RadioBand
import com.joaohouto.clusterradio.ui.theme.LocalClusterAccent
import com.joaohouto.clusterradio.ui.theme.SurfaceCard
import com.joaohouto.clusterradio.ui.theme.SurfaceCardBorder
import com.joaohouto.clusterradio.ui.theme.TextPrimary
import com.joaohouto.clusterradio.ui.theme.TextSecondary

@Composable
fun FrequencyDisplay(
    frequencyText: String,
    unitText: String,
    band: RadioBand,
    stationName: String?,
    radioText: String?,
    isScanning: Boolean,
    onSelectBand: (RadioBand) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalClusterAccent.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(SurfaceCard, Color(0xFF0F1013))
                ),
                shape = RoundedCornerShape(16.dp)
            )
            .border(1.dp, SurfaceCardBorder, RoundedCornerShape(16.dp))
            .padding(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 40.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Top Row of Display: Left = Band Selector Pill [ FM | AM ], Right = Station Name or Search Animation
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Segmented Pill Buttons for FM and AM with adjusted height and touch area
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetallicButton(
                        onClick = { onSelectBand(RadioBand.FM) },
                        text = "FM",
                        isActive = band == RadioBand.FM,
                        minSize = 46.dp,
                        textSize = 15.sp,
                        modifier = Modifier
                            .width(68.dp)
                            .height(46.dp)
                    )

                    MetallicButton(
                        onClick = { onSelectBand(RadioBand.AM) },
                        text = "AM",
                        isActive = band == RadioBand.AM,
                        minSize = 46.dp,
                        textSize = 15.sp,
                        modifier = Modifier
                            .width(68.dp)
                            .height(46.dp)
                    )
                }

                // Station / RDS Name or Scanning Radar Animation
                if (isScanning) {
                    ScanningAnimation()
                } else {
                    val statusText = if (!stationName.isNullOrBlank()) stationName else "${band.displayName} STEREO"
                    Text(
                        text = statusText,
                        color = if (!stationName.isNullOrBlank()) accent.primary else TextSecondary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Main Digital Cluster Frequency Numbers
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = frequencyText,
                    color = TextPrimary,
                    fontSize = 68.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.SansSerif
                )

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = unitText,
                    color = accent.primary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }

            // Radio Text (RDS RT - live track title / artist / slogan if available)
            if (!radioText.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = radioText,
                    color = TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun ScanningAnimation(modifier: Modifier = Modifier) {
    val accent = LocalClusterAccent.current
    val infiniteTransition = rememberInfiniteTransition(label = "RadarScanner")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.25f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val delays = listOf(0, 110, 220, 330, 440)
    val barFractions = delays.mapIndexed { index, delay ->
        infiniteTransition.animateFloat(
            initialValue = 0.25f,
            targetValue = 1.0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 350, delayMillis = delay, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "bar_$index"
        )
    }

    Row(
        modifier = modifier
            .background(
                color = Color(0xFF14161A),
                shape = RoundedCornerShape(20.dp)
            )
            .border(1.dp, accent.primary.copy(alpha = 0.35f), RoundedCornerShape(20.dp))
            .padding(horizontal = 14.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Pulsing radar beacon dot
        Box(
            modifier = Modifier
                .size(9.dp)
                .graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                    alpha = pulseAlpha
                }
                .clip(CircleShape)
                .background(accent.primary)
        )

        // 5-bar animated audio search spectrum
        Row(
            modifier = Modifier.height(20.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            barFractions.forEach { fraction ->
                Box(
                    modifier = Modifier
                        .width(4.5.dp)
                        .height((20 * fraction.value).dp.coerceAtLeast(4.dp))
                        .clip(RoundedCornerShape(2.dp))
                        .background(accent.primary)
                )
            }
        }
    }
}
