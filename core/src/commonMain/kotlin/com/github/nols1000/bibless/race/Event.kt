package com.github.nols1000.bibless.race

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How finishers get their positions. */
@Serializable
enum class TimingMode {
    /** Scanners pair each athlete with a numbered finish token; the token is the position. */
    TOKENS,

    /** No tokens: athletes are scanned in the order they finish. */
    SCAN_ORDER,
}

/**
 * Something that happened at a run. Events are never changed: mistakes are undone with `Remove…`
 * events. [id] is a UUID; [timestamp] is epoch milliseconds.
 */
@Serializable
sealed interface Event {
    val id: String
    val timestamp: Long
}

/**
 * The organiser creates a run. Its [id] is the run ID, and its author is the run's root key.
 * [date] is the day the run takes place (ISO `yyyy-MM-dd`), if the organiser set one.
 */
@Serializable
@SerialName("created")
data class CreatedEvent(
    override val id: String,
    override val timestamp: Long,
    val name: String,
    val format: TimingMode,
    val date: String? = null,
) : Event

/**
 * A timer or scanner session starts; finish times are measured from its [timestamp]. [run] is the
 * run the device joined, if any. It only says what the device meant to record for: the organiser
 * still decides by certifying the chain.
 */
@Serializable
@SerialName("start")
data class StartEvent(override val id: String, override val timestamp: Long, val run: String? = null) : Event

/**
 * A runner finishes. [timestamp] must be the session's start plus elapsed time from a monotonic
 * clock, so that changing the device clock mid-run can't move finish times.
 */
@Serializable
@SerialName("recordTime")
data class RecordTimeEvent(override val id: String, override val timestamp: Long) : Event

@Serializable
@SerialName("removeTime")
data class RemoveTimeEvent(
    override val id: String,
    override val timestamp: Long,
    val recordTimeEventId: String,
) : Event

/**
 * A finisher is recorded. In [TimingMode.TOKENS] [position] is the token number; in
 * [TimingMode.SCAN_ORDER] it is usually null and the order of the records gives the position.
 * Without an [athleteId] the finisher is unknown.
 */
@Serializable
@SerialName("recordAthlete")
data class RecordAthleteEvent(
    override val id: String,
    override val timestamp: Long,
    val athleteId: String? = null,
    val position: Int? = null,
) : Event

@Serializable
@SerialName("removeAthlete")
data class RemoveAthleteEvent(
    override val id: String,
    override val timestamp: Long,
    val recordAthleteEventId: String,
) : Event
