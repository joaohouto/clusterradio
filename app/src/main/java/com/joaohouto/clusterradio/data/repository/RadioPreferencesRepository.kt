package com.joaohouto.clusterradio.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.joaohouto.clusterradio.data.model.RadioBand
import com.joaohouto.clusterradio.data.model.RadioPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.radioDataStore: DataStore<Preferences> by preferencesDataStore(name = "cluster_radio_prefs")

data class RadioSettingsSnapshot(
    val lastBand: RadioBand,
    val lastFmFrequencyKhz: Int,
    val lastAmFrequencyKhz: Int,
    val accentThemeId: String,
    val masterVolumePercent: Int,
    val keepScreenOn: Boolean
)

class RadioPreferencesRepository(private val context: Context) {

    companion object {
        val KEY_BAND = stringPreferencesKey("last_band")
        val KEY_LAST_FM_FREQ = intPreferencesKey("last_fm_frequency")
        val KEY_LAST_AM_FREQ = intPreferencesKey("last_am_frequency")
        val KEY_ACCENT_THEME = stringPreferencesKey("accent_theme")
        val KEY_VOLUME_PERCENT = intPreferencesKey("volume_percent")
        val KEY_KEEP_SCREEN_ON = booleanPreferencesKey("keep_screen_on")

        fun getPresetFreqKey(band: RadioBand, slot: Int): Preferences.Key<Int> {
            return intPreferencesKey("${band.id.lowercase()}_preset_freq_$slot")
        }

        fun getPresetNameKey(band: RadioBand, slot: Int): Preferences.Key<String> {
            return stringPreferencesKey("${band.id.lowercase()}_preset_name_$slot")
        }

        // Default Brazilian/International Presets
        private val DEFAULT_FM_PRESETS = listOf(89_100, 91_300, 98_500, 100_900, 102_700, 105_700)
        private val DEFAULT_AM_PRESETS = listOf(620, 740, 840, 1040, 1100, 1220)

        @Volatile
        private var INSTANCE: RadioPreferencesRepository? = null

        fun getInstance(context: Context): RadioPreferencesRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = RadioPreferencesRepository(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }
    }

    val settingsFlow: Flow<RadioSettingsSnapshot> = context.radioDataStore.data.map { prefs ->
        val bandStr = prefs[KEY_BAND] ?: RadioBand.FM.id
        val band = if (bandStr == RadioBand.AM.id) RadioBand.AM else RadioBand.FM
        RadioSettingsSnapshot(
            lastBand = band,
            lastFmFrequencyKhz = prefs[KEY_LAST_FM_FREQ] ?: RadioBand.FM.defaultFrequencyKhz,
            lastAmFrequencyKhz = prefs[KEY_LAST_AM_FREQ] ?: RadioBand.AM.defaultFrequencyKhz,
            accentThemeId = prefs[KEY_ACCENT_THEME] ?: "needle_red",
            masterVolumePercent = prefs[KEY_VOLUME_PERCENT] ?: 100,
            keepScreenOn = prefs[KEY_KEEP_SCREEN_ON] ?: true
        )
    }

    fun getPresetsFlow(band: RadioBand): Flow<List<RadioPreset>> = context.radioDataStore.data.map { prefs ->
        val defaultList = if (band == RadioBand.FM) DEFAULT_FM_PRESETS else DEFAULT_AM_PRESETS
        (1..6).map { slot ->
            val freqKey = getPresetFreqKey(band, slot)
            val nameKey = getPresetNameKey(band, slot)
            val defaultFreq = defaultList.getOrElse(slot - 1) { band.defaultFrequencyKhz }
            val freq = prefs[freqKey] ?: defaultFreq
            val name = prefs[nameKey]
            RadioPreset(slot = slot, frequencyKhz = freq, band = band, name = name)
        }
    }

    suspend fun saveCurrentStation(band: RadioBand, frequencyKhz: Int) {
        context.radioDataStore.edit { prefs ->
            prefs[KEY_BAND] = band.id
            if (band == RadioBand.FM) {
                prefs[KEY_LAST_FM_FREQ] = frequencyKhz
            } else {
                prefs[KEY_LAST_AM_FREQ] = frequencyKhz
            }
        }
    }

    suspend fun savePreset(band: RadioBand, slot: Int, frequencyKhz: Int, name: String? = null) {
        context.radioDataStore.edit { prefs ->
            prefs[getPresetFreqKey(band, slot)] = frequencyKhz
            if (name != null) {
                prefs[getPresetNameKey(band, slot)] = name
            } else {
                prefs.remove(getPresetNameKey(band, slot))
            }
        }
    }

    suspend fun saveAccentTheme(themeId: String) {
        context.radioDataStore.edit { prefs ->
            prefs[KEY_ACCENT_THEME] = themeId
        }
    }

    suspend fun saveMasterVolume(percent: Int) {
        context.radioDataStore.edit { prefs ->
            prefs[KEY_VOLUME_PERCENT] = percent.coerceIn(10, 100)
        }
    }

    suspend fun saveKeepScreenOn(enabled: Boolean) {
        context.radioDataStore.edit { prefs ->
            prefs[KEY_KEEP_SCREEN_ON] = enabled
        }
    }
}
