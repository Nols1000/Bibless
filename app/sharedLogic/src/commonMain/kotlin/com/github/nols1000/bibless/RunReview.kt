package com.github.nols1000.bibless

import com.github.nols1000.bibless.race.AthleteRecord
import com.github.nols1000.bibless.race.ResultRow
import com.github.nols1000.bibless.race.Results

/** A timer or scanner session in a run's data, as the organiser reviews it. */
data class RunSession(
    /** The session's chain ID. */
    val id: String,
    /** The device it was exported from, if the file said. */
    val source: String?,
    /** What it recorded; null while it has recorded nothing. */
    val kind: SessionKind?,
    /** When it started, in epoch milliseconds. */
    val startedAt: Long,
    /** The run its volunteer joined, if any. */
    val run: String?,
    /** Finish times or finishers still in effect. */
    val count: Int,
    /** Whether the organiser has certified all of it for this run. */
    val included: Boolean,
)

/** Everything the organiser checks before sharing a run's results. */
data class RunReview(
    val run: RunSummary,
    /** Every session imported into the run, oldest first. */
    val sessions: List<RunSession>,
    val results: Results,
    /** The finishers in effect, so issues that name events can show who they are. */
    val finishers: List<AthleteRecord>,
    /** Entries left out because their signature or chain doesn't check out. */
    val unverified: Int,
)

/** The results as CSV for people: one line per position. */
fun resultsCsv(rows: List<ResultRow>): String = buildString {
    appendLine("position,time,elapsed_ms,athlete_id,name")
    rows.forEach { row ->
        appendLine(
            listOf(
                row.position.toString(),
                row.elapsedMs?.let { formatElapsed(it) }.orEmpty(),
                row.elapsedMs?.toString().orEmpty(),
                row.athleteId.orEmpty(),
                csvField(row.name.orEmpty()),
            ).joinToString(","),
        )
    }
}
