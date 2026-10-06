package com.github.nols1000.bibless

import kotlinx.serialization.Serializable

/**
 * The clocks a timer needs. Elapsed time comes from [monotonicMillis], which wall-clock changes
 * don't move; it only restarts with the device, which [bootId] tells apart.
 */
interface RaceClock {
    /** Epoch milliseconds. */
    fun wallMillis(): Long

    /** Milliseconds since some point in this boot, counting while the device sleeps. */
    fun monotonicMillis(): Long

    /** Differs after every restart of the device. */
    fun bootId(): String
}

/** [elapsed] milliseconds of the session had passed when the monotonic clock of [boot] read [monotonic]. */
@Serializable
internal data class ClockAnchor(val boot: String, val monotonic: Long, val elapsed: Long)

/**
 * Milliseconds since the anchor's session started. Within the same boot that is monotonic time;
 * after a restart the monotonic clock starts over, so the wall clock since [startedAt] is the best
 * there is, and the result says to re-anchor there.
 */
internal fun RaceClock.elapsedSince(anchor: ClockAnchor, startedAt: Long): Pair<Long, ClockAnchor?> {
    val boot = bootId()
    val monotonic = monotonicMillis()
    if (boot == anchor.boot && monotonic >= anchor.monotonic) {
        return anchor.elapsed + (monotonic - anchor.monotonic) to null
    }
    val elapsed = maxOf(anchor.elapsed, wallMillis() - startedAt)
    return elapsed to ClockAnchor(boot, monotonic, elapsed)
}

/** Formats elapsed time the way results show it: `m:ss`, or `h:mm:ss` from an hour on. */
fun formatElapsed(millis: Long): String {
    val seconds = maxOf(0, millis) / 1000
    val h = seconds / 3600
    val m = seconds / 60 % 60
    val s = (seconds % 60).toString().padStart(2, '0')
    return if (h > 0) "$h:${m.toString().padStart(2, '0')}:$s" else "$m:$s"
}
