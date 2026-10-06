package com.github.nols1000.bibless

import com.github.nols1000.bibless.barcode.encodeCode128
import com.github.nols1000.bibless.barcode.encodeQr
import com.github.nols1000.bibless.race.AthleteRecord
import com.github.nols1000.bibless.race.ResultIssue
import com.github.nols1000.bibless.race.ResultRow
import com.github.nols1000.bibless.race.TimingMode
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlin.js.Promise

/** A run for the web app. [format] is `"TOKENS"` or `"SCAN_ORDER"`; [createdAt] is epoch milliseconds. */
@JsExport
class RunInfo(val id: String, val name: String, val date: String?, val format: String, val createdAt: Double)

/** A QR code as one SVG path over a [size] × [size] grid of modules, without the quiet zone. */
@JsExport
class QrPath(val size: Int, val path: String)

/** A finish token: its [text] and its Code 128 bars as one SVG path, [width] modules by 1, without the quiet zone. */
@JsExport
class TokenCode(val position: Int, val text: String, val width: Int, val path: String)

/** A session in a run's data. [kind] is `"TIMER"`, `"SCANNER"` or null; [run] is the run it joined. */
@JsExport
class SessionInfo(
    val id: String,
    val source: String?,
    val kind: String?,
    val startedAt: Double,
    val run: String?,
    val count: Int,
    val included: Boolean,
)

/** A finisher as recorded, for issues that name one. */
@JsExport
class FinisherInfo(val eventId: String, val athleteId: String?, val name: String?, val position: Int?)

/** One results row; [time] is formatted like the apps show it. */
@JsExport
class RowInfo(
    val position: Int,
    val elapsedMs: Double?,
    val time: String?,
    val athleteId: String?,
    val name: String?,
    val timeEventId: String?,
    val athleteEventId: String?,
)

/**
 * Something to check before sharing results. [kind] names the case: `MISSING_TIME`,
 * `MISSING_ATHLETE`, `DUPLICATE_ATHLETE`, `DUPLICATE_POSITION`, `MISSING_POSITION`,
 * `COUNT_MISMATCH`, `SEVERAL_TIMERS` or `SEVERAL_SCANNERS`. Only the fields that case needs are set.
 */
@JsExport
class IssueInfo(
    val kind: String,
    val positions: Array<Int> = emptyArray(),
    val athleteId: String? = null,
    val finishers: Array<FinisherInfo> = emptyArray(),
    val times: Int = 0,
    val athletes: Int = 0,
)

/** A run's sessions, results and issues. [timer] is the session whose times are used. */
@JsExport
class ReviewInfo(
    val run: RunInfo,
    val sessions: Array<SessionInfo>,
    val timer: String?,
    val rows: Array<RowInfo>,
    val issues: Array<IssueInfo>,
    val unverified: Int,
)

/** The organiser's side of [RunRepository] for the web app, with promises instead of suspend functions. */
@JsExport
@OptIn(DelicateCoroutinesApi::class)
class RunOrganiser internal constructor(private val repository: RunRepository) {

    fun listRuns(): Promise<Array<RunInfo>> = GlobalScope.promise { repository.runs().map { it.info() }.toTypedArray() }

    fun getRun(id: String): Promise<RunInfo?> = GlobalScope.promise { repository.run(id)?.info() }

    /** Rejects if [name] is blank or [format] unknown. */
    fun createRun(name: String, date: String?, format: String): Promise<RunInfo> = GlobalScope.promise {
        repository.create(name, date, TimingMode.valueOf(format)).info()
    }

    fun deleteRun(id: String) = repository.delete(id)

    /** The run QR code that volunteers scan to join [id], or null for an unknown run. */
    fun runQr(id: String): Promise<QrPath?> = GlobalScope.promise {
        repository.hint(id)?.let { qrPath(it.toJson()) }
    }

    fun getReview(id: String): Promise<ReviewInfo?> = GlobalScope.promise { repository.review(id)?.info() }

    /** Adds an exported session file to [id]; resolves to the number of new sessions, rejects if it isn't one. */
    fun importLog(id: String, json: String): Promise<Int> = GlobalScope.promise { repository.import(id, json) }

    fun includeSession(id: String, session: String): Promise<Unit> = GlobalScope.promise { repository.include(id, session) }

    fun chooseTimer(id: String, session: String) = repository.chooseTimer(id, session)

    fun addTime(id: String, elapsedMs: Double): Promise<Unit> = GlobalScope.promise { repository.addTime(id, elapsedMs.toLong()) }

