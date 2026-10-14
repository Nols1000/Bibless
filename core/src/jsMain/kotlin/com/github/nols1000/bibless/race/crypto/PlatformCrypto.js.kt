package com.github.nols1000.bibless.race.crypto

import kotlinx.coroutines.await
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import kotlin.js.Promise

internal actual class KeyMaterial(val private: dynamic, val public: dynamic)

/** WebCrypto, which needs a secure context (HTTPS or localhost). Its signatures are already raw `r||s`. */
internal actual object PlatformCrypto {
    private val subtle: dynamic get() = js("globalThis.crypto.subtle")
    private val algorithm: dynamic get() = js("({ name: 'ECDSA', namedCurve: 'P-256' })")
    private val signing: dynamic get() = js("({ name: 'ECDSA', hash: 'SHA-256' })")

    actual suspend fun sha256(data: ByteArray): ByteArray =
        (subtle.digest("SHA-256", data) as Promise<ArrayBuffer>).await().toByteArray()

    actual suspend fun generateKey(): KeyMaterial {
        val pair = (subtle.generateKey(algorithm, true, arrayOf("sign", "verify")) as Promise<dynamic>).await()
        return KeyMaterial(pair.privateKey, pair.publicKey)
    }

    actual suspend fun publicKey(key: KeyMaterial): ByteArray =
        (subtle.exportKey("raw", key.public) as Promise<ArrayBuffer>).await().toByteArray()

    actual suspend fun sign(key: KeyMaterial, data: ByteArray): ByteArray =
        (subtle.sign(signing, key.private, data) as Promise<ArrayBuffer>).await().toByteArray()

    actual suspend fun verify(publicKey: ByteArray, data: ByteArray, signature: ByteArray): Boolean {
        val key = (subtle.importKey("raw", publicKey, algorithm, false, arrayOf("verify")) as Promise<dynamic>).await()
        return (subtle.verify(signing, key, signature, data) as Promise<Boolean>).await()
    }

    actual suspend fun exportKey(key: KeyMaterial): ByteArray {
        val jwk = (subtle.exportKey("jwk", key.private) as Promise<dynamic>).await()
        return byteArrayOf(0x04) + (jwk.x as String).fromBase64Url() + (jwk.y as String).fromBase64Url() +
            (jwk.d as String).fromBase64Url()
    }

    actual suspend fun importKey(privateKey: ByteArray): KeyMaterial {
        val x = privateKey.copyOfRange(1, 1 + P256_SIZE).toBase64Url()
        val y = privateKey.copyOfRange(1 + P256_SIZE, 1 + 2 * P256_SIZE).toBase64Url()
        val d = privateKey.copyOfRange(1 + 2 * P256_SIZE, privateKey.size).toBase64Url()
        val jwk: dynamic = js("({ kty: 'EC', crv: 'P-256' })")
        jwk.x = x
        jwk.y = y
        jwk.d = d
        val private = (subtle.importKey("jwk", jwk, algorithm, true, arrayOf("sign")) as Promise<dynamic>).await()
        val publicJwk: dynamic = js("({ kty: 'EC', crv: 'P-256' })")
        publicJwk.x = x
        publicJwk.y = y
        val public = (subtle.importKey("jwk", publicJwk, algorithm, true, arrayOf("verify")) as Promise<dynamic>).await()
        return KeyMaterial(private, public)
    }

    // A Kotlin ByteArray is an Int8Array at runtime, so it can be passed to WebCrypto as is
    private fun ArrayBuffer.toByteArray(): ByteArray = Int8Array(this).unsafeCast<ByteArray>()
}
