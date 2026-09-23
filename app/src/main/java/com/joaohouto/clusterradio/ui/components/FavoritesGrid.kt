package com.joaohouto.clusterradio.ui.components

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ripple
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joaohouto.clusterradio.R
import com.joaohouto.clusterradio.data.model.RadioBand
import com.joaohouto.clusterradio.data.model.RadioPreset
import com.joaohouto.clusterradio.ui.theme.LocalClusterAccent
import com.joaohouto.clusterradio.ui.theme.MetallicIntermediate
import com.joaohouto.clusterradio.ui.theme.SurfaceCard
import com.joaohouto.clusterradio.ui.theme.SurfaceCardBorder
import com.joaohouto.clusterradio.ui.theme.TextPrimary
import com.joaohouto.clusterradio.ui.theme.TextSecondary

private val PresetButtonShape = RoundedCornerShape(12.dp)

@Composable
fun FavoritesGrid(
    presets: List<RadioPreset>,
    currentFrequencyKhz: Int,
    currentBand: RadioBand,
    onTunePreset: (RadioPreset) -> Unit,
    onSavePreset: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current

    // Display 6 presets in 2 rows of 3 columns
    val row1 = presets.take(3)
    val row2 = presets.drop(3).take(3)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            row1.forEach { preset ->
                PresetCard(
                    preset = preset,
                    isActive = preset.frequencyKhz == currentFrequencyKhz,
                    onClick = { onTunePreset(preset) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSavePreset(preset.slot)
                        val freqText = currentBand.formatFrequency(currentFrequencyKhz)
                        Toast.makeText(
                            context,
                            context.getString(R.string.preset_saved_toast, freqText, preset.slot),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            row2.forEach { preset ->
                PresetCard(
                    preset = preset,
                    isActive = preset.frequencyKhz == currentFrequencyKhz,
                    onClick = { onTunePreset(preset) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onSavePreset(preset.slot)
                        val freqText = currentBand.formatFrequency(currentFrequencyKhz)
                        Toast.makeText(
                            context,
                            context.getString(R.string.preset_saved_toast, freqText, preset.slot),
                            Toast.LENGTH_SHORT
                        ).show()
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PresetCard(
    preset: RadioPreset,
    isActive: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = LocalClusterAccent.current

    val backgroundBrush = remember(isActive, accent.primary) {
        if (isActive) {
            Brush.verticalGradient(listOf(MetallicIntermediate, SurfaceCard))
        } else {
            Brush.verticalGradient(listOf(SurfaceCard, Color(0xFF101215)))
        }
    }

    val borderStroke = remember(isActive, accent.primary) {
        if (isActive) {
            BorderStroke(1.5.dp, accent.primary)
        } else {
            BorderStroke(1.dp, SurfaceCardBorder)
        }
    }

    Surface(
        modifier = modifier
            .height(78.dp)
            .clip(PresetButtonShape)
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = accent.primary),
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = PresetButtonShape,
        border = borderStroke,
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .background(backgroundBrush)
                .padding(horizontal = 14.dp, vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // Slot Label (P1..P6)
                Text(
                    text = "P${preset.slot}",
                    color = if (isActive) accent.primary else TextSecondary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Frequency or Station Name
                Text(
                    text = preset.displayFrequency,
                    color = if (isActive) accent.primary else TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
