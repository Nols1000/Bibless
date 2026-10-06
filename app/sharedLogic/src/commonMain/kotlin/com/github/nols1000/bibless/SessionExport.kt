package com.github.nols1000.bibless

import com.github.nols1000.bibless.race.TimingMode

enum class SessionKind { TIMER, SCANNER }

/** A timer or scanner session recorded on this device, for the list of recorded sessions. */
data class RecordedSession(
    val kind: SessionKind,
    val id: String,
    /** When it started, in epoch milliseconds. */
    val startedAt: Long,
    /** The run it records for, or null for none. */
    val run: String?,
    /** Finish times or finishers still in effect. */
    val count: Int,
    /** Whether it is still recording. */
    val running: Boolean,
    /** For scanner sessions, how positions are given. */
    val mode: TimingMode? = null,
    /** The paired watch it was recorded on; null for this device. */
    val source: String? = null,
)

/** One file to share. */
data class ExportFile(val name: String, val mimeType: String, val content: String)

/**
 * Turns this device's recorded sessions, and those its watch sent, into files for the organiser:
 * a CSV per session for people, and the session's signed events as a JSON bundle that imports
 * without loss.
 */
class SessionExporter(
    private val timer: TimerRepository,
    private val scanner: ScannerRepository,
    private val received: ReceivedSessions? = null,
) {

    /** Every session on this device or from its watch, newest first. */
    fun sessions(): List<RecordedSession> =
        (timer.recordedSessions() + scanner.recordedSessions() + received?.sessions().orEmpty())
            .distinctBy { it.kind to it.id }
            .sortedByDescending { it.startedAt }

    /**
     * The files for session [id]: CSV and JSON bundle. Names carry [deviceName], or the watch's
     * name for a session from the watch, and the start time in local time, which is UTC plus
     * [utcOffsetMinutes]. Null for an unknown session.
     */
    suspend fun export(kind: SessionKind, id: String, deviceName: String, utcOffsetMinutes: Int): List<ExportFile>? {
        val session = sessions().firstOrNull { it.kind == kind && it.id == id } ?: return null
        val fromWatch = session.source != null
        val log = when {
            fromWatch -> received?.log(id)
            kind == SessionKind.TIMER -> timer.log(id)
            else -> scanner.log(id)
        } ?: return null
        val source = session.source ?: deviceName
        val offset = utcOffsetMinutes * 60_000L
        val base = listOf(
            "bibless",
            if (kind == SessionKind.TIMER) "timer" else "scanner",
            localDateTime(session.startedAt + offset).replace(":", "").replace("T", "-").dropLast(2),
            source,
        ).joinToString("-") { part -> part.replace(Regex("[^A-Za-z0-9]+"), "-").trim('-') }
        val csv = when (kind) {
            SessionKind.TIMER -> timerCsv((if (fromWatch) received?.times(id) else timer.recordedTimes(id)).orEmpty())
            SessionKind.SCANNER -> scannerCsv(scanner.recordedFinishers(id).orEmpty(), offset)
        }
        return listOf(
            ExportFile("$base.csv", "text/csv", csv),
            ExportFile("$base.json", "application/json", log.copy(source = source).toJson()),
        )
    }

    private fun timerCsv(times: List<FinishTime>) = buildString {
        appendLine("position,time,elapsed_ms")
        times.forEach { appendLine("${it.position},${formatElapsed(it.elapsedMs)},${it.elapsedMs}") }
    }

    private fun scannerCsv(records: List<Pair<ScanRecord, Long>>, offset: Long) = buildString {
        appendLine("position,athlete_id,name,scanned_at")
        records.forEach { (record, timestamp) ->
            appendLine(
                listOf(
                    record.position?.toString().orEmpty(),
                    record.athleteId.orEmpty(),
                    csvField(record.name.orEmpty()),
                    localDateTime(timestamp + offset),
                ).joinToString(","),
            )
        }
    }
}

/** Quotes a CSV field when it needs it. */
internal fun csvField(value: String): String =
    if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"${value.replace("\"", "\"\"")}\"" else value

/** `yyyy-MM-ddTHH:mm:ss` for [millis] since the epoch, taken as local time already. */
internal fun localDateTime(millis: Long): String {
    val days = millis.floorDiv(86_400_000L)
    val secondOfDay = millis.mod(86_400_000L) / 1000
    // Days to civil date, after Howard Hinnant's algorithm
    val z = days + 719_468
    val era = z.floorDiv(146_097L)
    val doe = z - era * 146_097
    val yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365
    val doy = doe - (365 * yoe + yoe / 4 - yoe / 100)
    val mp = (5 * doy + 2) / 153
    val day = doy - (153 * mp + 2) / 5 + 1
    val month = if (mp < 10) mp + 3 else mp - 9
    val year = yoe + era * 400 + if (month <= 2) 1 else 0
    fun two(n: Long) = n.toString().padStart(2, '0')
    return "$year-${two(month)}-${two(day)}T${two(secondOfDay / 3600)}:${two(secondOfDay / 60 % 60)}:${two(secondOfDay % 60)}"
}
