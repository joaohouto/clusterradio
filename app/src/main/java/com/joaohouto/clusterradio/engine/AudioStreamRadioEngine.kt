package com.joaohouto.clusterradio.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
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
import kotlin.random.Random

class AudioStreamRadioEngine(private val context: Context) : RadioEngine {

    companion object {
        private const val TAG = "AudioStreamEngine"

        // Public reliable online radio streams for popular frequencies
        private val KNOWN_STREAMS = mapOf(
            89_100 to "https://icecast.radiogazetafm.com.br/gazetafm",
            91_300 to "https://streaming.radiodisney.com.br/stream",
            98_500 to "https://23803.live.streamtheworld.com/RADIO_METROPOLEAAC.aac",
            100_900 to "https://icecast.jovempan.com.br/jp_fm",
            102_700 to "https://playerservices.streamtheworld.com/api/livestream-redirect/ALPHA_FMAAC.aac",
            105_700 to "https://icecast.cbn.com.br/cbn_sp"
        )
    }

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private var exoPlayer: ExoPlayer? = null
    private var scanJob: Job? = null
    private var currentVolumeGain: Float = 1.0f

    private val _state = MutableStateFlow(RadioEngineState(isPlaying = false))
    override val state: StateFlow<RadioEngineState> = _state.asStateFlow()

    init {
        initPlayer()
    }

    private fun initPlayer() {
        try {
            exoPlayer = ExoPlayer.Builder(context).build().apply {
                volume = currentVolumeGain
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) {
                            _state.update { it.copy(isPlaying = true) }
                        }
                    }

                    override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
                        val title = mediaMetadata.title?.toString()
                        val artist = mediaMetadata.artist?.toString()
                        val station = mediaMetadata.station?.toString()

                        val songInfo = when {
                            !artist.isNullOrBlank() && !title.isNullOrBlank() -> "$artist - $title"
                            !title.isNullOrBlank() -> title
                            else -> null
                        }

                        _state.update {
                            it.copy(
                                rdsStationName = station ?: it.rdsStationName,
                                radioText = songInfo ?: it.radioText
                            )
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.w(TAG, "ExoPlayer stream error: ${error.message}")
                    }
                })
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ExoPlayer: ${e.message}")
        }
    }

    override fun tune(band: RadioBand, frequencyKhz: Int) {
        _state.update {
            it.copy(
                currentBand = band,
                currentFrequencyKhz = frequencyKhz,
                rdsStationName = null,
                radioText = null
            )
        }

        val streamUrl = KNOWN_STREAMS[frequencyKhz]
        if (streamUrl != null && _state.value.isPlaying) {
            playStream(streamUrl)
        } else if (_state.value.isPlaying) {
            // Play gentle static / synthesized carrier sound when no internet stream is registered
            playSynthesizedTone(frequencyKhz)
        }
    }

    private fun playStream(url: String) {
        try {
            exoPlayer?.apply {
                stop()
                clearMediaItems()
                setMediaItem(MediaItem.fromUri(Uri.parse(url)))
                prepare()
                play()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to play stream: ${e.message}")
        }
    }

    private fun playSynthesizedTone(frequencyKhz: Int) {
        // Soft audio feedback when running standalone
        scope.launch(Dispatchers.IO) {
            try {
                val sampleRate = 22050
                val numSamples = sampleRate / 2 // 500ms soft chime
                val buffer = ShortArray(numSamples)
                val freq = 440.0 + ((frequencyKhz % 500) * 0.5)
                for (i in 0 until numSamples) {
                    val angle = 2.0 * Math.PI * i / (sampleRate / freq)
                    val envelope = 1.0 - (i.toDouble() / numSamples)
                    buffer[i] = (Math.sin(angle) * 32767 * 0.15 * envelope).toInt().toShort()
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(buffer, 0, buffer.size)
                track.play()
            } catch (_: Exception) {}
        }
    }

    override fun play() {
        _state.update { it.copy(isPlaying = true, isMuted = false) }
        val freq = _state.value.currentFrequencyKhz
        val streamUrl = KNOWN_STREAMS[freq]
        if (streamUrl != null) {
            playStream(streamUrl)
        } else {
            playSynthesizedTone(freq)
        }
    }

    override fun pause() {
        _state.update { it.copy(isPlaying = false) }
        try {
            exoPlayer?.pause()
        } catch (_: Exception) {}
    }

    override fun mute(muted: Boolean) {
        _state.update { it.copy(isMuted = muted) }
        exoPlayer?.volume = if (muted) 0f else currentVolumeGain
    }

    override fun startScan(scanUp: Boolean, onFound: (Int) -> Unit) {
        scanJob?.cancel()
        _state.update { it.copy(isScanning = true) }
        scanJob = scope.launch {
            val band = _state.value.currentBand
            var nextFreq = _state.value.currentFrequencyKhz
            repeat(10) {
                delay(100)
                nextFreq = band.stepFrequency(nextFreq, scanUp)
                _state.update { it.copy(currentFrequencyKhz = nextFreq) }
            }
            _state.update { it.copy(isScanning = false) }
            tune(band, nextFreq)
            onFound(nextFreq)
        }
    }

    override fun stopScan() {
        scanJob?.cancel()
        _state.update { it.copy(isScanning = false) }
    }

    override fun setVolumeGain(gain: Float) {
        currentVolumeGain = gain.coerceIn(0.1f, 1.0f)
        if (!_state.value.isMuted) {
            exoPlayer?.volume = currentVolumeGain
        }
    }

    override fun release() {
        scanJob?.cancel()
        try {
            exoPlayer?.release()
            exoPlayer = null
        } catch (_: Exception) {}
    }
}
