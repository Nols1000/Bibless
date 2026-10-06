package com.github.nols1000.bibless.race

import kotlinx.serialization.Serializable

/**
 * One finish position. [elapsedMs] is null when no time was recorded for it, [athleteEventId] when
 * nobody was; a finisher recorded without an athlete ID is unknown, and [name] is what they told
 * the scanner, if anything.
 */
@Serializable
data class ResultRow(
    val position: Int,
    val elapsedMs: Long? = null,
    val athleteId: String? = null,
    val timeEventId: String? = null,
    val athleteEventId: String? = null,
    val name: String? = null,
)

/** Something for the organiser to check before publishing. */
sealed interface ResultIssue {
    data class MissingTime(val position: Int) : ResultIssue
    data class MissingAthlete(val position: Int) : ResultIssue
    data class DuplicateAthlete(val athleteId: String, val positions: List<Int>) : ResultIssue

    /** In token mode, several finishers for one position; the earliest recorded is used. */
    data class DuplicatePosition(val position: Int, val eventIds: List<String>) : ResultIssue

    /** In token mode, a finisher recorded without a token. */
    data class MissingPosition(val eventId: String) : ResultIssue

    /** In scan-order mode, the number of times and finishers differ, so positions may be shifted. */
    data class CountMismatch(val times: Int, val athletes: Int) : ResultIssue

    /** Several sessions recorded times; [used] gives the positions. */
    data class SeveralTimers(val chains: List<String>, val used: String) : ResultIssue

    /** In scan-order mode, several sessions recorded finishers in order, and their order was merged by time. */
    data class SeveralScannersInScanOrder(val chains: List<String>) : ResultIssue
}

data class Results(val timerChain: String?, val rows: List<ResultRow>, val issues: List<ResultIssue>)

/**
 * Joins times and finishers by position.
 *
 * Times come from one timer session, [timerChain] or else the one with the most times, plus any
 * times the organiser recorded in the run's own chain; elapsed time is measured from that
 * session's start, and the n-th time is position n.
 *
 * In [TimingMode.TOKENS] finishers take their token's position. In [TimingMode.SCAN_ORDER]
 * finishers without a position line up in the order they were recorded; then each one with a
 * position is put in at that position, in the order they were recorded. So an organiser can insert
 * a missed finisher or move one, everyone between shifts by one, and later corrections build on
 * earlier ones.
 */
fun combineResults(state: RunState, timerChain: String? = null): Results {
    val issues = mutableListOf<ResultIssue>()

    val timers = state.times.filter { it.chain != state.run && it.chain in state.starts }
        .groupBy { it.chain }
    val timer = timerChain?.takeIf { it in timers }
        ?: timers.entries.sortedWith(compareByDescending<Map.Entry<String, List<TimeRecord>>> { it.value.size }.thenBy { it.key })
            .firstOrNull()?.key
    if (timers.size > 1 && timer != null) issues += ResultIssue.SeveralTimers(timers.keys.sorted(), timer)
    val times = if (timer == null) {
        emptyList()
    } else {
        val start = state.starts.getValue(timer)
        (timers.getValue(timer) + state.times.filter { it.chain == state.run })
            .map { it to it.timestamp - start }
            .sortedWith(compareBy({ it.second }, { it.first.eventId }))
    }

    val placed = state.athletes.filter { it.position != null && it.position > 0 }.sortedWith(recordOrder)
    val unplaced = state.athletes.filter { it.position == null || it.position <= 0 }.sortedWith(recordOrder)
    val byPosition = mutableMapOf<Int, AthleteRecord>()
    when (state.format) {
        TimingMode.TOKENS -> {
            placed.groupBy { it.position!! }.entries.sortedBy { it.key }.forEach { (position, records) ->
                if (records.size > 1) issues += ResultIssue.DuplicatePosition(position, records.map { it.eventId })
                byPosition[position] = records.first()
            }
            unplaced.forEach { issues += ResultIssue.MissingPosition(it.eventId) }
        }
        TimingMode.SCAN_ORDER -> {
            val scanners = unplaced.map { it.chain }.filter { it != state.run }.distinct().sorted()
            if (scanners.size > 1) issues += ResultIssue.SeveralScannersInScanOrder(scanners)
            val order = unplaced.toMutableList<AthleteRecord?>()
            for (record in placed) {
                val index = record.position!! - 1
                while (order.size < index) order += null
                order.add(index, record)
            }
            order.forEachIndexed { index, record -> if (record != null) byPosition[index + 1] = record }
            if (times.size != byPosition.size) issues += ResultIssue.CountMismatch(times.size, byPosition.size)
        }
    }

    val last = maxOf(times.size, byPosition.keys.maxOrNull() ?: 0)
    val rows = (1..last).map { position ->
        val time = times.getOrNull(position - 1)
        val athlete = byPosition[position]
        if (time == null) issues += ResultIssue.MissingTime(position)
        if (athlete == null) issues += ResultIssue.MissingAthlete(position)
        ResultRow(position, time?.second, athlete?.athleteId, time?.first?.eventId, athlete?.eventId, athlete?.name)
    }
    rows.filter { it.athleteId != null }.groupBy { it.athleteId!! }.entries.sortedBy { it.key }.forEach { (athleteId, found) ->
        if (found.size > 1) issues += ResultIssue.DuplicateAthlete(athleteId, found.map { it.position })
    }
    return Results(timer, rows, issues)
}

private val recordOrder = compareBy<AthleteRecord>({ it.timestamp }, { it.eventId })
