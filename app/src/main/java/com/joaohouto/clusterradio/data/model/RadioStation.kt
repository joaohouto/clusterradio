package com.joaohouto.clusterradio.data.model

data class RadioStation(
    val frequencyKhz: Int,
    val band: RadioBand,
    val name: String? = null,
    val isStereo: Boolean = true,
    val signalStrength: Int = 100
) {
    val displayFrequency: String
        get() = band.formatFrequency(frequencyKhz)
}
