package com.github.nols1000.bibless

import com.github.nols1000.bibless.race.RecordAthleteEvent
import com.github.nols1000.bibless.race.RemoveAthleteEvent
import com.github.nols1000.bibless.race.RunLog
import com.github.nols1000.bibless.race.TimingMode
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

/** The highest position a finish token can carry. */
const val MAX_TOKEN = 99_999

/** What finish token [position] reads, zero-padded like parkrun's: `P0042`. */
fun finishToken(position: Int): String {
    require(position in 1..MAX_TOKEN) { "Tokens run from 1 to $MAX_TOKEN" }
    return "P" + position.toString().padStart(4, '0')
}

/** Finish tokens read `P` and the position, like parkrun's (`P0042`); athlete IDs as in [normalizeAthleteId]. */
fun parseScannedCode(text: String): ScannedCode {
    val trimmed = text.trim()
    normalizeAthleteId(trimmed)?.let { return ScannedCode.Athlete(it) }
    tokenPattern.matchEntire(trimmed)?.let { return ScannedCode.Token(it.groupValues[1].toInt()) }
    return ScannedCode.Unknown(trimmed)
}

/**
 * A finisher recorded by this scanner, still in effect. [position] is the token number, or in
 * scan order the place in the order of records. Without [athleteId] the finisher is unknown, and
 * [name] is what they told the scanner, if anything.
 */
data class ScanRecord(val eventId: String, val athleteId: String?, val position: Int?, val name: String? = null)

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
    /** The run the session records for, or null for none. */
    val run: String? = null,
    val mode: TimingMode = TimingMode.TOKENS,
) {
    /** The position the next finisher gets in scan order. */
    val nextPosition: Int get() = records.size + 1
}

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

    /** A finish token, but the session records in scan order. */
    data object TokenInScanOrder : ScanOutcome

    /** An unknown finisher with tokens needs their token scanned first. */
    data object MissingToken : ScanOutcome

    data object NotRunning : ScanOutcome
}

/**
 * The finish scanner. With [TimingMode.TOKENS] it pairs a runner's athlete barcode with their
 * finish token, in either order; in [TimingMode.SCAN_ORDER] each runner takes the next position
 * as they are scanned. Records are saved and signed like the timer's taps, see [SessionLog].
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

    /** Starts a session that gives positions by [mode]; the joined run's mode, if there is one. */
    fun start(mode: TimingMode) = sessions.start(mode)

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
        val session = currentSession() ?: return ScanOutcome.NotRunning
        val records = session.records
        val code = parseScannedCode(text)
        if (session.mode == TimingMode.SCAN_ORDER) {
            return when (code) {
                is ScannedCode.Unknown -> ScanOutcome.NotRecognised(code.text)
                is ScannedCode.Token -> ScanOutcome.TokenInScanOrder
                is ScannedCode.Athlete -> {
                    val warnings = if (records.any { it.athleteId == code.athleteId }) listOf(ScanWarning.DUPLICATE_ATHLETE) else emptyList()
                    record(code.athleteId, null, null, session.nextPosition, warnings)
                }
            }
        }
        return when (code) {
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

    /**
     * Records a finisher without a barcode, with the [name] they gave, if any. In scan order they
     * take the next position; with tokens, the token scanned last.
     */
    fun addUnknown(name: String?): ScanOutcome {
        val session = currentSession() ?: return ScanOutcome.NotRunning
        val given = name?.trim()?.takeIf { it.isNotEmpty() }
        if (session.mode == TimingMode.SCAN_ORDER) return record(null, null, given, session.nextPosition, emptyList())
        val position = pendingPosition ?: return ScanOutcome.MissingToken
        clearPending()
        return record(null, position, given, position, emptyList())
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

    /** Every scanner session on this device, for export. */
    fun recordedSessions(): List<RecordedSession> = sessionIds().mapNotNull { id ->
        sessions.record(id)?.let { record ->
            val mode = record.mode ?: TimingMode.TOKENS
            RecordedSession(SessionKind.SCANNER, id, record.startedAt, record.run, records(record).size, sessions.current?.id == id, mode)
        }
    }

    /** The finishers of session [id] with the time each was recorded, running or not. */
    fun recordedFinishers(id: String): List<Pair<ScanRecord, Long>>? {
        val record = sessions.record(id) ?: return null
        val times = record.events.associate { it.id to it.timestamp }
        return records(record).map { it to times.getValue(it.eventId) }
    }

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
        return record(athleteId, position, null, position, warnings)
    }

    /** Saves a finisher; [shown] is the position they get on screen. */
    private fun record(athleteId: String?, position: Int?, name: String?, shown: Int, warnings: List<ScanWarning>): ScanOutcome {
        val event = sessions.append { id, timestamp -> RecordAthleteEvent(id, timestamp, athleteId, position, name) }!!
        return ScanOutcome.Recorded(ScanRecord(event.id, athleteId, shown, name), warnings)
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
        val mode = record.mode ?: TimingMode.TOKENS
        return ScannerSession(record.id, record.startedAt, records(record), pendingAthleteId, pendingPosition, record.run, mode)
    }

    private fun records(record: SessionRecord): List<ScanRecord> {
        val removed = record.events.filterIsInstance<RemoveAthleteEvent>().map { it.recordAthleteEventId }.toSet()
        val mode = record.mode ?: TimingMode.TOKENS
        return record.events.filterIsInstance<RecordAthleteEvent>()
            .filter { it.id !in removed }
            .mapIndexed { index, event ->
                val position = if (mode == TimingMode.SCAN_ORDER) index + 1 else event.position
                ScanRecord(event.id, event.athleteId, position, event.name)
            }
    }

    private companion object {
        /** How long a code must be out of view before the camera takes it again. */
        const val REPEAT_WINDOW_MS = 1_500L
    }
}
