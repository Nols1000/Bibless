package com.github.nols1000.bibless.race

/** A finish time that is still in effect. [chain] is the session that recorded it. */
data class TimeRecord(val eventId: String, val author: String, val chain: String, val timestamp: Long)

/** A recorded finisher that is still in effect. */
data class AthleteRecord(
    val eventId: String,
    val author: String,
    val chain: String,
    val timestamp: Long,
    val athleteId: String?,
    val position: Int?,
)

/** Why some of the data for a run isn't used. */
sealed interface RunProblem {
    data class Log(val problem: LogProblem) : RunProblem

    /** A chain with data that no certificate covers yet; the organiser can certify it. */
    data class Uncertified(val author: String, val chain: String, val head: String) : RunProblem

    /** A certificate that isn't signed by the run's root key, or not for this run. */
    data class BadCertificate(val json: String) : RunProblem

    /** A certified entry isn't in the log (or follows a gap); that chain is left out. */
    data class MissingHead(val certificate: String) : RunProblem

    /** A certificate the caller asked for isn't in the log. */
    data class MissingCertificate(val certificate: String) : RunProblem

    /** A remove event by someone who may not remove that event, or for an event of the wrong kind. */
    data class IgnoredRemoval(val eventId: String) : RunProblem
}

/**
 * A run as of the events in effect: the organiser's chain plus every certified chain up to its
 * certified head, after removals.
 */
data class RunState(
    val run: String,
    /** The organiser's key, the author of the run's [CreatedEvent]. */
    val root: String,
    val name: String,
    val format: TimingMode,
    /** Session start per chain that has a [StartEvent]. */
    val starts: Map<String, Long>,
    val times: List<TimeRecord>,
    val athletes: List<AthleteRecord>,
    /** Hashes of the certificates used, sorted. */
    val certificates: List<String>,
    val problems: List<RunProblem>,
)

/**
 * Builds [run]'s state from [log], or null if the log doesn't contain the run's [CreatedEvent].
 * With [certificates] (hashes) only those are used, which is how a published result is checked
 * against the data it was computed from; otherwise every valid certificate is used, and for each
 * chain the one reaching furthest wins.
 */
suspend fun foldRun(log: RunLog, run: String, certificates: Set<String>? = null): RunState? {
    val verification = log.verify()
    val problems: MutableList<RunProblem> = verification.problems.mapTo(mutableListOf()) { RunProblem.Log(it) }
    val entries = verification.entries
    val genesis = entries.firstOrNull { it.entry.seq == 0L && it.entry.chain == run && it.entry.event is CreatedEvent }
        ?: return null
    val root = genesis.entry.author
    val created = genesis.entry.event as CreatedEvent

    val chains = entries.groupBy { it.entry.author to it.entry.chain }
    val byHash = entries.associateBy { it.hash }

    val valid = log.certificates.mapNotNull { signed ->
        val certificate = signed.verified()
        when {
            certificate == null || certificate.certificate.issuer != root || certificate.certificate.run != run -> {
                if (certificates == null || certificate?.hash in certificates) problems += RunProblem.BadCertificate(signed.json)
                null
            }
            certificates != null && certificate.hash !in certificates -> null
            else -> certificate
        }
    }
    certificates?.minus(valid.map { it.hash }.toSet())?.sorted()?.forEach { problems += RunProblem.MissingCertificate(it) }

    // For each certified chain, the certificate whose head reaches furthest
    val heads = mutableMapOf<Pair<String, String>, Pair<VerifiedCertificate, VerifiedEntry>>()
    for (certificate in valid) {
        val c = certificate.certificate
        val head = byHash[c.head]?.takeIf { it.entry.author == c.author && it.entry.chain == c.chain }
        if (head == null) {
            problems += RunProblem.MissingHead(certificate.hash)
            continue
        }
        val key = c.author to c.chain
        val current = heads[key]
        if (current == null || head.entry.seq > current.second.entry.seq) heads[key] = certificate to head
    }

    val included = chains.flatMap { (key, chain) ->
        when {
            key == root to run -> chain
            key in heads -> chain.filter { it.entry.seq <= heads.getValue(key).second.entry.seq }
            else -> emptyList()
        }
    }
    chains.forEach { (key, chain) ->
        val last = chain.maxBy { it.entry.seq }
        val certified = heads[key]?.second?.entry?.seq ?: -1
        if (key != root to run && last.entry.seq > certified) problems += RunProblem.Uncertified(key.first, key.second, last.hash)
    }

    val events = included.sortedWith(compareBy({ it.entry.author }, { it.entry.chain }, { it.entry.seq }))
    val recordedTimes = events.filter { it.entry.event is RecordTimeEvent }.associateBy { it.entry.event.id }
    val recordedAthletes = events.filter { it.entry.event is RecordAthleteEvent }.associateBy { it.entry.event.id }
    val removed = mutableSetOf<String>()
    for (entry in events) {
        val (targetId, targets) = when (val event = entry.entry.event) {
            is RemoveTimeEvent -> event.recordTimeEventId to recordedTimes
            is RemoveAthleteEvent -> event.recordAthleteEventId to recordedAthletes
            else -> continue
        }
        val target = targets[targetId]
        // Authors may remove their own records; the organiser may remove any
        if (target != null && (entry.entry.author == root || entry.entry.author == target.entry.author)) {
            removed += targetId
        } else {
            problems += RunProblem.IgnoredRemoval(entry.entry.event.id)
        }
    }

    val starts = events.mapNotNull { e -> (e.entry.event as? StartEvent)?.let { e.entry.chain to it.timestamp } }.toMap()
    val times = recordedTimes.values.filter { it.entry.event.id !in removed }.map {
        TimeRecord(it.entry.event.id, it.entry.author, it.entry.chain, it.entry.event.timestamp)
    }
    val athletes = recordedAthletes.values.filter { it.entry.event.id !in removed }.map {
        val event = it.entry.event as RecordAthleteEvent
        AthleteRecord(event.id, it.entry.author, it.entry.chain, event.timestamp, event.athleteId, event.position)
    }
    return RunState(
        run = run,
        root = root,
        name = created.name,
        format = created.format,
        starts = starts,
        times = times,
        athletes = athletes,
        certificates = heads.values.map { it.first.hash }.sorted(),
        problems = problems,
    )
}
