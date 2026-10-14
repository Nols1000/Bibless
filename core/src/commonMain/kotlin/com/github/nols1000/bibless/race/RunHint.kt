package com.github.nols1000.bibless.race

import kotlinx.serialization.Serializable

/**
 * What a run QR code tells a volunteer's device: which run, who organises it, and how it is timed.
 * It is only a hint; whether a device's data counts is decided by the organiser's certificates.
 */
@Serializable
data class RunHint(
    val run: String,
    val root: String,
    val name: String,
    val format: TimingMode,
    /** The day of the run (ISO `yyyy-MM-dd`), if the organiser set one. */
    val date: String? = null,
) {
    fun toJson(): String = raceJson.encodeToString(serializer(), this)

    companion object {
        fun fromJson(json: String): RunHint? = runCatching { raceJson.decodeFromString(serializer(), json) }.getOrNull()
    }
}