    fun removeTime(id: String, timeEventId: String): Promise<Unit> = GlobalScope.promise { repository.removeTime(id, timeEventId) }

    /** Rejects if [athleteId] isn't an athlete ID. */
    fun addFinisher(id: String, position: Int?, athleteId: String?, name: String?): Promise<Unit> =
        GlobalScope.promise { repository.addFinisher(id, position, athleteId, name) }

    /** Rejects if [athleteId] isn't an athlete ID. */
    fun editFinisher(id: String, athleteEventId: String, position: Int?, athleteId: String?, name: String?): Promise<Unit> =
        GlobalScope.promise { repository.editFinisher(id, athleteEventId, position, athleteId, name) }

    fun removeFinisher(id: String, athleteEventId: String): Promise<Unit> =
        GlobalScope.promise { repository.removeFinisher(id, athleteEventId) }

    fun resultsCsv(id: String): Promise<String?> = GlobalScope.promise { repository.resultsCsv(id) }

    /** Finish tokens [from] to [to] for printing; throws if the range is outside 1–99999. */
    fun tokens(from: Int, to: Int): Array<TokenCode> {
        require(from in 1..MAX_TOKEN && to in from..MAX_TOKEN) { "Tokens run from 1 to $MAX_TOKEN" }
        return (from..to).map { tokenCode(it) }.toTypedArray()
    }

    companion object {
        /** An organiser whose runs and key are kept in this browser's local storage. */
        @JsStatic
        fun create() = RunOrganiser(RunRepository(LocalStorageStore()))
    }
}

internal fun qrPath(text: String): QrPath {
    val matrix = encodeQr(text)
    val path = buildString {
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            if (matrix[x, y]) append("M$x ${y}h1v1h-1z")
        }
    }
    return QrPath(matrix.width, path)
}

private fun RunSummary.info() = RunInfo(id, name, date, format.name, createdAt.toDouble())

private fun RunReview.info(): ReviewInfo {
    val byEvent = finishers.associateBy { it.eventId }
    fun finishersOf(ids: List<String>) = ids.mapNotNull { byEvent[it]?.info() }.toTypedArray()
    val issues = results.issues.map { issue ->
        when (issue) {
            is ResultIssue.MissingTime -> IssueInfo("MISSING_TIME", positions = arrayOf(issue.position))
            is ResultIssue.MissingAthlete -> IssueInfo("MISSING_ATHLETE", positions = arrayOf(issue.position))
            is ResultIssue.DuplicateAthlete ->
                IssueInfo("DUPLICATE_ATHLETE", positions = issue.positions.toTypedArray(), athleteId = issue.athleteId)
            is ResultIssue.DuplicatePosition ->
                IssueInfo("DUPLICATE_POSITION", positions = arrayOf(issue.position), finishers = finishersOf(issue.eventIds))
            is ResultIssue.MissingPosition -> IssueInfo("MISSING_POSITION", finishers = finishersOf(listOf(issue.eventId)))
            is ResultIssue.CountMismatch -> IssueInfo("COUNT_MISMATCH", times = issue.times, athletes = issue.athletes)
            is ResultIssue.SeveralTimers -> IssueInfo("SEVERAL_TIMERS")
            is ResultIssue.SeveralScannersInScanOrder -> IssueInfo("SEVERAL_SCANNERS")
        }
    }
    return ReviewInfo(
        run = run.info(),
        sessions = sessions.map { it.info() }.toTypedArray(),
        timer = results.timerChain,
        rows = results.rows.map { it.info() }.toTypedArray(),
        issues = issues.toTypedArray(),
        unverified = unverified,
    )
}

private fun RunSession.info() = SessionInfo(id, source, kind?.name, startedAt.toDouble(), run, count, included)

private fun AthleteRecord.info() = FinisherInfo(eventId, athleteId, name, position)

private fun ResultRow.info() =
    RowInfo(position, elapsedMs?.toDouble(), elapsedMs?.let { formatElapsed(it) }, athleteId, name, timeEventId, athleteEventId)

internal fun tokenCode(position: Int): TokenCode {
    val text = finishToken(position)
    val bars = encodeCode128(text)
    val path = buildString {
        var x = 0
        while (x < bars.width) {
            if (!bars[x, 0]) {
                x++
                continue
            }
            val start = x
            while (x < bars.width && bars[x, 0]) x++
            append("M$start 0h${x - start}v1h-${x - start}z")
        }
    }
    return TokenCode(position, text, bars.width, path)
}
