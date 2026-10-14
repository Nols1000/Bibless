package com.github.nols1000.bibless

import com.github.nols1000.bibless.race.RecordTimeEvent
import com.github.nols1000.bibless.race.RemoveTimeEvent
import com.github.nols1000.bibless.race.RunLog
import com.github.nols1000.bibless.race.StartEvent
import com.github.nols1000.bibless.race.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

/**
 * Hands the watch's stopped timer sessions to the phone, each once. [send] gives the session's
 * signed events to the platform's transfer, which delivers them whenever the phone is in reach,
 * and returns false if it can't take them; the session is then offered again next time.
 */
class WatchSessionSender(
    private val timer: TimerRepository,
    private val store: KeyValueStore,
    /** The watch's name, so the organiser can tell its files apart. */
    private val source: String,
    private val send: (id: String, json: String) -> Boolean,
) {
    /** Offers every stopped session the phone hasn't been sent yet. */
    @Throws(CancellationException::class)
    suspend fun sendStopped() {
        val running = timer.state.value.session?.id
        var sent = sent()
        for (id in timer.sessionIds()) {
            if (id == running || id in sent) continue
            val log = timer.log(id) ?: continue
            if (runCatching { send(id, log.copy(source = source).toJson()) }.getOrDefault(false)) {
                sent = sent + id
                store.put(KEY_SENT, Json.encodeToString(sent))
            }
        }
    }

    private fun sent(): Set<String> =
        store.get(KEY_SENT)?.let { runCatching { Json.decodeFromString<Set<String>>(it) }.getOrNull() }.orEmpty()

    private companion object {
        const val KEY_SENT = "watch.sent"
    }
}

/** A timer session recorded on a paired watch, as the phone keeps it. */
@Serializable
internal data class ReceivedSession(
    val id: String,
    val source: String,
    val startedAt: Long,
    val run: String?,
    val times: List<FinishTime>,
    /** The session's signed events as received, for export. */
    val log: String,
)

/** Timer sessions the paired watch sent to this phone, kept so they can be shared like the phone's own. */
class ReceivedSessions(private val store: KeyValueStore) {
    private val _received = MutableStateFlow(0)

    /** Counts the sessions kept since launch, so lists know to refresh. */
    val received: StateFlow<Int> = _received.asStateFlow()

    private val listeners = MutableStateFlow(emptyList<() -> Unit>())

    /** Calls [listener] whenever a session is kept. */
    fun observe(listener: () -> Unit): AutoCloseable {
        listeners.update { it + listener }
        return AutoCloseable { listeners.update { it - listener } }
    }

    /**
     * Keeps the timer session in [payload], a [RunLog] the watch sent. Only the part of it whose
     * signatures and chain check out is kept, and a copy with fewer entries never replaces a
     * fuller one. Returns false if [payload] holds no timer session.
     */
    @Throws(CancellationException::class)
    suspend fun receive(payload: String): Boolean {
        val log = runCatching { RunLog.fromJson(payload) }.getOrNull() ?: return false
        val entries = log.verify().entries
        val start = entries.firstOrNull { it.entry.seq == 0L && it.entry.event is StartEvent } ?: return false
        val chain = entries.filter { it.entry.chain == start.entry.chain && it.entry.author == start.entry.author }
            .sortedBy { it.entry.seq }
        val events = chain.map { it.entry.event }
        if (events.none { it is RecordTimeEvent }) return false

        val id = start.entry.chain
        if ((session(id)?.let { RunLog.fromJson(it.log).events.size } ?: -1) > chain.size) return true
        val startedAt = start.entry.event.timestamp
        val removed = events.filterIsInstance<RemoveTimeEvent>().map { it.recordTimeEventId }.toSet()
        val times = events.filterIsInstance<RecordTimeEvent>()
            .filter { it.id !in removed }
            .sortedBy { it.timestamp }
            .mapIndexed { index, event -> FinishTime(event.id, index + 1, event.timestamp - startedAt) }
        val source = log.source?.takeIf { it.isNotBlank() } ?: "Watch"
        val kept = RunLog(events = chain.map { it.signed }, source = source)
        val session = ReceivedSession(id, source, startedAt, (start.entry.event as StartEvent).run, times, kept.toJson())
        store.put(sessionKey(id), json.encodeToString(session))
        store.put(KEY_IDS, Json.encodeToString((ids() + id).distinct()))
        _received.update { it + 1 }
        listeners.value.forEach { it() }
        return true
    }

    /** Every session received, for the list of recorded sessions. */
    fun sessions(): List<RecordedSession> = ids().mapNotNull { session(it) }.map {
        RecordedSession(SessionKind.TIMER, it.id, it.startedAt, it.run, it.times.size, running = false, source = it.source)
    }

    /** The signed events of session [id] as the watch sent them, naming the watch as their source. */
    fun log(id: String): RunLog? = session(id)?.let { RunLog.fromJson(it.log) }

    fun times(id: String): List<FinishTime>? = session(id)?.times

    private fun session(id: String): ReceivedSession? =
        store.get(sessionKey(id))?.takeIf { it.isNotEmpty() }?.let { runCatching { json.decodeFromString<ReceivedSession>(it) }.getOrNull() }

    private fun ids(): List<String> =
        store.get(KEY_IDS)?.let { runCatching { Json.decodeFromString<List<String>>(it) }.getOrNull() }.orEmpty()

    private companion object {
        const val KEY_IDS = "received.sessions"
        fun sessionKey(id: String) = "received.session.$id"
        val json = Json { ignoreUnknownKeys = true }
    }
}
