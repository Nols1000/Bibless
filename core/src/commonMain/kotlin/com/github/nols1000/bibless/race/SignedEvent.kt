package com.github.nols1000.bibless.race

import com.github.nols1000.bibless.race.crypto.Signer
import com.github.nols1000.bibless.race.crypto.toBase64Url
import kotlinx.serialization.Serializable

/**
 * One event in its author's chain. A chain is one session of one author: the organiser's run
 * (starting with a [CreatedEvent]) or a timer or scanner session (starting with a [StartEvent]).
 * [chain] is the ID of that first event, [seq] counts from 0 and [prev] is the hash of the previous
 * entry, so a missing or changed entry shows.
 */
@Serializable
data class Entry(
    val author: String,
    val chain: String,
    val seq: Long,
    val prev: String?,
    val event: Event,
)

/**
 * An [Entry] as JSON with its author's signature over exactly those bytes. The JSON is kept as
 * signed and never re-encoded, so signatures verify on every platform.
 */
@Serializable
data class SignedEvent(val json: String, val sig: String)

/** A [SignedEvent] whose signature and place in its chain have been checked. */
data class VerifiedEntry(val hash: String, val entry: Entry, val signed: SignedEvent)

/** Appends signed events to one chain. Resume after a restart with [resume]. */
class ChainWriter(
    private val signer: Signer,
    val chain: String,
    private var nextSeq: Long = 0,
    private var head: String? = null,
) {
    /** Signs [event] as the next entry. The first event must be the chain's [CreatedEvent] or [StartEvent]. */
    suspend fun append(event: Event): SignedEvent {
        if (nextSeq == 0L) {
            require(event.id == chain && (event is CreatedEvent || event is StartEvent)) {
                "A chain starts with its own CreatedEvent or StartEvent"
            }
        }
        val json = raceJson.encodeToString(Entry.serializer(), Entry(signer.author, chain, nextSeq, head, event))
        val signed = SignedEvent(json, signer.sign(json.encodeToByteArray()).toBase64Url())
        head = hashOf(json)
        nextSeq++
        return signed
    }

    companion object {
        /** A writer for [genesis]'s chain, before anything was written. */
        fun start(signer: Signer, genesis: Event) = ChainWriter(signer, genesis.id)

        /** Continues the chain after [last], its most recent entry. */
        fun resume(signer: Signer, last: VerifiedEntry): ChainWriter {
            require(last.entry.author == signer.author) { "Can only continue your own chain" }
            return ChainWriter(signer, last.entry.chain, last.entry.seq + 1, last.hash)
        }
    }
}
