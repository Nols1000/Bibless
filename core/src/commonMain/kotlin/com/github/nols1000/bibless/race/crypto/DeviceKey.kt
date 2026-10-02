package com.github.nols1000.bibless.race.crypto

/** Signs entries for one author. Hardware-backed keys can implement this later. */
interface Signer {
    /** The base64url public key (`04||X||Y`), which identifies the author. */
    val author: String

    /** Signs [data] with ECDSA P-256 / SHA-256 and returns the raw `r||s` signature. */
    suspend fun sign(data: ByteArray): ByteArray
}

/** A device's key pair held in memory, exportable so the app can persist it. */
class DeviceKey private constructor(private val key: KeyMaterial, override val author: String) : Signer {

    override suspend fun sign(data: ByteArray): ByteArray = PlatformCrypto.sign(key, data)

    /** The private key as base64url `04||X||Y||D`, for [import]. Keep it secret. */
    suspend fun export(): String = PlatformCrypto.exportKey(key).toBase64Url()

    companion object {
        suspend fun generate(): DeviceKey = of(PlatformCrypto.generateKey())

        /** Restores a key from [export]; throws [IllegalArgumentException] if it is malformed. */
        suspend fun import(exported: String): DeviceKey {
            val bytes = exported.fromBase64Url()
            require(bytes.size == 1 + 3 * P256_SIZE && bytes[0] == 0x04.toByte()) { "Not a P-256 private key" }
            return of(PlatformCrypto.importKey(bytes))
        }

        private suspend fun of(key: KeyMaterial) = DeviceKey(key, PlatformCrypto.publicKey(key).toBase64Url())
    }
}

/** Checks a raw `r||s` [signature] over [data] by [author]; false for malformed keys or signatures. */
suspend fun verifySignature(author: String, data: ByteArray, signature: ByteArray): Boolean {
    val publicKey = runCatching { author.fromBase64Url() }.getOrNull() ?: return false
    if (publicKey.size != 1 + 2 * P256_SIZE || publicKey[0] != 0x04.toByte()) return false
    if (signature.size != 2 * P256_SIZE) return false
    return runCatching { PlatformCrypto.verify(publicKey, data, signature) }.getOrDefault(false)
}

suspend fun sha256(data: ByteArray): ByteArray = PlatformCrypto.sha256(data)
