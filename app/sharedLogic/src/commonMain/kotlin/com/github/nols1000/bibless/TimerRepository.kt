package com.github.nols1000.bibless

import com.github.nols1000.bibless.race.ChainWriter
import com.github.nols1000.bibless.race.Event
import com.github.nols1000.bibless.race.RecordTimeEvent
import com.github.nols1000.bibless.race.RemoveTimeEvent
import com.github.nols1000.bibless.race.RunLog
import com.github.nols1000.bibless.race.SignedEvent
import com.github.nols1000.bibless.race.StartEvent
import com.github.nols1000.bibless.race.crypto.DeviceKey
import com.github.nols1000.bibless.race.verify
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

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

/** One session as stored: its events in order, signed or not yet. */
@Serializable
private data class SessionRecord(
    val id: String,
    val startedAt: Long,
    val anchor: ClockAnchor,
    val events: List<Event>,
)

/**
 * The phone stopwatch. Each tap is saved before the method returns, so it survives the app being
 * killed; signing it into the session's chain happens right after, on [scope], and catches up on
 * the next launch if the app died in between. Finish times are the session start plus elapsed
 * time from [clock]'s monotonic clock.
 */
class TimerRepository(
    private val store: KeyValueStore,
    private val clock: RaceClock,
    private val scope: CoroutineScope,
) {
    /** Signs on a background thread of its own. */
    constructor(store: KeyValueStore, clock: RaceClock) :
        this(store, clock, CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1)))

    private var record: SessionRecord? = store.get(KEY_CURRENT)?.takeIf { it.isNotEmpty() }?.let { id ->
        store.get(sessionKey(id))?.let { runCatching { json.decodeFromString<SessionRecord>(it) }.getOrNull() }
    }
    private val _state = MutableStateFlow(TimerState(record?.session()))
    val state: StateFlow<TimerState> = _state.asStateFlow()

    private val listeners = MutableStateFlow(emptyList<(TimerState) -> Unit>())
    private val signing = Mutex()
    private var writer: Pair<String, ChainWriter>? = null
    private var key: DeviceKey? = null

    init {
        record?.let { sign(it.id) }
    }

    /** Milliseconds since the clock started, or null when the timer is stopped. */
    fun elapsedMs(): Long? = record?.let { elapsed(it) }

    /** Starts the clock. Does nothing while a session runs. */
    @OptIn(ExperimentalUuidApi::class)
    fun start() {
        if (record != null) return
        val startedAt = clock.wallMillis()
        val start = StartEvent(Uuid.random().toString(), startedAt)
        save(SessionRecord(start.id, startedAt, ClockAnchor(clock.bootId(), clock.monotonicMillis(), 0), listOf(start)))
        store.put(KEY_SESSIONS, json.encodeToString(sessionIds() + start.id))
    }

    /** Records the next finisher at the current elapsed time; null when the timer is stopped. */
    @OptIn(ExperimentalUuidApi::class)
    fun tap(): FinishTime? {
        val current = record ?: return null
        val elapsed = elapsed(current)
        val event = RecordTimeEvent(Uuid.random().toString(), current.startedAt + elapsed)
        save(current.copy(events = current.events + event))
        return _state.value.session?.times?.firstOrNull { it.eventId == event.id }
    }

    /** Undoes the last finish still in effect. */
    @OptIn(ExperimentalUuidApi::class)
    fun undo() {
        val current = record ?: return
        val last = _state.value.session?.times?.lastOrNull() ?: return
        val event = RemoveTimeEvent(Uuid.random().toString(), current.startedAt + elapsed(current), last.eventId)
        save(current.copy(events = current.events + event))
    }

    /** Stops the timer. The session stays stored for export. */
    fun stop() {
        record ?: return
        record = null
        store.put(KEY_CURRENT, "")
        publish()
    }

    /** IDs of every session recorded on this device, oldest first. */
    fun sessionIds(): List<String> =
        store.get(KEY_SESSIONS)?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }.orEmpty()

    /** The signed events of session [id], for export. Events not signed yet are signed first. */
    suspend fun log(id: String): RunLog? {
        signing.withLock { signPending(id) }
        return store.get(logKey(id))?.let { runCatching { RunLog.fromJson(it) }.getOrNull() }
    }

    fun observe(listener: (TimerState) -> Unit): AutoCloseable {
        listeners.update { it + listener }
        listener(_state.value)
        return AutoCloseable { listeners.update { it - listener } }
    }

    private fun elapsed(current: SessionRecord): Long {
        val (elapsed, anchor) = clock.elapsedSince(current.anchor, current.startedAt)
        if (anchor != null) save(current.copy(anchor = anchor), publish = false)
        return elapsed
    }

    private fun save(updated: SessionRecord, publish: Boolean = true) {
        record = updated
        store.put(sessionKey(updated.id), json.encodeToString(updated))
        store.put(KEY_CURRENT, updated.id)
        if (publish) {
            publish()
            sign(updated.id)
        }
    }

    private fun publish() {
        val state = TimerState(record?.session())
        _state.value = state
        listeners.value.forEach { it(state) }
    }

    private fun sign(id: String) {
        scope.launch { signing.withLock { signPending(id) } }
    }

    /** Appends the stored events of session [id] that its signed log doesn't have yet. */
    private suspend fun signPending(id: String) {
        val stored = store.get(sessionKey(id))?.let { runCatching { json.decodeFromString<SessionRecord>(it) }.getOrNull() }
            ?: return
        var log = store.get(logKey(id))?.let { runCatching { RunLog.fromJson(it) }.getOrNull() } ?: RunLog()
        val chain = writer?.takeIf { it.first == id }?.second ?: run {
            val entries = log.verify().entries.filter { it.entry.chain == id }
            // Keep only the unbroken chain, so a damaged log is rewritten rather than forked
            log = RunLog(events = entries.map { it.signed })
            val last = entries.maxByOrNull { it.entry.seq }
            (if (last == null) ChainWriter.start(deviceKey(), stored.events.first()) else ChainWriter.resume(deviceKey(), last))
                .also { writer = id to it }
        }
        val pending = stored.events.drop(log.events.size)
        if (pending.isEmpty()) return
        val signed = mutableListOf<SignedEvent>()
        pending.forEach { signed += chain.append(it) }
        store.put(logKey(id), RunLog(events = log.events + signed).toJson())
    }

    private suspend fun deviceKey(): DeviceKey = key ?: (
        store.get(KEY_DEVICE)?.takeIf { it.isNotEmpty() }?.let { DeviceKey.import(it) }
            ?: DeviceKey.generate().also { store.put(KEY_DEVICE, it.export()) }
        ).also { key = it }

    private fun SessionRecord.session(): TimerSession {
        val removed = events.filterIsInstance<RemoveTimeEvent>().map { it.recordTimeEventId }.toSet()
        val times = events.filterIsInstance<RecordTimeEvent>()
            .filter { it.id !in removed }
            .sortedBy { it.timestamp }
            .mapIndexed { index, event -> FinishTime(event.id, index + 1, event.timestamp - startedAt) }
        return TimerSession(id, startedAt, times)
    }

    private companion object {
        const val KEY_DEVICE = "device.key"
        const val KEY_CURRENT = "timer.current"
        const val KEY_SESSIONS = "timer.sessions"
        fun sessionKey(id: String) = "timer.session.$id"
        fun logKey(id: String) = "timer.log.$id"

        val json = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }
    }
}
