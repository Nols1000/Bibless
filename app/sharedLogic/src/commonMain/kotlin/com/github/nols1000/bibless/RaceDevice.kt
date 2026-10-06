package com.github.nols1000.bibless

import com.github.nols1000.bibless.race.ChainWriter
import com.github.nols1000.bibless.race.Event
import com.github.nols1000.bibless.race.RunHint
import com.github.nols1000.bibless.race.RunLog
import com.github.nols1000.bibless.race.SignedEvent
import com.github.nols1000.bibless.race.StartEvent
import com.github.nols1000.bibless.race.TimingMode
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

/**
 * What this device records race-day data with: where it is stored, the clocks, and the device
 * key that signs it. The timer and the scanner share one, so they sign with the same key.
 * [store] should write before `put` returns, so a tap survives the app being killed right after.
 */
class RaceDevice(
    internal val store: KeyValueStore,
    internal val clock: RaceClock,
    internal val scope: CoroutineScope,
) {
    /** Signs on a background thread of its own. */
    constructor(store: KeyValueStore, clock: RaceClock) :
        this(store, clock, CoroutineScope(SupervisorJob() + Dispatchers.Default.limitedParallelism(1)))

    private val keyLock = Mutex()
    private var key: DeviceKey? = null

    private val _joinedRun = MutableStateFlow(store.get(KEY_JOINED_RUN)?.takeIf { it.isNotEmpty() }?.let { RunHint.fromJson(it) })

    /** The run this device records for, from the run's QR code; null records without a run. */
    val joinedRun: StateFlow<RunHint?> = _joinedRun.asStateFlow()

    private val runListeners = MutableStateFlow(emptyList<(RunHint?) -> Unit>())

    /**
     * Joins the run whose QR code reads [text]. Sessions started from now on record for it;
     * running ones keep their run. Returns null, and changes nothing, if [text] isn't a run code.
     */
    fun join(text: String): RunHint? {
        val hint = RunHint.fromJson(text.trim())?.takeIf { it.run.isNotBlank() && it.root.length == ROOT_LENGTH } ?: return null
        store.put(KEY_JOINED_RUN, hint.toJson())
        setJoinedRun(hint)
        return hint
    }

    /** Records without a run from now on. */
    fun leave() {
        store.put(KEY_JOINED_RUN, "")
        setJoinedRun(null)
    }

    fun observeJoinedRun(listener: (RunHint?) -> Unit): AutoCloseable {
        runListeners.update { it + listener }
        listener(_joinedRun.value)
        return AutoCloseable { runListeners.update { it - listener } }
    }

    private fun setJoinedRun(hint: RunHint?) {
        _joinedRun.value = hint
        runListeners.value.forEach { it(hint) }
    }

    internal suspend fun key(): DeviceKey = keyLock.withLock {
        key ?: (
            store.get(KEY_DEVICE)?.takeIf { it.isNotEmpty() }?.let { DeviceKey.import(it) }
                ?: DeviceKey.generate().also { store.put(KEY_DEVICE, it.export()) }
            ).also { key = it }
    }

    private companion object {
        const val KEY_DEVICE = "device.key"
        const val KEY_JOINED_RUN = "run.joined"

        /** An organiser key: base64url of an uncompressed P-256 point. */
        const val ROOT_LENGTH = 87
    }
}

/** One session as stored: its events in order, signed or not yet. */
@Serializable
internal data class SessionRecord(
    val id: String,
    val startedAt: Long,
    val anchor: ClockAnchor,
    val events: List<Event>,
    /** How a scanner session gives positions; null for the timer. */
    val mode: TimingMode? = null,
) {
    /** The run the session records for, from its [StartEvent]. */
    val run: String? get() = (events.firstOrNull() as? StartEvent)?.run
}

/**
 * The sessions of one kind (timer or scanner) on a [device]. Every event is saved before
 * [append] returns, then signed into the session's chain on the device's scope; events the app
 * didn't get to sign are signed on the next launch. Event times are the session start plus
 * elapsed time from the monotonic clock, so wall-clock changes don't reorder them.
 */
internal class SessionLog(private val device: RaceDevice, private val kind: String, private val onChange: () -> Unit) {
    private val store = device.store
    private val clock = device.clock

    var current: SessionRecord? = store.get("$kind.current")?.takeIf { it.isNotEmpty() }?.let { id ->
        store.get(sessionKey(id))?.let { runCatching { json.decodeFromString<SessionRecord>(it) }.getOrNull() }
    }
        private set

    private val signing = Mutex()
    private var writer: Pair<String, ChainWriter>? = null

    init {
        current?.let { sign(it.id) }
    }

    /** Milliseconds since the session started, or null without one. */
    fun elapsed(): Long? = current?.let { elapsed(it) }

    /** Starts a session. Does nothing while one runs. */
    @OptIn(ExperimentalUuidApi::class)
    fun start(mode: TimingMode? = null) {
        if (current != null) return
        val startedAt = clock.wallMillis()
        val start = StartEvent(Uuid.random().toString(), startedAt, device.joinedRun.value?.run)
        save(SessionRecord(start.id, startedAt, ClockAnchor(clock.bootId(), clock.monotonicMillis(), 0), listOf(start), mode))
        store.put("$kind.sessions", json.encodeToString(sessionIds() + start.id))
    }

    /** Adds the event [build] makes from a new event ID and the current session time. */
    @OptIn(ExperimentalUuidApi::class)
    fun <E : Event> append(build: (id: String, timestamp: Long) -> E): E? {
        val session = current ?: return null
        val event = build(Uuid.random().toString(), session.startedAt + elapsed(session))
        save(session.copy(events = session.events + event))
        return event
    }

    /** Ends the session. It stays stored for export. */
    fun stop() {
        current ?: return
        current = null
        store.put("$kind.current", "")
        onChange()
    }

    fun sessionIds(): List<String> =
        store.get("$kind.sessions")?.let { runCatching { json.decodeFromString<List<String>>(it) }.getOrNull() }.orEmpty()

    /** The signed events of session [id]. Events not signed yet are signed first. */
    suspend fun log(id: String): RunLog? {
        signing.withLock { signPending(id) }
        return store.get(logKey(id))?.let { runCatching { RunLog.fromJson(it) }.getOrNull() }
    }

    private fun elapsed(session: SessionRecord): Long {
        val (elapsed, anchor) = clock.elapsedSince(session.anchor, session.startedAt)
        if (anchor != null) save(session.copy(anchor = anchor), changed = false)
        return elapsed
    }

    private fun save(updated: SessionRecord, changed: Boolean = true) {
        current = updated
        store.put(sessionKey(updated.id), json.encodeToString(updated))
        store.put("$kind.current", updated.id)
        if (changed) {
            onChange()
            sign(updated.id)
        }
    }

    private fun sign(id: String) {
        device.scope.launch { signing.withLock { signPending(id) } }
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
            val key = device.key()
            (if (last == null) ChainWriter.start(key, stored.events.first()) else ChainWriter.resume(key, last))
                .also { writer = id to it }
        }
        val pending = stored.events.drop(log.events.size)
        if (pending.isEmpty()) return
        val signed = mutableListOf<SignedEvent>()
        pending.forEach { signed += chain.append(it) }
        store.put(logKey(id), RunLog(events = log.events + signed).toJson())
    }

    private fun sessionKey(id: String) = "$kind.session.$id"
    private fun logKey(id: String) = "$kind.log.$id"

    private companion object {
        val json = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }
    }
}
