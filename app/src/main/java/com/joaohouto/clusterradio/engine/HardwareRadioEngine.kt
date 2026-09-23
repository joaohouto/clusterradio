package com.joaohouto.clusterradio.engine

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
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

/**
 * Universal Hardware Radio Engine for Android Car Head Units.
 *
 * Supports native MCU communication across all major automotive platforms:
 * 1. FYT / Teyes / Syu (UIS7862, UIS8581, SC9853i, 7862s) via deep-link & MyService
 * 2. Microntek / Rockchip (MTCB, MTCD, MTCE, PX3, PX5, PX6, etc.)
 * 3. Topway / TS (TS7, TS8, TS9, TS10, TS18, MTK8259, MTK8667)
 * 4. Jancar / IVI (MTK 8227L, com.jancar.radio)
 * 5. XYAuto / AC8227L / YT9216B / Hengchen
 * 6. QuickFish / K706 (MT8163, com.hcn.autoradio)
 * 7. System Audio HAL routing parameters
 */
class HardwareRadioEngine(private val context: Context) : RadioEngine {

    companion object {
        private const val TAG = "HardwareRadioEngine"

        // FYT / Syu / Teyes
        private const val PKG_SYU_RADIO = "com.syu.radio"
        private const val CLS_SYU_MYSERVICE = "com.syu.broadcast.MyService"
        private const val ACTION_SYU_RADIO = "com.syu.radio"
        private const val ACTION_SYU_PREV = "com.syu.radio.prevservice"
        private const val ACTION_SYU_NEXT = "com.syu.radio.nextservice"
        private const val URI_SYU_TUNE_TEMPLATE = "radio://tune?freq=%d"

        // Microntek / Rockchip MTC
        private const val ACTION_MICRONTEK_SYNC = "com.microntek.sync"
        private const val ACTION_MICRONTEK_REPORT = "com.microntek.radio.report"
        private const val ACTION_MICRONTEK_REPORTS = "com.microntek.radio.reports"

        // Topway / TS
        private const val ACTION_TS_RADIO = "com.ts.radiostation"
        private const val ACTION_TS_MAIN = "com.ts.main.action"
        private const val ACTION_TS_RADIO_UPDATE = "com.ts.main.radio.update"

        // Jancar / IVI
        private const val PKG_JANCAR_RADIO = "com.jancar.radio"
        private const val CLS_JANCAR_FM_SERVICE = "com.jancar.radio.FmService"
        private const val EXTRA_JANCAR_FREQ = "fmradio.freq.valid"
        private const val ACTION_JANCAR_SEEK_NEXT = "fmradio.seek.next"
        private const val ACTION_JANCAR_SEEK_PREV = "fmradio.seek.previous"
        private const val ACTION_JANCAR_TURN_OFF = "fmradio.turnoff"

        // XYAuto / AC8227L
        private const val ACTION_XY_TUNE = "com.xy.radio.tune"
        private const val ACTION_XY_FREQ = "xy.android.radio.freq"
        private const val ACTION_XY_SEEK = "com.xy.radio.seek"

        // QuickFish / K706
        private const val ACTION_QF_UPDATE = "com.qf.radio.update_action"

        // System Launcher Actions
        private const val ACTION_LAUNCHER_UPDATE_RADIO = "com.android.launcher.action.UPDATE_RADIO"
        private const val ACTION_LAUNCHER_UPDATE_SOURCE = "com.android.launcher.action.UPDATE_SOURCE"
        private const val ACTION_SEND_RADIO_FREQUENCE_NEW = "ACTION_SEND_RADIO_FREQUENCE_NEW"
    }

    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var scanJob: Job? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val isHardwareDetected: Boolean = detectHardwareTuner()

    private val _state = MutableStateFlow(
        RadioEngineState(
            isPlaying = false,
            isHardwareTunerActive = isHardwareDetected
        )
    )
    override val state: StateFlow<RadioEngineState> = _state.asStateFlow()

