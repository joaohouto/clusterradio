package com.joaohouto.clusterradio.ui.radio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joaohouto.clusterradio.R
import com.joaohouto.clusterradio.data.model.RadioBand
import com.joaohouto.clusterradio.ui.components.FavoritesGrid
import com.joaohouto.clusterradio.ui.components.FrequencyDisplay
import com.joaohouto.clusterradio.ui.components.FrequencyRulerSlider
import com.joaohouto.clusterradio.ui.components.MetallicButton
import com.joaohouto.clusterradio.ui.components.MetallicButtonStyle
import com.joaohouto.clusterradio.ui.components.RadioControlRow
import com.joaohouto.clusterradio.ui.settings.RadioSettingsDialog
import com.joaohouto.clusterradio.ui.theme.DeepMetallicBackground
import com.joaohouto.clusterradio.ui.theme.LocalClusterAccent
import com.joaohouto.clusterradio.ui.theme.TextSecondary

@Composable
fun RadioScreen(
    viewModel: RadioViewModel,
    modifier: Modifier = Modifier
) {
    val state by viewModel.playbackState.collectAsState()
    val presets by viewModel.presets.collectAsState()
    val settings by viewModel.settings.collectAsState()
    val currentAccent = LocalClusterAccent.current

    var showSettingsDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(DeepMetallicBackground)
            .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Brand Title
            Text(
                text = stringResource(R.string.header_cluster_radio),
                color = TextSecondary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )

            // Settings Button
            MetallicButton(
                onClick = { showSettingsDialog = true },
                icon = Icons.Rounded.Settings,
                minSize = 44.dp,
                iconSize = 24.dp,
                contentDescription = stringResource(R.string.desc_settings)
            )
        }

        // Section 1: Frequency Display & Tuning Ruler Slider
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FrequencyDisplay(
                frequencyText = state.displayFrequency,
                unitText = state.unitLabel,
                band = state.currentBand,
                stationName = state.stationName,
                radioText = state.radioText,
                isScanning = state.isScanning,
                isHardwareActive = state.isHardwareActive,
                onSelectBand = { band -> viewModel.setBand(band) }
            )

            FrequencyRulerSlider(
                currentFrequencyKhz = state.currentFrequencyKhz,
                band = state.currentBand,
                onFrequencyChanged = { freq -> viewModel.tuneFrequency(freq) },
                onStep = { stepUp -> viewModel.stepFrequency(stepUp) }
            )
        }

        // Section 2: Favorites Grid (P1..P6)
        FavoritesGrid(
            presets = presets,
            currentFrequencyKhz = state.currentFrequencyKhz,
            currentBand = state.currentBand,
            onTunePreset = { preset -> viewModel.tunePreset(preset) },
            onSavePreset = { slot -> viewModel.savePreset(slot) }
        )

        // Section 3: Bottom Control Row (Volume -, Seek Down, Play/Pause, Seek Up, Volume +)
        RadioControlRow(
            isPlaying = state.isPlaying,
            onSeekDown = { viewModel.startScan(scanUp = false) },
            onPlayPause = { viewModel.togglePlayPause() },
            onSeekUp = { viewModel.startScan(scanUp = true) },
            onVolumeDown = { viewModel.adjustVolume(raise = false) },
            onVolumeUp = { viewModel.adjustVolume(raise = true) }
        )
    }

    if (showSettingsDialog) {
        RadioSettingsDialog(
            currentAccentTheme = currentAccent,
            currentVolumeGainPercent = settings.masterVolumePercent,
            isKeepScreenOn = settings.keepScreenOn,
            isHardwareActive = state.isHardwareActive,
            onSelectAccent = { themeId -> viewModel.saveAccentTheme(themeId) },
            onSelectVolumeGain = { gain -> viewModel.saveMasterVolume(gain) },
            onToggleKeepScreenOn = { enabled -> viewModel.saveKeepScreenOn(enabled) },
            onDismiss = { showSettingsDialog = false }
        )
    }
}
