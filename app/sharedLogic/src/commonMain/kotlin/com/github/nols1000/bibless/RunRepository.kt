package com.github.nols1000.bibless

import com.github.nols1000.bibless.race.ChainWriter
import com.github.nols1000.bibless.race.CreatedEvent
import com.github.nols1000.bibless.race.Entry
import com.github.nols1000.bibless.race.Event
import com.github.nols1000.bibless.race.RecordAthleteEvent
import com.github.nols1000.bibless.race.RecordTimeEvent
import com.github.nols1000.bibless.race.RemoveAthleteEvent
import com.github.nols1000.bibless.race.RemoveTimeEvent
import com.github.nols1000.bibless.race.RunHint
import com.github.nols1000.bibless.race.RunLog
import com.github.nols1000.bibless.race.RunProblem
import com.github.nols1000.bibless.race.StartEvent
import com.github.nols1000.bibless.race.TimingMode
import com.github.nols1000.bibless.race.certify
import com.github.nols1000.bibless.race.combineResults
import com.github.nols1000.bibless.race.crypto.DeviceKey
import com.github.nols1000.bibless.race.foldRun
import com.github.nols1000.bibless.race.verify
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** A run as the organiser's run list shows it. */
data class RunSummary(
    val id: String,
    val name: String,
    /** The day of the run (ISO `yyyy-MM-dd`), if set. */
    val date: String?,
    val format: TimingMode,
    /** When the run was created, in epoch milliseconds. */
    val createdAt: Long,
)

/**
 * The runs organised on this device. Each run is its signed [RunLog], and this device's
 * organiser key is the root of every run it creates.
 */
