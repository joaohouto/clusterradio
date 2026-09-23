package com.joaohouto.clusterradio.data.model

enum class RadioBand(
    val id: String,
    val displayName: String,
    val minFrequencyKhz: Int,
    val maxFrequencyKhz: Int,
    val defaultStepKhz: Int,
    val defaultFrequencyKhz: Int,
    val unitLabel: String
) {
    FM(
        id = "FM",
        displayName = "FM",
        minFrequencyKhz = 87_500,     // 87.5 MHz
        maxFrequencyKhz = 108_000,    // 108.0 MHz
        defaultStepKhz = 100,         // 100 kHz (0.1 MHz)
        defaultFrequencyKhz = 98_500, // 98.5 MHz
        unitLabel = "MHz"
    ),
    AM(
        id = "AM",
        displayName = "AM",
        minFrequencyKhz = 530,        // 530 kHz
        maxFrequencyKhz = 1710,       // 1710 kHz
        defaultStepKhz = 10,          // 10 kHz
        defaultFrequencyKhz = 740,    // 740 kHz
        unitLabel = "kHz"
    );

    fun formatFrequency(khz: Int): String {
        return when (this) {
            FM -> {
                val integerPart = khz / 1000
                val decimalPart = (khz % 1000) / 100
                "$integerPart.$decimalPart"
            }
            AM -> khz.toString()
        }
    }

    fun stepFrequency(currentKhz: Int, stepUp: Boolean, stepKhz: Int = defaultStepKhz): Int {
        val next = if (stepUp) currentKhz + stepKhz else currentKhz - stepKhz
        return when {
            next > maxFrequencyKhz -> minFrequencyKhz
            next < minFrequencyKhz -> maxFrequencyKhz
            else -> next
        }
    }
}
