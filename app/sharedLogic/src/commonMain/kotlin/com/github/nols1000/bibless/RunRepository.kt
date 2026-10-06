package com.github.nols1000.bibless

import com.github.nols1000.bibless.race.ChainWriter
import com.github.nols1000.bibless.race.CreatedEvent
import com.github.nols1000.bibless.race.Entry
import com.github.nols1000.bibless.race.RunHint
import com.github.nols1000.bibless.race.RunLog
import com.github.nols1000.bibless.race.TimingMode
import com.github.nols1000.bibless.race.crypto.DeviceKey
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
        store.put(runKey(id), "")
    }

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
    }
}
