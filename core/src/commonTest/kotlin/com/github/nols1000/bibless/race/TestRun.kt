package com.github.nols1000.bibless.race

import com.github.nols1000.bibless.race.crypto.DeviceKey

internal const val START = 1_000_000L

/** Builds a run the way the apps would: an organiser's chain plus timer and scanner sessions. */
internal class TestRun private constructor(val organiser: DeviceKey, val run: String, private val writer: ChainWriter) {
    val events = mutableListOf<SignedEvent>()
    val certificates = mutableListOf<SignedCertificate>()
    private var clock = START

    /** Organiser corrections; times are absolute, so they line up with the timer's start. */
    suspend fun recordTime(elapsed: Long, start: Long = START) = organiserEvent(RecordTimeEvent(nextId(), start + elapsed))
    suspend fun recordAthlete(athleteId: String?, position: Int? = null) =
        organiserEvent(RecordAthleteEvent(nextId(), tick(), athleteId, position))
    suspend fun removeTime(eventId: String) = organiserEvent(RemoveTimeEvent(nextId(), tick(), eventId))
    suspend fun removeAthlete(eventId: String) = organiserEvent(RemoveAthleteEvent(nextId(), tick(), eventId))

    private suspend fun organiserEvent(event: Event): String {
        events += writer.append(event)
        return event.id
    }

    suspend fun session(key: DeviceKey? = null, start: Long = START): TestSession {
        val genesis = StartEvent(nextId(), start)
        val session = TestSession(this, key ?: DeviceKey.generate(), genesis)
        session.append(genesis)
        return session
    }

    /** Certifies [session] up to its current last entry. */
    suspend fun certify(session: TestSession, issuer: DeviceKey = organiser): SignedCertificate =
        certify(issuer, run, session.head(), tick()).also { certificates += it }

    fun log() = RunLog(events = events.toList(), certificates = certificates.toList())

    suspend fun state(certificates: Set<String>? = null): RunState = foldRun(log(), run, certificates)!!

    suspend fun results(timerChain: String? = null) = combineResults(state(), timerChain)

    fun nextId() = "e${ids++}"
    fun tick() = ++clock

    private var ids = 0

    companion object {
        suspend fun create(format: TimingMode = TimingMode.TOKENS, name: String = "Riverside 5k", date: String? = null): TestRun {
            val organiser = DeviceKey.generate()
            val created = CreatedEvent("run-1", START, name, format, date)
            val run = TestRun(organiser, created.id, ChainWriter.start(organiser, created))
            run.organiserEvent(created)
            return run
        }
    }
}

internal class TestSession(private val run: TestRun, val key: DeviceKey, genesis: StartEvent) {
    val chain = genesis.id
    val start = genesis.timestamp
    private val writer = ChainWriter.start(key, genesis)
    val entries = mutableListOf<SignedEvent>()

    suspend fun time(elapsed: Long) = add(RecordTimeEvent(run.nextId(), start + elapsed))
    suspend fun athlete(athleteId: String?, position: Int? = null) =
        add(RecordAthleteEvent(run.nextId(), run.tick(), athleteId, position))
    suspend fun removeTime(eventId: String) = add(RemoveTimeEvent(run.nextId(), run.tick(), eventId))
    suspend fun removeAthlete(eventId: String) = add(RemoveAthleteEvent(run.nextId(), run.tick(), eventId))

    private suspend fun add(event: Event): String {
        append(event)
        return event.id
    }

    suspend fun append(event: Event) {
        val signed = writer.append(event)
        entries += signed
        run.events += signed
    }

    suspend fun head(): VerifiedEntry {
        val last = entries.last()
        return VerifiedEntry(hashOf(last.json), raceJson.decodeFromString(Entry.serializer(), last.json), last)
    }
}