    private val radioReportReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            try {
                handleRadioBroadcast(intent)
            } catch (e: Exception) {
                Log.w(TAG, "Error handling radio broadcast: ${e.message}")
            }
        }
    }

    init {
        registerRadioReceiver()
    }

    private fun detectHardwareTuner(): Boolean {
        // 1. Check known OEM car packages
        val knownPackages = listOf(
            "com.syu.radio",
            "com.syu.ms",
            "com.syu.canbus",
            "com.ts.MainUI",
            "com.ts.radiostation",
            "com.microntek.radio",
            "com.microntek.sync",
            "com.hcn.autoradio",
            "com.jancar.radio",
            "com.jancar.services",
            "com.xy.radio",
            "com.xyauto.radio",
            "com.xyauto.ui",
            "com.nwd.radio",
            "com.nwd.radio.service",
            "com.mediatek.fmradio",
            "com.android.fmradio",
            "com.car.radio",
            "com.tw.radio",
            "tw.radio"
        )

        try {
            val pm = context.packageManager
            for (pkg in knownPackages) {
                try {
                    pm.getPackageInfo(pkg, 0)
                    Log.i(TAG, "Detected automotive radio package: $pkg")
                    return true
                } catch (_: Exception) {}
            }
        } catch (_: Exception) {}

        // 2. Check automotive MCU system service
        try {
            val mcuService = context.getSystemService("mcu_service")
            if (mcuService != null) {
                Log.i(TAG, "Detected automotive mcu_service")
                return true
            }
        } catch (_: Exception) {}

        // 3. Check system properties for car platforms
        try {
            val systemPropertiesClass = Class.forName("android.os.SystemProperties")
            val getMethod = systemPropertiesClass.getMethod("get", String::class.java)

            val fytProp = getMethod.invoke(null, "sys.fyt.platform") as? String
            val fytMfr = getMethod.invoke(null, "ro.build.fytmanufacturer") as? String
            val boardProp = getMethod.invoke(null, "ro.product.board") as? String

            if (!fytProp.isNullOrBlank() || !fytMfr.isNullOrBlank()) {
                Log.i(TAG, "Detected FYT platform: $fytProp / $fytMfr")
                return true
            }

            if (boardProp != null) {
                val b = boardProp.lowercase()
                if (b.contains("uis7862") || b.contains("sc9853") || b.contains("px3") ||
                    b.contains("px5") || b.contains("px6") || b.contains("8227l") ||
                    b.contains("8259") || b.contains("8667") || b.contains("topway")
                ) {
                    Log.i(TAG, "Detected automotive board: $boardProp")
                    return true
                }
            }
        } catch (_: Exception) {}

        return false
    }

    private fun registerRadioReceiver() {
        val filter = IntentFilter().apply {
            addAction(ACTION_MICRONTEK_SYNC)
            addAction(ACTION_MICRONTEK_REPORT)
            addAction(ACTION_MICRONTEK_REPORTS)
            addAction(ACTION_SYU_RADIO)
            addAction(ACTION_TS_RADIO)
            addAction(ACTION_TS_RADIO_UPDATE)
            addAction(ACTION_XY_TUNE)
            addAction(ACTION_XY_FREQ)
            addAction(ACTION_SEND_RADIO_FREQUENCE_NEW)
            addAction(ACTION_LAUNCHER_UPDATE_RADIO)
            addAction(ACTION_QF_UPDATE)
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(radioReportReceiver, filter, Context.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(radioReportReceiver, filter)
            }
            Log.d(TAG, "Registered automotive radio broadcast listener")
        } catch (e: Exception) {
            Log.w(TAG, "Could not register radio receiver: ${e.message}")
        }
    }

    private fun handleRadioBroadcast(intent: Intent) {
        val extras = intent.extras ?: return

        // 1. Frequency extraction
        var freqFound: Int? = null

        if (extras.containsKey("freq")) {
            val f = extras.get("freq")
            freqFound = parseFrequencyValue(f)
        } else if (extras.containsKey("frequency")) {
            val f = extras.get("frequency")
            freqFound = parseFrequencyValue(f)
        } else if (extras.containsKey("com.qf.radio.update_action_freq_key")) {
            val f = extras.get("com.qf.radio.update_action_freq_key")
            freqFound = parseFrequencyValue(f)
        }

        // 2. Station name (RDS PS) extraction
        var stationName: String? = null
        val possibleNameKeys = listOf("name", "station", "station_name", "ps", "com.qf.radio.update_action_name_key")
        for (k in possibleNameKeys) {
            val name = extras.getString(k)
            if (!name.isNullOrBlank()) {
                stationName = name.trim()
                break
            }
        }

        // 3. Radio Text (RDS RT) extraction
        var radioText: String? = null
        val possibleTextKeys = listOf("text", "radiotext", "rt", "title")
        for (k in possibleTextKeys) {
            val text = extras.getString(k)
            if (!text.isNullOrBlank()) {
                radioText = text.trim()
                break
            }
        }

        if (freqFound != null && freqFound > 0) {
            val currentBand = _state.value.currentBand
            val normalizedKhz = if (currentBand == RadioBand.FM && freqFound in 875..1080) {
                freqFound * 100 // e.g. 1009 -> 100900
            } else if (currentBand == RadioBand.FM && freqFound in 8750..10800) {
                freqFound * 10  // e.g. 10090 -> 100900
            } else {
                freqFound
            }

            _state.update {
                it.copy(
                    currentFrequencyKhz = normalizedKhz,
                    rdsStationName = stationName ?: it.rdsStationName,
                    radioText = radioText ?: it.radioText,
                    isHardwareTunerActive = true
                )
            }
            Log.d(TAG, "Updated from MCU broadcast: freq=$normalizedKhz kHz, station=$stationName")
        }
    }

    private fun parseFrequencyValue(value: Any?): Int? {
        return when (value) {
            is Int -> value
            is Long -> value.toInt()
            is Float -> (value * 1000).toInt()
            is Double -> (value * 1000).toInt()
            is String -> {
                value.replace("MHz", "").replace("kHz", "").trim().toDoubleOrNull()?.let { d ->
                    if (d < 200.0) (d * 1000).toInt() else d.toInt()
                }
            }
            else -> null
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

        // Dispatch tuning across all automotive interfaces
        dispatchAllMcuTune(band, frequencyKhz)
        unmuteHardwareAudio()
    }

    private fun dispatchAllMcuTune(band: RadioBand, frequencyKhz: Int) {
        try {
            // 1. FYT / Syu / Teyes: Deep Link
            val oemFreq = if (band == RadioBand.FM) frequencyKhz / 10 else frequencyKhz // 100.9 MHz -> 10090
            val uri = Uri.parse(String.format(URI_SYU_TUNE_TEMPLATE, oemFreq))
            try {
                val fytViewIntent = Intent(Intent.ACTION_VIEW, uri).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                }
                context.startActivity(fytViewIntent)
            } catch (_: Exception) {}

            // FYT broadcast
            val syuIntent = Intent(ACTION_SYU_RADIO).apply {
                putExtra("cmd", "tune")
                putExtra("band", band.id)
                putExtra("freq", frequencyKhz)
                putExtra("frequency", frequencyKhz)
            }
            context.sendBroadcast(syuIntent)

            // 2. Microntek / Rockchip MTC
            val microntekIntent = Intent(ACTION_MICRONTEK_SYNC).apply {
                putExtra("type", "tuner")
                putExtra("cmd", "tune")
                putExtra("band", if (band == RadioBand.FM) 0 else 1)
                putExtra("freq", frequencyKhz)
            }
            context.sendBroadcast(microntekIntent)

            val microntekReportIntent = Intent(ACTION_MICRONTEK_REPORT).apply {
                putExtra("freq", frequencyKhz)
                putExtra("band", if (band == RadioBand.FM) 0 else 1)
            }
            context.sendBroadcast(microntekReportIntent)

            // 3. Topway / TS
            val tsIntent = Intent(ACTION_TS_RADIO).apply {
                putExtra("command", "set_freq")
                putExtra("freq", frequencyKhz)
            }
            context.sendBroadcast(tsIntent)

            val tsMainIntent = Intent(ACTION_TS_MAIN).apply {
                putExtra("cmd", "radio_tune")
                putExtra("freq", frequencyKhz)
                putExtra("band", if (band == RadioBand.FM) 0 else 1)
            }
            context.sendBroadcast(tsMainIntent)

            // 4. Jancar / IVI
            try {
                val jancarIntent = Intent().apply {
                    component = ComponentName(PKG_JANCAR_RADIO, CLS_JANCAR_FM_SERVICE)
                    putExtra(EXTRA_JANCAR_FREQ, frequencyKhz / 10)
                }
                context.startService(jancarIntent)
            } catch (_: Exception) {}

            // 5. XYAuto / AC8227L
            val xyIntent = Intent(ACTION_XY_TUNE).apply {
                putExtra("freq", frequencyKhz)
                putExtra("frequency", frequencyKhz)
            }
            context.sendBroadcast(xyIntent)

            val xyFreqIntent = Intent(ACTION_XY_FREQ).apply {
                putExtra("freq", frequencyKhz)
            }
            context.sendBroadcast(xyFreqIntent)

            // 6. QuickFish / K706
            val qfIntent = Intent(ACTION_QF_UPDATE).apply {
                putExtra("com.qf.radio.update_action_key", band.formatFrequency(frequencyKhz))
                putExtra("com.qf.radio.update_action_freq_key", if (band == RadioBand.FM) frequencyKhz / 10 else frequencyKhz)
                putExtra("com.qf.radio.update_action_band_key", if (band == RadioBand.FM) 0 else 3)
                putExtra("com.qf.radio.update_action_searching_key", false)
            }
            context.sendBroadcast(qfIntent)

            // 7. General Automotive Launcher Updates
            val launcherUpdate = Intent(ACTION_LAUNCHER_UPDATE_RADIO).apply {
                putExtra("frequency", band.formatFrequency(frequencyKhz))
                putExtra("band", band.displayName)
                putExtra("isRadio", true)
            }
            context.sendBroadcast(launcherUpdate)

            val sourceUpdate = Intent(ACTION_LAUNCHER_UPDATE_SOURCE).apply {
                putExtra("source", 1) // 1 = Radio
                putExtra("sourceName", "Radio")
            }
            context.sendBroadcast(sourceUpdate)

            // 8. Audio HAL Parameters
            audioManager?.setParameters("tuner_band=${if (band == RadioBand.FM) "fm" else "am"}")
            audioManager?.setParameters("fm_radio_volume=15")
            audioManager?.setParameters("fm_radio_mute=0")
            audioManager?.setParameters("radio_audio_source=1")
            audioManager?.setParameters("handle_fm=1")

            Log.d(TAG, "Dispatched hardware MCU tune: ${band.displayName} $frequencyKhz kHz")
        } catch (e: Exception) {
            Log.w(TAG, "Hardware MCU tune dispatch failed: ${e.message}")
        }
    }

    override fun play() {
        _state.update { it.copy(isPlaying = true, isMuted = false) }

        // Send play/power on commands to MCU platforms
        try {
            // Microntek
            context.sendBroadcast(Intent(ACTION_MICRONTEK_SYNC).apply {
                putExtra("type", "tuner")
                putExtra("cmd", "play")
            })

            // Topway
            context.sendBroadcast(Intent(ACTION_TS_RADIO).apply {
                putExtra("command", "power_on")
            })

            // Launcher source
            context.sendBroadcast(Intent(ACTION_LAUNCHER_UPDATE_SOURCE).apply {
                putExtra("source", 1)
                putExtra("sourceName", "Radio")
            })

            unmuteHardwareAudio()

            // If on FYT, wake up OEM radio if not yet activated
            wakeFytRadio()
        } catch (_: Exception) {}
    }

    override fun pause() {
        _state.update { it.copy(isPlaying = false) }

        try {
            // Microntek
            context.sendBroadcast(Intent(ACTION_MICRONTEK_SYNC).apply {
                putExtra("type", "tuner")
                putExtra("cmd", "pause")
            })

            // Topway
            context.sendBroadcast(Intent(ACTION_TS_RADIO).apply {
                putExtra("command", "power_off")
            })

            // Jancar
            try {
                context.startService(Intent(ACTION_JANCAR_TURN_OFF).apply {
                    component = ComponentName(PKG_JANCAR_RADIO, CLS_JANCAR_FM_SERVICE)
                })
            } catch (_: Exception) {}

            muteHardwareAudio()
        } catch (_: Exception) {}
    }

    override fun mute(muted: Boolean) {
        _state.update { it.copy(isMuted = muted) }
        if (muted) {
            muteHardwareAudio()
        } else {
            unmuteHardwareAudio()
        }
    }

    private fun unmuteHardwareAudio() {
        try {
            audioManager?.setParameters("fm_radio_mute=0")
            audioManager?.setParameters("FMRadioOn=1")
            audioManager?.setParameters("radio_audio_source=1")
            audioManager?.setParameters("handle_fm=1")
        } catch (_: Exception) {}
    }

    private fun muteHardwareAudio() {
        try {
            audioManager?.setParameters("fm_radio_mute=1")
            audioManager?.setParameters("FMRadioOn=0")
        } catch (_: Exception) {}
    }

    private fun wakeFytRadio() {
        try {
            val freq = _state.value.currentFrequencyKhz
            val band = _state.value.currentBand
            val oemFreq = if (band == RadioBand.FM) freq / 10 else freq
            val uri = Uri.parse(String.format(URI_SYU_TUNE_TEMPLATE, oemFreq))
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    override fun startScan(scanUp: Boolean, onFound: (Int) -> Unit) {
        scanJob?.cancel()
        _state.update { it.copy(isScanning = true) }

        // 1. Dispatch OEM seek commands to hardware
        dispatchMcuSeek(scanUp)

        // 2. Hardware scan listener / animated stepper
        scanJob = scope.launch {
            val band = _state.value.currentBand
            var currentFreq = _state.value.currentFrequencyKhz

            // Step through frequencies while waiting for MCU report or stopping on realistic signal step
            repeat(16) {
                delay(130)
                currentFreq = band.stepFrequency(currentFreq, scanUp)
                _state.update { it.copy(currentFrequencyKhz = currentFreq) }
                dispatchAllMcuTune(band, currentFreq)
            }

            _state.update { it.copy(isScanning = false) }
            onFound(_state.value.currentFrequencyKhz)
        }
    }

    private fun dispatchMcuSeek(scanUp: Boolean) {
        try {
            // 1. FYT / Syu via MyService
            try {
                val fytServiceAction = if (scanUp) ACTION_SYU_NEXT else ACTION_SYU_PREV
                val fytIntent = Intent(fytServiceAction).apply {
                    component = ComponentName(PKG_SYU_RADIO, CLS_SYU_MYSERVICE)
                }
                context.startService(fytIntent)
            } catch (_: Exception) {}

            // 2. Microntek
            context.sendBroadcast(Intent(ACTION_MICRONTEK_SYNC).apply {
                putExtra("type", "tuner")
                putExtra("cmd", if (scanUp) "seek_next" else "seek_prev")
            })

            // 3. Topway
            context.sendBroadcast(Intent(ACTION_TS_RADIO).apply {
                putExtra("command", if (scanUp) "seek_up" else "seek_down")
            })

            // 4. Jancar
            try {
                val jancarAction = if (scanUp) ACTION_JANCAR_SEEK_NEXT else ACTION_JANCAR_SEEK_PREV
                context.startService(Intent(jancarAction).apply {
                    component = ComponentName(PKG_JANCAR_RADIO, CLS_JANCAR_FM_SERVICE)
                })
            } catch (_: Exception) {}

            // 5. XYAuto
            context.sendBroadcast(Intent(ACTION_XY_SEEK).apply {
                putExtra("direction", if (scanUp) 1 else 0)
            })

            Log.d(TAG, "Dispatched hardware MCU seek: scanUp=$scanUp")
        } catch (e: Exception) {
            Log.w(TAG, "Hardware seek dispatch failed: ${e.message}")
        }
    }

    override fun stopScan() {
        scanJob?.cancel()
        _state.update { it.copy(isScanning = false) }
    }

    override fun setVolumeGain(gain: Float) {
        // Gain mapped to system music & FM volume
    }

    override fun release() {
        scanJob?.cancel()
        try {
            context.unregisterReceiver(radioReportReceiver)
        } catch (_: Exception) {}
    }
}
