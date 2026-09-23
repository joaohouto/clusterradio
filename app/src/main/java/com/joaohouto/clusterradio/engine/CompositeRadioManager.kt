package com.joaohouto.clusterradio.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.joaohouto.clusterradio.data.model.RadioBand
import com.joaohouto.clusterradio.service.RadioPlaybackService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RadioPlaybackState(
    val isPlaying: Boolean = false,
    val isMuted: Boolean = false,
    val currentBand: RadioBand = RadioBand.FM,
    val currentFrequencyKhz: Int = RadioBand.FM.defaultFrequencyKhz,
    val stationName: String? = null,
    val radioText: String? = null,
    val isScanning: Boolean = false,
    val isHardwareActive: Boolean = false,
    val streamVolumePercent: Int = 100,
    val masterGainPercent: Int = 100
) {
    val displayFrequency: String
        get() = currentBand.formatFrequency(currentFrequencyKhz)

    val unitLabel: String
        get() = currentBand.unitLabel
}

class CompositeRadioManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "CompositeRadioManager"

        @Volatile
        private var INSTANCE: CompositeRadioManager? = null

        fun getInstance(context: Context): CompositeRadioManager {
            return INSTANCE ?: synchronized(this) {
                val instance = CompositeRadioManager(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val hardwareEngine = HardwareRadioEngine(context)
    private val streamEngine = AudioStreamRadioEngine(context)

    private val _playbackState = MutableStateFlow(RadioPlaybackState())
    val playbackState: StateFlow<RadioPlaybackState> = _playbackState.asStateFlow()

    private var audioFocusRequest: AudioFocusRequest? = null
    private var hasAudioFocus = false

    init {
        updateStreamVolumeState()
        observeEngines()
    }

    private fun observeEngines() {
        scope.launch {
            hardwareEngine.state.collect { hwState ->
                if (hwState.isHardwareTunerActive) {
                    _playbackState.update {
                        it.copy(
                            isHardwareActive = true,
                            isScanning = hwState.isScanning
                        )
                    }
                }
            }
        }

        scope.launch {
            streamEngine.state.collect { stState ->
                if (!_playbackState.value.isHardwareActive) {
                    _playbackState.update {
                        it.copy(
                            isScanning = stState.isScanning,
                            stationName = stState.rdsStationName,
                            radioText = stState.radioText
                        )
                    }
                }
            }
        }
    }

    fun tune(band: RadioBand, frequencyKhz: Int) {
        _playbackState.update {
            it.copy(
                currentBand = band,
                currentFrequencyKhz = frequencyKhz,
                stationName = null,
                radioText = null
            )
        }
        hardwareEngine.tune(band, frequencyKhz)
        streamEngine.tune(band, frequencyKhz)
    }

    fun step(stepUp: Boolean) {
        val current = _playbackState.value.currentFrequencyKhz
        val band = _playbackState.value.currentBand
        val next = band.stepFrequency(current, stepUp)
        tune(band, next)
    }

    fun play() {
        requestAudioFocus()
        _playbackState.update { it.copy(isPlaying = true, isMuted = false) }
        hardwareEngine.play()
        streamEngine.play()
        RadioPlaybackService.start(context)
    }

    fun pause() {
        _playbackState.update { it.copy(isPlaying = false) }
        hardwareEngine.pause()
        streamEngine.pause()
        abandonAudioFocus()
    }

    fun togglePlayPause() {
        if (_playbackState.value.isPlaying) {
            pause()
        } else {
            play()
        }
    }

    fun startScan(scanUp: Boolean, onFound: (Int) -> Unit = {}) {
        _playbackState.update { it.copy(isScanning = true) }
        if (_playbackState.value.isHardwareActive) {
            hardwareEngine.startScan(scanUp) { foundFreq ->
                _playbackState.update { it.copy(isScanning = false) }
                tune(_playbackState.value.currentBand, foundFreq)
                onFound(foundFreq)
            }
        } else {
            streamEngine.startScan(scanUp) { foundFreq ->
                _playbackState.update { it.copy(isScanning = false) }
                tune(_playbackState.value.currentBand, foundFreq)
                onFound(foundFreq)
            }
        }
    }

    fun stopScan() {
        hardwareEngine.stopScan()
        streamEngine.stopScan()
        _playbackState.update { it.copy(isScanning = false) }
    }

    fun adjustSystemVolume(raise: Boolean) {
        val direction = if (raise) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        try {
            audioManager.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, AudioManager.FLAG_SHOW_UI)
        } catch (_: Exception) {}
        updateStreamVolumeState()
    }

    fun setMasterGain(percent: Int) {
        val safePercent = percent.coerceIn(10, 100)
        _playbackState.update { it.copy(masterGainPercent = safePercent) }
        val gainFloat = safePercent / 100f
        hardwareEngine.setVolumeGain(gainFloat)
        streamEngine.setVolumeGain(gainFloat)
    }

    fun updateStreamVolumeState() {
        try {
            val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            val pct = if (max > 0) ((current.toFloat() / max) * 100).toInt() else 100
            _playbackState.update { it.copy(streamVolumePercent = pct) }
        } catch (_: Exception) {}
    }

    private fun requestAudioFocus() {
        if (hasAudioFocus) return

        val listener = AudioManager.OnAudioFocusChangeListener { focusChange ->
            when (focusChange) {
                AudioManager.AUDIOFOCUS_LOSS -> {
                    pause()
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    // Mute temporarily during incoming call
                    mute(true)
                }
                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    // Duck volume during GPS navigation alerts (Waze, Maps)
                    streamEngine.setVolumeGain((_playbackState.value.masterGainPercent / 100f) * 0.3f)
                }
                AudioManager.AUDIOFOCUS_GAIN -> {
                    // Restore normal volume
                    mute(false)
                    streamEngine.setVolumeGain(_playbackState.value.masterGainPercent / 100f)
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val playbackAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(playbackAttributes)
                .setAcceptsDelayedFocusGain(true)
                .setOnAudioFocusChangeListener(listener)
                .build()

            val res = audioManager.requestAudioFocus(audioFocusRequest!!)
            hasAudioFocus = (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        } else {
            @Suppress("DEPRECATION")
            val res = audioManager.requestAudioFocus(
                listener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
            hasAudioFocus = (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED)
        }
    }

    private fun abandonAudioFocus() {
        if (!hasAudioFocus) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        } else {
            @Suppress("DEPRECATION")
            audioManager.abandonAudioFocus(null)
        }
        hasAudioFocus = false
    }

    fun mute(muted: Boolean) {
        _playbackState.update { it.copy(isMuted = muted) }
        hardwareEngine.mute(muted)
        streamEngine.mute(muted)
    }

    fun release() {
        abandonAudioFocus()
        hardwareEngine.release()
        streamEngine.release()
    }
}