class RunRepository(
    private val store: KeyValueStore,
    private val now: () -> Long,
) {
    constructor(store: KeyValueStore) : this(store, { Clock.System.now().toEpochMilliseconds() })

    private var key: DeviceKey? = null

    /** This device's organiser key, created on first use. */
    suspend fun organiserKey(): DeviceKey = key ?: (
        store.get(KEY_ORGANISER)?.takeIf { it.isNotEmpty() }?.let { DeviceKey.import(it) }
            ?: DeviceKey.generate().also { store.put(KEY_ORGANISER, it.export()) }
        ).also { key = it }

    /** Creates a run, or throws [IllegalArgumentException] if [name] is blank. */
    @OptIn(ExperimentalUuidApi::class)
    @Throws(IllegalArgumentException::class, CancellationException::class)
    suspend fun create(name: String, date: String?, format: TimingMode): RunSummary {
        require(name.isNotBlank()) { "A run needs a name" }
        val created = CreatedEvent(Uuid.random().toString(), now(), name.trim(), format, date?.takeIf { it.isNotBlank() })
        val genesis = ChainWriter.start(organiserKey(), created).append(created)
        store.put(runKey(created.id), RunLog(events = listOf(genesis)).toJson())
        saveIndex(listOf(created.id) + index())
        return created.summary()
    }

    /** The runs on this device, newest first. */
    suspend fun runs(): List<RunSummary> = index().mapNotNull { run(it) }.sortedByDescending { it.createdAt }

    suspend fun run(id: String): RunSummary? = genesis(id)?.event?.let { (it as CreatedEvent).summary() }

    fun log(id: String): RunLog? =
        store.get(runKey(id))?.takeIf { it.isNotEmpty() }?.let { runCatching { RunLog.fromJson(it) }.getOrNull() }

    /** What the run QR code carries, so volunteers' devices can join [id]. */
    suspend fun hint(id: String): RunHint? {
        val genesis = genesis(id) ?: return null
        val created = genesis.event as CreatedEvent
        return RunHint(id, genesis.author, created.name, created.format, created.date)
    }

    fun delete(id: String) {
        saveIndex(index() - id)
        listOf(runKey(id), sourcesKey(id), includedKey(id), timerKey(id)).forEach { store.put(it, "") }
    }

    /**
     * Adds a volunteer's exported [RunLog] to run [id] and returns how many sessions are new to it.
     * Sessions whose volunteer joined this run are included straight away, as are newer entries of
     * sessions included before. Importing the same file twice changes nothing. Throws
     * [IllegalArgumentException] if [json] isn't a run log or the run isn't stored here.
     */
    @Throws(IllegalArgumentException::class, CancellationException::class)
    suspend fun import(id: String, json: String): Int {
        val log = requireLog(id)
        val imported = RunLog.fromJson(json)
        val before = sessionIds(log)
        val added = sessionIds(imported) - before
        imported.source?.let { source -> saveSources(id, added.associateWith { source } + sources(id)) }
        save(id, certifyIncluded(id, log.merge(imported).copy(source = null)))
        return added.size
    }

    /** Includes session [session] in run [id], for data recorded without joining the run. */
    @Throws(IllegalArgumentException::class, CancellationException::class)
    suspend fun include(id: String, session: String) {
        val log = requireLog(id)
        require(session in sessionIds(log)) { "No such session" }
        saveIncluded(id, included(id) + session)
        save(id, certifyIncluded(id, log))
    }

    /** Uses [session]'s times for run [id], instead of the timer session with the most times. */
    fun chooseTimer(id: String, session: String) = store.put(timerKey(id), session)

    /** The run's sessions, results and issues, or null for an unknown run. */
    suspend fun review(id: String): RunReview? {
        val summary = run(id) ?: return null
        val log = requireLog(id)
        val state = foldRun(log, id) ?: return null
        val verification = log.verify()
        val uncertified = state.problems.filterIsInstance<RunProblem.Uncertified>().map { it.chain }.toSet()
        val sources = sources(id)
        val sessions = verification.entries.filter { it.entry.chain != id }.groupBy { it.entry.chain }.mapNotNull { (chain, entries) ->
            val start = entries.firstOrNull { it.entry.seq == 0L }?.entry?.event as? StartEvent ?: return@mapNotNull null
            val events = entries.map { it.entry.event }
            val removed = events.mapNotNull {
                when (it) {
                    is RemoveTimeEvent -> it.recordTimeEventId
                    is RemoveAthleteEvent -> it.recordAthleteEventId
                    else -> null
                }
            }.toSet()
            val times = events.count { it is RecordTimeEvent && it.id !in removed }
            val finishers = events.count { it is RecordAthleteEvent && it.id !in removed }
            val kind = when {
                events.any { it is RecordTimeEvent } -> SessionKind.TIMER
                events.any { it is RecordAthleteEvent } -> SessionKind.SCANNER
                else -> null
            }
            RunSession(chain, sources[chain], kind, start.timestamp, start.run, times + finishers, chain !in uncertified)
        }.sortedWith(compareBy({ it.startedAt }, { it.id }))
        return RunReview(
            run = summary,
            sessions = sessions,
            results = combineResults(state, chosenTimer(id)),
            finishers = state.athletes,
            unverified = state.problems.count { it is RunProblem.Log },
        )
    }

    /** The organiser adds a finish time, [elapsedMs] after the start of the timer in use. */
    @Throws(IllegalArgumentException::class, CancellationException::class)
    suspend fun addTime(id: String, elapsedMs: Long) {
        require(elapsedMs >= 0) { "A time can't be negative" }
        val state = foldRun(requireLog(id), id) ?: throw IllegalArgumentException("Unknown run")
        val timer = combineResults(state, chosenTimer(id)).timerChain
            ?: throw IllegalArgumentException("There are no times to add to")
        val start = state.starts.getValue(timer)
        appendAll(id, listOf(RecordTimeEvent(newId(), start + elapsedMs)))
    }

    @Throws(IllegalArgumentException::class, CancellationException::class)
    suspend fun removeTime(id: String, timeEventId: String) =
        appendAll(id, listOf(RemoveTimeEvent(newId(), now(), timeEventId)))

    /**
     * The organiser records a finisher at [position], or after everyone else without one. In scan
     * order everyone from [position] on moves down one. [athleteId] is normalized; throws
     * [IllegalArgumentException] if it isn't an athlete ID.
     */
    @Throws(IllegalArgumentException::class, CancellationException::class)
    suspend fun addFinisher(id: String, position: Int?, athleteId: String?, name: String?) =
        appendAll(id, listOf(finisher(position, athleteId, name)))

    /** Replaces finisher [athleteEventId] with one at [position] with [athleteId] and [name]. */
    @Throws(IllegalArgumentException::class, CancellationException::class)
    suspend fun editFinisher(id: String, athleteEventId: String, position: Int?, athleteId: String?, name: String?) {
        val replacement = finisher(position, athleteId, name)
        appendAll(id, listOf(RemoveAthleteEvent(newId(), now(), athleteEventId), replacement))
    }

    @Throws(IllegalArgumentException::class, CancellationException::class)
    suspend fun removeFinisher(id: String, athleteEventId: String) =
        appendAll(id, listOf(RemoveAthleteEvent(newId(), now(), athleteEventId)))

    /** The results as CSV, or null for an unknown run. */
    suspend fun resultsCsv(id: String): String? = review(id)?.results?.rows?.let { resultsCsv(it) }

    private fun finisher(position: Int?, athleteId: String?, name: String?): RecordAthleteEvent {
        require(position == null || position > 0) { "Positions start at 1" }
        val normalized = athleteId?.takeIf { it.isNotBlank() }?.let {
            normalizeAthleteId(it) ?: throw IllegalArgumentException("Not an athlete ID: $it")
        }
        return RecordAthleteEvent(newId(), now(), normalized, position, name?.trim()?.takeIf { it.isNotEmpty() })
    }

    /** Signs [events] into the run's own chain, after its last entry. */
    private suspend fun appendAll(id: String, events: List<Event>) {
        val log = requireLog(id)
        val key = organiserKey()
        val last = log.verify().entries.filter { it.entry.chain == id && it.entry.author == key.author }
            .maxByOrNull { it.entry.seq } ?: throw IllegalArgumentException("Not organised here")
        val writer = ChainWriter.resume(key, last)
        save(id, log.merge(RunLog(events = events.map { writer.append(it) })))
    }

    /** Certifies the newest entries of every session that joined [id] or that the organiser included. */
    private suspend fun certifyIncluded(id: String, log: RunLog): RunLog {
        val verification = log.verify()
        val joined = verification.entries.mapNotNull { e -> (e.entry.event as? StartEvent)?.takeIf { it.run == id }?.let { e.entry.chain } }
        val included = included(id) + joined
        val byHash = verification.entries.associateBy { it.hash }
        val state = foldRun(log, id) ?: return log
        val certificates = state.problems.filterIsInstance<RunProblem.Uncertified>()
            .filter { it.chain in included }
            .map { certify(organiserKey(), id, byHash.getValue(it.head), now()) }
        return if (certificates.isEmpty()) log else log.merge(RunLog(certificates = certificates))
    }

    /** The sessions in [log], by chain: every chain that starts with a [StartEvent]. */
    private suspend fun sessionIds(log: RunLog): Set<String> = log.verify().entries
        .filter { it.entry.seq == 0L && it.entry.event is StartEvent }.map { it.entry.chain }.toSet()

    private fun chosenTimer(id: String) = store.get(timerKey(id))?.takeIf { it.isNotEmpty() }

    private fun requireLog(id: String) = log(id) ?: throw IllegalArgumentException("Unknown run")

    private fun save(id: String, log: RunLog) = store.put(runKey(id), log.toJson())

    @OptIn(ExperimentalUuidApi::class)
    private fun newId() = Uuid.random().toString()

    private fun sources(id: String): Map<String, String> = store.get(sourcesKey(id))
        ?.let { runCatching { Json.decodeFromString<Map<String, String>>(it) }.getOrNull() }.orEmpty()

    private fun saveSources(id: String, sources: Map<String, String>) = store.put(sourcesKey(id), Json.encodeToString(sources))

    private fun included(id: String): Set<String> = store.get(includedKey(id))
        ?.let { runCatching { Json.decodeFromString<Set<String>>(it) }.getOrNull() }.orEmpty()

    private fun saveIncluded(id: String, sessions: Set<String>) = store.put(includedKey(id), Json.encodeToString(sessions))

    /** The run's signed [CreatedEvent] entry, if its signature holds. */
    private suspend fun genesis(id: String): Entry? = log(id)?.verify()?.entries
        ?.firstOrNull { it.entry.chain == id && it.entry.seq == 0L && it.entry.event is CreatedEvent }?.entry

    private fun index(): List<String> =
        store.get(KEY_RUNS)?.let { runCatching { Json.decodeFromString<List<String>>(it) }.getOrNull() }.orEmpty()

    private fun saveIndex(ids: List<String>) = store.put(KEY_RUNS, Json.encodeToString(ids.distinct()))

    private fun CreatedEvent.summary() = RunSummary(id, name, date, format, timestamp)

    private companion object {
        const val KEY_ORGANISER = "organiser.key"
        const val KEY_RUNS = "runs"
        fun runKey(id: String) = "run.$id"
        fun sourcesKey(id: String) = "run.$id.sources"
        fun includedKey(id: String) = "run.$id.included"
        fun timerKey(id: String) = "run.$id.timer"
    }
}
