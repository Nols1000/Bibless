package com.github.nols1000.bibless

import com.github.nols1000.bibless.race.RecordAthleteEvent
import com.github.nols1000.bibless.race.RemoveAthleteEvent
import com.github.nols1000.bibless.race.RunLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What a scanned or typed code stands for. */
sealed interface ScannedCode {
    data class Athlete(val athleteId: String) : ScannedCode

    /** A finish token; its number is the finish position. */
    data class Token(val position: Int) : ScannedCode

    data class Unknown(val text: String) : ScannedCode
}

private val tokenPattern = Regex("""P0*([1-9][0-9]{0,4})""", RegexOption.IGNORE_CASE)

/** Finish tokens read `P` and the position, like parkrun's (`P0042`); athlete IDs as in [normalizeAthleteId]. */
fun parseScannedCode(text: String): ScannedCode {
    val trimmed = text.trim()
    normalizeAthleteId(trimmed)?.let { return ScannedCode.Athlete(it) }
    tokenPattern.matchEntire(trimmed)?.let { return ScannedCode.Token(it.groupValues[1].toInt()) }
    return ScannedCode.Unknown(trimmed)
}

/** A finisher recorded by this scanner, still in effect. */
data class ScanRecord(val eventId: String, val athleteId: String?, val position: Int?)

data class ScannerState(val session: ScannerSession?)

data class ScannerSession(
    /** The session's chain ID. */
    val id: String,
    val startedAt: Long,
    /** Finishers in the order they were recorded, without undone ones. */
    val records: List<ScanRecord>,
    /** The half of a pair scanned so far: an athlete waiting for a token, or a token for an athlete. */
    val pendingAthleteId: String?,
    val pendingPosition: Int?,
)

enum class ScanWarning {
    /** This athlete was already recorded in this session. */
    DUPLICATE_ATHLETE,

    /** This token was already recorded in this session. */
    DUPLICATE_TOKEN,

    /** A second athlete came before a token; the first one was dropped. */
    REPLACED_ATHLETE,

    /** A second token came before an athlete; the first one was dropped. */
    REPLACED_TOKEN,
}

/** What a scan did, for the scanner's feedback. */
sealed interface ScanOutcome {
    /** A pair was recorded. Warnings deserve the error sound. */
    data class Recorded(val record: ScanRecord, val warnings: List<ScanWarning>) : ScanOutcome

    /** Half a pair; the other half is still missing. */
    data class Waiting(val athleteId: String?, val position: Int?, val warnings: List<ScanWarning>) : ScanOutcome

    /** The code is still in front of the camera from the last scan; nothing happened. */
    data object Repeat : ScanOutcome

    data class NotRecognised(val text: String) : ScanOutcome

    data object NotRunning : ScanOutcome
}

/**
 * The finish scanner: pairs a runner's athlete barcode with their finish token, in either order.
 * Pairs are saved and signed like the timer's taps, see [SessionLog].
 */
class ScannerRepository(private val device: RaceDevice) {
    private val sessions = SessionLog(device, "scanner") { publish() }
    private var pendingAthleteId: String? = null
    private var pendingPosition: Int? = null
    private var lastCode: String? = null
    private var lastSeenAt = 0L

    private val _state = MutableStateFlow(ScannerState(currentSession()))
    val state: StateFlow<ScannerState> = _state.asStateFlow()

    private val listeners = MutableStateFlow(emptyList<(ScannerState) -> Unit>())

    fun start() = sessions.start()

    /**
     * Handles a code from the camera. The camera sees the same code many times while it is in
     * view, so a code is only taken again after it was out of view for a moment; [enter] takes
     * typed codes every time.
     */
    fun scan(text: String): ScanOutcome {
        val now = device.clock.monotonicMillis()
        val repeat = text == lastCode && now - lastSeenAt in 0 until REPEAT_WINDOW_MS
        lastCode = text
        lastSeenAt = now
        return if (repeat) ScanOutcome.Repeat else enter(text)
    }

    /** Handles a typed code, e.g. a barcode too damaged to scan. */
    fun enter(text: String): ScanOutcome {
        sessions.current ?: return ScanOutcome.NotRunning
        val records = currentSession()?.records.orEmpty()
        return when (val code = parseScannedCode(text)) {
            is ScannedCode.Unknown -> ScanOutcome.NotRecognised(code.text)
            is ScannedCode.Athlete -> {
                if (code.athleteId == pendingAthleteId) return ScanOutcome.Repeat
                val warnings = buildList {
                    if (records.any { it.athleteId == code.athleteId }) add(ScanWarning.DUPLICATE_ATHLETE)
                    if (pendingAthleteId != null) add(ScanWarning.REPLACED_ATHLETE)
                }
                pendingAthleteId = code.athleteId
                complete(warnings)
            }
            is ScannedCode.Token -> {
                if (code.position == pendingPosition) return ScanOutcome.Repeat
                val warnings = buildList {
                    if (records.any { it.position == code.position }) add(ScanWarning.DUPLICATE_TOKEN)
                    if (pendingPosition != null) add(ScanWarning.REPLACED_TOKEN)
                }
                pendingPosition = code.position
                complete(warnings)
            }
        }
    }

    /** Drops the half pair scanned so far, or else undoes the last recorded pair. */
    fun undo() {
        if (pendingAthleteId != null || pendingPosition != null) {
            clearPending()
            publish()
            return
        }
        val last = currentSession()?.records?.lastOrNull() ?: return
        sessions.append { id, timestamp -> RemoveAthleteEvent(id, timestamp, last.eventId) }
    }

    /** Ends the session. It stays stored for export. */
    fun stop() {
        clearPending()
        sessions.stop()
    }

    fun sessionIds(): List<String> = sessions.sessionIds()

    suspend fun log(id: String): RunLog? = sessions.log(id)

    fun observe(listener: (ScannerState) -> Unit): AutoCloseable {
        listeners.update { it + listener }
        listener(_state.value)
        return AutoCloseable { listeners.update { it - listener } }
    }

    private fun complete(warnings: List<ScanWarning>): ScanOutcome {
        val athleteId = pendingAthleteId
        val position = pendingPosition
        if (athleteId == null || position == null) {
            publish()
            return ScanOutcome.Waiting(athleteId, position, warnings)
        }
        clearPending()
        val event = sessions.append { id, timestamp -> RecordAthleteEvent(id, timestamp, athleteId, position) }!!
        return ScanOutcome.Recorded(ScanRecord(event.id, athleteId, position), warnings)
    }

    private fun clearPending() {
        pendingAthleteId = null
        pendingPosition = null
    }

    private fun publish() {
        val state = ScannerState(currentSession())
        _state.value = state
        listeners.value.forEach { it(state) }
    }

    private fun currentSession(): ScannerSession? {
        val record = sessions.current ?: return null
        val removed = record.events.filterIsInstance<RemoveAthleteEvent>().map { it.recordAthleteEventId }.toSet()
        val records = record.events.filterIsInstance<RecordAthleteEvent>()
            .filter { it.id !in removed }
            .map { ScanRecord(it.id, it.athleteId, it.position) }
        return ScannerSession(record.id, record.startedAt, records, pendingAthleteId, pendingPosition)
    }

    private companion object {
        /** How long a code must be out of view before the camera takes it again. */
        const val REPEAT_WINDOW_MS = 1_500L
    }
}
