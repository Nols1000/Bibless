package com.github.nols1000.bibless.race

import com.github.nols1000.bibless.race.crypto.Signer
import com.github.nols1000.bibless.race.crypto.fromBase64Url
import com.github.nols1000.bibless.race.crypto.toBase64Url
import com.github.nols1000.bibless.race.crypto.verifySignature
import kotlinx.serialization.Serializable

/**
 * A run's published results, signed by its organiser ([publisher]). It names the timer session and
 * the certificates it was computed from, so anyone holding the log can recompute and check it.
 */
@Serializable
data class RunResult(
    val run: String,
    val publisher: String,
    val timerChain: String?,
    val rows: List<ResultRow>,
    val certificates: List<String>,
    val publishedAt: Long,
)

/** A [RunResult] as JSON with the publisher's signature over exactly those bytes. */
@Serializable
data class SignedRunResult(val json: String, val sig: String)

/** Signs [results], computed from [state], as the published result of the run. */
suspend fun publishResult(signer: Signer, state: RunState, results: Results, publishedAt: Long): SignedRunResult {
    require(signer.author == state.root) { "Only the run's organiser can publish its results" }
    val result = RunResult(state.run, signer.author, results.timerChain, results.rows, state.certificates, publishedAt)
    val json = raceJson.encodeToString(RunResult.serializer(), result)
    return SignedRunResult(json, signer.sign(json.encodeToByteArray()).toBase64Url())
}

/** The outcome of [verifyRunResult]. */
sealed interface ResultCheck {
    data class Valid(val result: RunResult) : ResultCheck

    sealed interface Invalid : ResultCheck

    data object Unreadable : Invalid
    data object BadSignature : Invalid

    /** The log doesn't contain the run's [CreatedEvent]. */
    data object UnknownRun : Invalid

    /** Signed by someone other than the run's organiser. */
    data object NotOrganiser : Invalid

    /** Certificates the result relies on are missing from the log or invalid. */
    data class BadCertificates(val problems: List<RunProblem>) : Invalid

    /** Events the result relies on are missing from the log or don't verify. */
    data class MissingEvents(val problems: List<RunProblem>) : Invalid

    /** Recomputing from the log gives different rows. */
    data class RowsDiffer(val expected: List<ResultRow>) : Invalid
}

/** Checks [result] against [log]: signature, organiser, certificates, events and every row. */
suspend fun verifyRunResult(log: RunLog, result: SignedRunResult): ResultCheck {
    val run = runCatching { raceJson.decodeFromString(RunResult.serializer(), result.json) }.getOrNull()
        ?: return ResultCheck.Unreadable
    val signature = runCatching { result.sig.fromBase64Url() }.getOrNull()
    if (signature == null || !verifySignature(run.publisher, result.json.encodeToByteArray(), signature)) {
        return ResultCheck.BadSignature
    }
    val state = foldRun(log, run.run, run.certificates.toSet()) ?: return ResultCheck.UnknownRun
    if (run.publisher != state.root) return ResultCheck.NotOrganiser

    val certificateProblems = state.problems.filter { it is RunProblem.BadCertificate || it is RunProblem.MissingCertificate }
    if (certificateProblems.isNotEmpty()) return ResultCheck.BadCertificates(certificateProblems)
    val missing = state.problems.filterIsInstance<RunProblem.MissingHead>()
    if (missing.isNotEmpty() || state.certificates != run.certificates.sorted()) {
        return ResultCheck.MissingEvents(missing + state.problems.filterIsInstance<RunProblem.Log>())
    }

    val expected = combineResults(state, run.timerChain).rows
    return if (expected == run.rows) ResultCheck.Valid(run) else ResultCheck.RowsDiffer(expected)
}
