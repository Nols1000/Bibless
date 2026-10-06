package com.github.nols1000.bibless

import com.github.nols1000.bibless.race.RecordTimeEvent
import com.github.nols1000.bibless.race.RemoveTimeEvent
import com.github.nols1000.bibless.race.RunLog
import com.github.nols1000.bibless.race.StartEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** A recorded finish: [position] counts the finishes still in effect, in order. */
data class FinishTime(val eventId: String, val position: Int, val elapsedMs: Long)

/** The timer session that is running, or null when the timer is stopped. */
data class TimerState(val session: TimerSession?)

data class TimerSession(
    /** The session's chain ID, the ID of its [StartEvent]. */
    val id: String,
    /** When the clock started, in epoch milliseconds. */
    val startedAt: Long,
    /** Finishes in order, without undone ones. */
    val times: List<FinishTime>,
)

/**
 * The phone stopwatch. Each tap is saved before the method returns and signed right after, see
 * [SessionLog]. Finish times are the session start plus elapsed time from the monotonic clock.
 */
class TimerRepository(device: RaceDevice) {
    private val sessions = SessionLog(device, "timer") { publish() }
    private val _state = MutableStateFlow(TimerState(sessions.current?.session()))
    val state: StateFlow<TimerState> = _state.asStateFlow()

    private val listeners = MutableStateFlow(emptyList<(TimerState) -> Unit>())

    /** Milliseconds since the clock started, or null when the timer is stopped. */
    fun elapsedMs(): Long? = sessions.elapsed()

    /** Starts the clock. Does nothing while a session runs. */
    fun start() = sessions.start()

    /** Records the next finisher at the current elapsed time; null when the timer is stopped. */
    fun tap(): FinishTime? {
        val event = sessions.append { id, timestamp -> RecordTimeEvent(id, timestamp) } ?: return null
        return _state.value.session?.times?.firstOrNull { it.eventId == event.id }
    }

    /** Undoes the last finish still in effect. */
    fun undo() {
        val last = _state.value.session?.times?.lastOrNull() ?: return
        sessions.append { id, timestamp -> RemoveTimeEvent(id, timestamp, last.eventId) }
    }

    /** Stops the timer. The session stays stored for export. */
    fun stop() = sessions.stop()

    /** IDs of every timer session recorded on this device, oldest first. */
    fun sessionIds(): List<String> = sessions.sessionIds()

    /** The signed events of session [id], for export. Events not signed yet are signed first. */
    suspend fun log(id: String): RunLog? = sessions.log(id)

    fun observe(listener: (TimerState) -> Unit): AutoCloseable {
        listeners.update { it + listener }
        listener(_state.value)
        return AutoCloseable { listeners.update { it - listener } }
    }

    private fun publish() {
        val state = TimerState(sessions.current?.session())
        _state.value = state
        listeners.value.forEach { it(state) }
    }

    private fun SessionRecord.session(): TimerSession {
        val removed = events.filterIsInstance<RemoveTimeEvent>().map { it.recordTimeEventId }.toSet()
        val times = events.filterIsInstance<RecordTimeEvent>()
            .filter { it.id !in removed }
            .sortedBy { it.timestamp }
            .mapIndexed { index, event -> FinishTime(event.id, index + 1, event.timestamp - startedAt) }
        return TimerSession(id, startedAt, times)
    }
}
