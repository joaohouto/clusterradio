package com.joaohouto.clusterradio.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.joaohouto.clusterradio.data.model.RadioBand
import com.joaohouto.clusterradio.ui.theme.LocalClusterAccent
import com.joaohouto.clusterradio.ui.theme.MetallicIntermediate
import com.joaohouto.clusterradio.ui.theme.SurfaceCard
import com.joaohouto.clusterradio.ui.theme.SurfaceCardBorder
import com.joaohouto.clusterradio.ui.theme.TrackRail

@Composable
fun FrequencyRulerSlider(
    currentFrequencyKhz: Int,
    band: RadioBand,
    onFrequencyChanged: (Int) -> Unit,
    onStep: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalClusterAccent.current
    var isDragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }

    val minFreq = band.minFrequencyKhz
    val maxFreq = band.maxFrequencyKhz
    val span = (maxFreq - minFreq).toFloat()

    val currentFraction = if (isDragging) {
        dragFraction
    } else {
        ((currentFrequencyKhz - minFreq) / span).coerceIn(0f, 1f)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Nudge Step Left (<)
        MetallicButton(
            onClick = { onStep(false) },
            icon = Icons.Rounded.ChevronLeft,
            modifier = Modifier
                .fillMaxHeight()
                .width(64.dp),
            iconSize = 32.dp,
            contentDescription = "Fine Step Down"
        )

        // Ruler Dial Box
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(SurfaceCard, RoundedCornerShape(12.dp))
                .border(1.dp, SurfaceCardBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .pointerInput(band) {
                        detectTapGestures { offset ->
                            val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            val targetKhz = (minFreq + (fraction * span)).toInt()
                            val steppedKhz = roundToStep(targetKhz, band)
                            onFrequencyChanged(steppedKhz)
                        }
                    }
                    .pointerInput(band) {
                        detectHorizontalDragGestures(
                            onDragStart = { offset ->
                                isDragging = true
                                dragFraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            },
                            onDragEnd = {
                                val targetKhz = (minFreq + (dragFraction * span)).toInt()
                                val steppedKhz = roundToStep(targetKhz, band)
                                onFrequencyChanged(steppedKhz)
                                isDragging = false
                            },
                            onDragCancel = {
                                isDragging = false
                            },
                            onHorizontalDrag = { change, _ ->
                                dragFraction = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                val targetKhz = (minFreq + (dragFraction * span)).toInt()
                                onFrequencyChanged(roundToStep(targetKhz, band))
                            }
                        )
                    }
            ) {
                val width = size.width
                val height = size.height
                val centerY = height / 2f

                // Horizontal Rail Track
                val railHeight = 4.dp.toPx()
                val railTop = centerY - (railHeight / 2f)
                drawRoundRect(
                    color = TrackRail,
                    topLeft = Offset(0f, railTop),
                    size = Size(width, railHeight),
                    cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
                )

                // Graduation Ticks
                val tickCount = if (band == RadioBand.FM) 41 else 30
                for (i in 0..tickCount) {
                    val x = (i.toFloat() / tickCount) * width
                    val isMajor = (i % 5 == 0)
                    val tickHeight = if (isMajor) 16.dp.toPx() else 8.dp.toPx()
                    val tickColor = if (isMajor) MetallicIntermediate else Color(0xFF1E2026)

                    drawLine(
                        color = tickColor,
                        start = Offset(x, centerY - (tickHeight / 2f)),
                        end = Offset(x, centerY + (tickHeight / 2f)),
                        strokeWidth = if (isMajor) 2.dp.toPx() else 1.dp.toPx()
                    )
                }

                // Needle Indicator
                val needleX = (width * currentFraction).coerceIn(4.dp.toPx(), width - 4.dp.toPx())
                val needleWidth = if (isDragging) 4.dp.toPx() else 3.dp.toPx()

                // Needle Ambient Glow (illuminated instrument needle effect)
                drawRoundRect(
                    color = accent.glow,
                    topLeft = Offset(needleX - 4.dp.toPx(), 4.dp.toPx()),
                    size = Size(8.dp.toPx(), height - 8.dp.toPx()),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )

                // Vertical Illuminated Needle Line (clean automotive gauge pointer)
                drawLine(
                    color = accent.primary,
                    start = Offset(needleX, 6.dp.toPx()),
                    end = Offset(needleX, height - 6.dp.toPx()),
                    strokeWidth = needleWidth,
                    cap = StrokeCap.Round
                )
            }
        }

        // Nudge Step Right (>)
        MetallicButton(
            onClick = { onStep(true) },
            icon = Icons.Rounded.ChevronRight,
            modifier = Modifier
                .fillMaxHeight()
                .width(64.dp),
            iconSize = 32.dp,
            contentDescription = "Fine Step Up"
        )
    }
}

private fun roundToStep(khz: Int, band: RadioBand): Int {
    val step = band.defaultStepKhz
    val remainder = khz % step
    val rounded = if (remainder >= step / 2) khz + (step - remainder) else khz - remainder
    return rounded.coerceIn(band.minFrequencyKhz, band.maxFrequencyKhz)
}
