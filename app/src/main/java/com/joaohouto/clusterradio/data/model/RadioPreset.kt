package com.joaohouto.clusterradio.data.model

data class RadioPreset(
    val slot: Int, // 1..6
    val frequencyKhz: Int,
    val band: RadioBand,
    val name: String? = null
) {
    val displayFrequency: String
        get() = if (frequencyKhz > 0) band.formatFrequency(frequencyKhz) else "---"

    val isSet: Boolean
        get() = frequencyKhz > 0
}
