package com.joaohouto.clusterradio.engine

import com.joaohouto.clusterradio.data.model.RadioBand
import kotlinx.coroutines.flow.StateFlow

data class RadioEngineState(
    val isPlaying: Boolean = false,
    val isMuted: Boolean = false,
    val currentBand: RadioBand = RadioBand.FM,
    val currentFrequencyKhz: Int = RadioBand.FM.defaultFrequencyKhz,
    val isScanning: Boolean = false,
    val isHardwareTunerActive: Boolean = false,
    val rdsStationName: String? = null,
    val radioText: String? = null
)

interface RadioEngine {
    val state: StateFlow<RadioEngineState>
    fun tune(band: RadioBand, frequencyKhz: Int)
    fun play()
    fun pause()
    fun mute(muted: Boolean)
    fun startScan(scanUp: Boolean, onFound: (Int) -> Unit)
    fun stopScan()
    fun setVolumeGain(gain: Float)
    fun release()
}
