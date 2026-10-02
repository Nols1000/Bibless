package com.github.nols1000.bibless.race

import com.github.nols1000.bibless.race.crypto.Signer
import com.github.nols1000.bibless.race.crypto.fromBase64Url
import com.github.nols1000.bibless.race.crypto.toBase64Url
import com.github.nols1000.bibless.race.crypto.verifySignature
import kotlinx.serialization.Serializable

/**
 * The organiser ([issuer]) vouches that [author]'s [chain], up to and including the entry [head],
 * belongs to [run]. The chain's events stay signed only by their author; entries after [head]
 * need a newer certificate.
 */
@Serializable
data class Certificate(
    val run: String,
    val issuer: String,
    val author: String,
    val chain: String,
    val head: String,
    val issuedAt: Long,
)

/** A [Certificate] as JSON with the issuer's signature over exactly those bytes. */
@Serializable
data class SignedCertificate(val json: String, val sig: String)

/** Certifies the chain that ends in [head] for [run]. */
suspend fun certify(signer: Signer, run: String, head: VerifiedEntry, issuedAt: Long): SignedCertificate {
    val certificate = Certificate(run, signer.author, head.entry.author, head.entry.chain, head.hash, issuedAt)
    val json = raceJson.encodeToString(Certificate.serializer(), certificate)
    return SignedCertificate(json, signer.sign(json.encodeToByteArray()).toBase64Url())
}

internal data class VerifiedCertificate(val hash: String, val certificate: Certificate)

/** The certificate if it is readable and signed by its issuer, else null. */
internal suspend fun SignedCertificate.verified(): VerifiedCertificate? {
    val certificate = runCatching { raceJson.decodeFromString(Certificate.serializer(), json) }.getOrNull() ?: return null
    val signature = runCatching { sig.fromBase64Url() }.getOrNull() ?: return null
    if (!verifySignature(certificate.issuer, json.encodeToByteArray(), signature)) return null
    return VerifiedCertificate(hashOf(json), certificate)
}
