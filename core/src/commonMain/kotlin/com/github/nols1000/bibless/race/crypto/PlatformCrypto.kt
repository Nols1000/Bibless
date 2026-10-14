package com.github.nols1000.bibless.race.crypto

/** A P-256 key pair held by the platform's own crypto API. */
internal expect class KeyMaterial

/**
 * ECDSA P-256 with SHA-256 through each platform's native API: java.security on the JVM and Android,
 * the Security framework on Apple, WebCrypto in the browser. Bytes cross this boundary in one format
 * on every platform: public keys as `04||X||Y`, private keys as `04||X||Y||D` and signatures as raw
 * `r||s`, so data signed on one platform verifies on all others.
 */
internal expect object PlatformCrypto {
    suspend fun sha256(data: ByteArray): ByteArray
    suspend fun generateKey(): KeyMaterial
    suspend fun publicKey(key: KeyMaterial): ByteArray
    suspend fun sign(key: KeyMaterial, data: ByteArray): ByteArray
    suspend fun verify(publicKey: ByteArray, data: ByteArray, signature: ByteArray): Boolean
    suspend fun exportKey(key: KeyMaterial): ByteArray
    suspend fun importKey(privateKey: ByteArray): KeyMaterial
}

/** Byte length of one P-256 coordinate or scalar. */
internal const val P256_SIZE = 32
