package com.github.nols1000.bibless.race

import com.github.nols1000.bibless.race.crypto.fromBase64Url
import com.github.nols1000.bibless.race.crypto.verifySignature
import kotlinx.serialization.Serializable

/**
 * Everything recorded about runs: devices' signed events, the organiser's certificates and the
 * published results. It is also the file volunteers export and organisers import; merging is a
 * union, so importing the same data twice changes nothing.
 */
@Serializable
data class RunLog(
    val version: Int = VERSION,
    val events: List<SignedEvent> = emptyList(),
    val certificates: List<SignedCertificate> = emptyList(),
    val results: List<SignedRunResult> = emptyList(),
    /**
     * The device a volunteer exported this from, so organisers can tell files apart. It isn't
     * signed and says nothing about who recorded what; the events' authors do.
     */
    val source: String? = null,
) {
    fun merge(other: RunLog) = RunLog(
        version = maxOf(version, other.version),
        events = (events + other.events).distinctBy { it.json }.sortedBy { it.json },
        certificates = (certificates + other.certificates).distinctBy { it.json }.sortedBy { it.json },
        results = (results + other.results).distinctBy { it.json }.sortedBy { it.json },
        source = source.takeIf { it == other.source },
    )

    fun toJson(): String = raceJson.encodeToString(serializer(), this)

    companion object {
        /** The newest log format this code understands. */
        const val VERSION = 1

        /** Reads a log; throws [IllegalArgumentException] if [json] isn't one. */
        fun fromJson(json: String): RunLog =
            runCatching { raceJson.decodeFromString(serializer(), json) }
                .getOrElse { throw IllegalArgumentException("Not a run log", it) }
    }
}

/** Why some of a log's events can't be used. */
sealed interface LogProblem {
    /** The log was written by a newer version; parts of it may be missing below. */
    data class UnsupportedVersion(val version: Int) : LogProblem

    /** An entry that can't be read, e.g. an event type from a newer version. */
    data class Unreadable(val json: String) : LogProblem

    data class BadSignature(val hash: String) : LogProblem

    /** The first entry of a chain isn't its own [CreatedEvent] or [StartEvent]. */
    data class BadGenesis(val hash: String) : LogProblem

    /** Entry [missingSeq] of a chain is missing; the entries after it are left out. */
    data class Gap(val author: String, val chain: String, val missingSeq: Long) : LogProblem

    /** An author signed two different entries at the same place in a chain; that chain stops before it. */
    data class Fork(val author: String, val chain: String, val seq: Long, val hashes: List<String>) : LogProblem

    /** An entry's [Entry.prev] doesn't match the entry before it; the chain stops before it. */
    data class BrokenLink(val hash: String) : LogProblem
}

/** The entries of a log that are correctly signed and chained, and what's wrong with the rest. */
data class Verification(val entries: List<VerifiedEntry>, val problems: List<LogProblem>)

/** Checks every event's signature and chain. Only an unbroken prefix of each chain is kept. */
suspend fun RunLog.verify(): Verification {
    val problems = mutableListOf<LogProblem>()
    if (version > RunLog.VERSION) problems += LogProblem.UnsupportedVersion(version)

    val signed = events.mapNotNull { event ->
        val entry = runCatching { raceJson.decodeFromString(Entry.serializer(), event.json) }.getOrNull()
        if (entry == null) {
            problems += LogProblem.Unreadable(event.json)
            return@mapNotNull null
        }
        val hash = hashOf(event.json)
        val signature = runCatching { event.sig.fromBase64Url() }.getOrNull()
        if (signature == null || !verifySignature(entry.author, event.json.encodeToByteArray(), signature)) {
            problems += LogProblem.BadSignature(hash)
            return@mapNotNull null
        }
        VerifiedEntry(hash, entry, event)
    }

    val entries = signed.groupBy { it.entry.author to it.entry.chain }.flatMap { (key, chain) ->
        val (author, chainId) = key
        val bySeq = chain.groupBy { it.entry.seq }
        val kept = mutableListOf<VerifiedEntry>()
        var seq = 0L
        while (true) {
            val candidates = bySeq[seq] ?: break
            if (candidates.size > 1) {
                problems += LogProblem.Fork(author, chainId, seq, candidates.map { it.hash }.sorted())
                break
            }
            val entry = candidates.single()
            if (seq == 0L && !entry.isGenesis()) {
                problems += LogProblem.BadGenesis(entry.hash)
                break
            }
            if (entry.entry.prev != kept.lastOrNull()?.hash) {
                problems += LogProblem.BrokenLink(entry.hash)
                break
            }
            kept += entry
            seq++
        }
        val highest = bySeq.keys.max()
        if (seq <= highest && bySeq[seq] == null) problems += LogProblem.Gap(author, chainId, seq)
        kept
    }
    return Verification(entries, problems)
}

private fun VerifiedEntry.isGenesis(): Boolean {
    val event = entry.event
    return entry.prev == null && event.id == entry.chain && (event is CreatedEvent || event is StartEvent)
}
