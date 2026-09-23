package com.joaohouto.clusterradio.ui.radio

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.joaohouto.clusterradio.data.model.RadioBand
import com.joaohouto.clusterradio.data.model.RadioPreset
import com.joaohouto.clusterradio.data.repository.RadioPreferencesRepository
import com.joaohouto.clusterradio.data.repository.RadioSettingsSnapshot
import com.joaohouto.clusterradio.engine.CompositeRadioManager
import com.joaohouto.clusterradio.engine.RadioPlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RadioViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = RadioPreferencesRepository.getInstance(application)
    private val radioManager = CompositeRadioManager.getInstance(application)

    val playbackState: StateFlow<RadioPlaybackState> = radioManager.playbackState

    val settings: StateFlow<RadioSettingsSnapshot> = repository.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = RadioSettingsSnapshot(
            lastBand = RadioBand.FM,
            lastFmFrequencyKhz = RadioBand.FM.defaultFrequencyKhz,
            lastAmFrequencyKhz = RadioBand.AM.defaultFrequencyKhz,
            accentThemeId = "needle_red",
            masterVolumePercent = 100,
            keepScreenOn = true
        )
    )

    private val _presets = MutableStateFlow<List<RadioPreset>>(emptyList())
    val presets: StateFlow<List<RadioPreset>> = _presets.asStateFlow()

    init {
        restoreLastSession()
        observePresetsForCurrentBand()
    }

    private fun restoreLastSession() {
        viewModelScope.launch {
            val snapshot = repository.settingsFlow.first()
            val band = snapshot.lastBand
            val freq = if (band == RadioBand.FM) snapshot.lastFmFrequencyKhz else snapshot.lastAmFrequencyKhz
            radioManager.setMasterGain(snapshot.masterVolumePercent)
            radioManager.tune(band, freq)
        }
    }

    private fun observePresetsForCurrentBand() {
        viewModelScope.launch {
            playbackState.collect { state ->
                repository.getPresetsFlow(state.currentBand).collect { list ->
                    _presets.value = list
                }
            }
        }
    }

    fun setBand(band: RadioBand) {
        viewModelScope.launch {
            val snapshot = repository.settingsFlow.first()
            val freq = if (band == RadioBand.FM) snapshot.lastFmFrequencyKhz else snapshot.lastAmFrequencyKhz
            radioManager.tune(band, freq)
            repository.saveCurrentStation(band, freq)
        }
    }

    fun tuneFrequency(khz: Int) {
        val band = playbackState.value.currentBand
        radioManager.tune(band, khz)
        viewModelScope.launch {
            repository.saveCurrentStation(band, khz)
        }
    }

    fun stepFrequency(stepUp: Boolean) {
        radioManager.step(stepUp)
        viewModelScope.launch {
            val current = playbackState.value
            repository.saveCurrentStation(current.currentBand, current.currentFrequencyKhz)
        }
    }

    fun startScan(scanUp: Boolean) {
        radioManager.startScan(scanUp) { foundFreq ->
            viewModelScope.launch {
                repository.saveCurrentStation(playbackState.value.currentBand, foundFreq)
            }
        }
    }

    fun togglePlayPause() {
        radioManager.togglePlayPause()
    }

    fun play() {
        radioManager.play()
    }

    fun pause() {
        radioManager.pause()
    }

    fun adjustVolume(raise: Boolean) {
        radioManager.adjustSystemVolume(raise)
    }

    fun savePreset(slot: Int) {
        val state = playbackState.value
        viewModelScope.launch {
            repository.savePreset(state.currentBand, slot, state.currentFrequencyKhz, state.stationName)
        }
    }

    fun tunePreset(preset: RadioPreset) {
        if (preset.isSet) {
            tuneFrequency(preset.frequencyKhz)
        }
    }

    fun saveAccentTheme(themeId: String) {
        viewModelScope.launch {
            repository.saveAccentTheme(themeId)
        }
    }

    fun saveMasterVolume(percent: Int) {
        radioManager.setMasterGain(percent)
        viewModelScope.launch {
            repository.saveMasterVolume(percent)
        }
    }

    fun saveKeepScreenOn(enabled: Boolean) {
        viewModelScope.launch {
            repository.saveKeepScreenOn(enabled)
        }
    }
}
