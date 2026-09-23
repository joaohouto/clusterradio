package com.joaohouto.clusterradio.engine

import android.content.Context
import android.content.Intent
import android.util.Log
import com.joaohouto.clusterradio.data.model.RadioBand
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HardwareRadioEngine(private val context: Context) : RadioEngine {

    companion object {
        private const val TAG = "HardwareRadioEngine"

        // Automotive MCU Broadcast Actions
        private const val ACTION_MICRONTEK_SYNC = "com.microntek.sync"
        private const val ACTION_SYU_RADIO = "com.syu.radio"
        private const val ACTION_TS_RADIO = "com.ts.radiostation"
    }

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var scanJob: Job? = null

    private val _state = MutableStateFlow(
        RadioEngineState(
            isPlaying = false,
            isHardwareTunerActive = detectHardwareTuner()
        )
    )
    override val state: StateFlow<RadioEngineState> = _state.asStateFlow()

    private fun detectHardwareTuner(): Boolean {
        return try {
            val pm = context.packageManager
            val knownPackages = listOf("com.syu.radio", "com.microntek.radio", "com.ts.radiostation", "com.car.radio")
            knownPackages.any { pkg ->
                try {
                    pm.getPackageInfo(pkg, 0)
                    true
                } catch (_: Exception) {
                    false
                }
            }
        } catch (_: Exception) {
            false
        }
    }

    override fun tune(band: RadioBand, frequencyKhz: Int) {
        _state.update {
            it.copy(
                currentBand = band,
                currentFrequencyKhz = frequencyKhz,
                rdsStationName = null
            )
        }
        dispatchMcuTune(band, frequencyKhz)
    }

    private fun dispatchMcuTune(band: RadioBand, frequencyKhz: Int) {
        try {
            // 1. Microntek format
            val microntekIntent = Intent(ACTION_MICRONTEK_SYNC).apply {
                putExtra("type", "tuner")
                putExtra("cmd", "tune")
                putExtra("band", if (band == RadioBand.FM) 0 else 1)
                putExtra("freq", frequencyKhz)
            }
            context.sendBroadcast(microntekIntent)

            // 2. Syu / FYT format
            val syuIntent = Intent(ACTION_SYU_RADIO).apply {
                putExtra("cmd", "tune")
                putExtra("band", band.id)
                putExtra("freq", frequencyKhz)
            }
            context.sendBroadcast(syuIntent)

            // 3. Topway format
            val tsIntent = Intent(ACTION_TS_RADIO).apply {
                putExtra("command", "set_freq")
                putExtra("freq", frequencyKhz)
            }
            context.sendBroadcast(tsIntent)

            Log.d(TAG, "Dispatched hardware MCU tune: ${band.id} $frequencyKhz kHz")
        } catch (e: Exception) {
            Log.w(TAG, "Hardware MCU dispatch failed: ${e.message}")
        }
    }

    override fun play() {
        _state.update { it.copy(isPlaying = true, isMuted = false) }
        try {
            val intent = Intent(ACTION_MICRONTEK_SYNC).apply {
                putExtra("type", "tuner")
                putExtra("cmd", "play")
            }
            context.sendBroadcast(intent)
        } catch (_: Exception) {}
    }

    override fun pause() {
        _state.update { it.copy(isPlaying = false) }
        try {
            val intent = Intent(ACTION_MICRONTEK_SYNC).apply {
                putExtra("type", "tuner")
                putExtra("cmd", "pause")
            }
            context.sendBroadcast(intent)
        } catch (_: Exception) {}
    }

    override fun mute(muted: Boolean) {
        _state.update { it.copy(isMuted = muted) }
    }

    override fun startScan(scanUp: Boolean, onFound: (Int) -> Unit) {
        scanJob?.cancel()
        _state.update { it.copy(isScanning = true) }
        scanJob = scope.launch {
            val current = _state.value.currentFrequencyKhz
            val band = _state.value.currentBand
            var nextFreq = current
            // Simulated / MCU stepped seek
            repeat(15) {
                delay(120)
                nextFreq = band.stepFrequency(nextFreq, scanUp)
                _state.update { it.copy(currentFrequencyKhz = nextFreq) }
                dispatchMcuTune(band, nextFreq)
            }
            _state.update { it.copy(isScanning = false) }
            onFound(nextFreq)
        }
    }

    override fun stopScan() {
        scanJob?.cancel()
        _state.update { it.copy(isScanning = false) }
    }

    override fun setVolumeGain(gain: Float) {
        // Handled by master audio manager
    }

    override fun release() {
        scanJob?.cancel()
    }
}
