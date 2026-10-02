package com.github.nols1000.bibless.race.crypto

import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPrivateKey
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPrivateKeySpec
import java.security.spec.ECPublicKeySpec

internal actual class KeyMaterial(val private: ECPrivateKey, val public: ECPublicKey)

internal actual object PlatformCrypto {
    private val p256: ECParameterSpec by lazy {
        AlgorithmParameters.getInstance("EC")
            .apply { init(ECGenParameterSpec("secp256r1")) }
            .getParameterSpec(ECParameterSpec::class.java)
    }

    actual suspend fun sha256(data: ByteArray): ByteArray = MessageDigest.getInstance("SHA-256").digest(data)

    actual suspend fun generateKey(): KeyMaterial {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        return KeyMaterial(pair.private as ECPrivateKey, pair.public as ECPublicKey)
    }

    actual suspend fun publicKey(key: KeyMaterial): ByteArray = encodePoint(key.public.w)

    actual suspend fun sign(key: KeyMaterial, data: ByteArray): ByteArray {
        val der = Signature.getInstance("SHA256withECDSA").run {
            initSign(key.private)
            update(data)
            sign()
        }
        return derToRaw(der)
    }

    actual suspend fun verify(publicKey: ByteArray, data: ByteArray, signature: ByteArray): Boolean =
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(decodePublic(publicKey))
            update(data)
            verify(rawToDer(signature))
        }

    actual suspend fun exportKey(key: KeyMaterial): ByteArray =
        encodePoint(key.public.w) + key.private.s.toByteArray().leftPad()

    actual suspend fun importKey(privateKey: ByteArray): KeyMaterial {
        val public = decodePublic(privateKey.copyOfRange(0, 1 + 2 * P256_SIZE))
        val d = BigInteger(1, privateKey.copyOfRange(1 + 2 * P256_SIZE, privateKey.size))
        val private = KeyFactory.getInstance("EC").generatePrivate(ECPrivateKeySpec(d, p256)) as ECPrivateKey
        return KeyMaterial(private, public)
    }

    private fun encodePoint(point: ECPoint): ByteArray =
        byteArrayOf(0x04) + point.affineX.toByteArray().leftPad() + point.affineY.toByteArray().leftPad()

    private fun decodePublic(bytes: ByteArray): ECPublicKey {
        val x = BigInteger(1, bytes.copyOfRange(1, 1 + P256_SIZE))
        val y = BigInteger(1, bytes.copyOfRange(1 + P256_SIZE, 1 + 2 * P256_SIZE))
        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(ECPoint(x, y), p256)) as ECPublicKey
    }
}
