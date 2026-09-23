package com.joaohouto.clusterradio.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeDown
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.joaohouto.clusterradio.R

@Composable
fun RadioControlRow(
    isPlaying: Boolean,
    onSeekDown: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekUp: () -> Unit,
    onVolumeDown: () -> Unit,
    onVolumeUp: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Automotive Touch Buttons Row (Height 84dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(84.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Diminuir Volume (-) - Primeiro da linha
        MetallicButton(
            onClick = onVolumeDown,
            icon = Icons.AutoMirrored.Rounded.VolumeDown,
            minSize = 56.dp,
            iconSize = 34.dp,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentDescription = stringResource(R.string.desc_volume_down)
        )

        // 2. Estação Anterior (Busca para trás)
        MetallicButton(
            onClick = onSeekDown,
            icon = Icons.Rounded.SkipPrevious,
            minSize = 56.dp,
            iconSize = 36.dp,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentDescription = stringResource(R.string.desc_previous_station)
        )

        // 3. Play / Pause (Centralizado em Destaque Accent)
        MetallicButton(
            onClick = onPlayPause,
            style = MetallicButtonStyle.Accent,
            icon = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            minSize = 64.dp,
            iconSize = 40.dp,
            modifier = Modifier
                .weight(1.35f)
                .fillMaxHeight(),
            contentDescription = if (isPlaying) stringResource(R.string.desc_pause) else stringResource(R.string.desc_play)
        )

        // 4. Próxima Estação (Busca para frente)
        MetallicButton(
            onClick = onSeekUp,
            icon = Icons.Rounded.SkipNext,
            minSize = 56.dp,
            iconSize = 36.dp,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentDescription = stringResource(R.string.desc_next_station)
        )

        // 5. Aumentar Volume (+)
        MetallicButton(
            onClick = onVolumeUp,
            icon = Icons.AutoMirrored.Rounded.VolumeUp,
            minSize = 56.dp,
            iconSize = 34.dp,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentDescription = stringResource(R.string.desc_volume_up)
        )
    }
}
