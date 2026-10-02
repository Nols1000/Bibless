@file:OptIn(ExperimentalForeignApi::class, ExperimentalNativeApi::class)

package com.github.nols1000.bibless.race.crypto

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFDictionaryRef
import platform.CoreFoundation.CFErrorRefVar
import platform.CoreFoundation.CFNumberCreate
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringRef
import platform.CoreFoundation.CFTypeRef
import platform.CoreFoundation.kCFNumberIntType
import platform.Security.SecKeyCopyExternalRepresentation
import platform.Security.SecKeyCopyPublicKey
import platform.Security.SecKeyCreateRandomKey
import platform.Security.SecKeyCreateSignature
import platform.Security.SecKeyCreateWithData
import platform.Security.SecKeyRef
import platform.Security.SecKeyVerifySignature
import platform.Security.kSecAttrKeyClass
import platform.Security.kSecAttrKeyClassPrivate
import platform.Security.kSecAttrKeyClassPublic
import platform.Security.kSecAttrKeySizeInBits
import platform.Security.kSecAttrKeyType
import platform.Security.kSecAttrKeyTypeECSECPrimeRandom
import platform.Security.kSecKeyAlgorithmECDSASignatureMessageX962SHA256
import kotlin.experimental.ExperimentalNativeApi
import kotlin.native.ref.Cleaner
import kotlin.native.ref.createCleaner

internal actual class KeyMaterial(val private: SecKeyRef) {
    @Suppress("unused") // Releases the key once this object is collected
    private val cleaner: Cleaner = createCleaner(private) { CFRelease(it) }
}

/**
 * Keys are created in memory only (never added to the Keychain), so this needs no entitlements.
 * The Security framework speaks the same `04||X||Y(||D)` key formats Bibless stores.
 */
internal actual object PlatformCrypto {
    actual suspend fun sha256(data: ByteArray): ByteArray {
        val digest = ByteArray(CC_SHA256_DIGEST_LENGTH)
        digest.usePinned { out ->
            if (data.isEmpty()) {
                CC_SHA256(null, 0u, out.addressOf(0).reinterpret())
            } else {
                data.usePinned { CC_SHA256(it.addressOf(0), data.size.convert(), out.addressOf(0).reinterpret()) }
            }
        }
        return digest
    }

    actual suspend fun generateKey(): KeyMaterial = memScoped {
        val attributes = attributes(kSecAttrKeyClassPrivate)
        val error = alloc<CFErrorRefVar>()
        val key = SecKeyCreateRandomKey(attributes, error.ptr)
        CFRelease(attributes)
        KeyMaterial(checkNotNull(key) { "Could not create a key" })
    }

    actual suspend fun publicKey(key: KeyMaterial): ByteArray {
        val public = checkNotNull(SecKeyCopyPublicKey(key.private))
        return try {
            export(public)
        } finally {
            CFRelease(public)
        }
    }

    actual suspend fun sign(key: KeyMaterial, data: ByteArray): ByteArray = memScoped {
        val input = data.toCFData()
        val error = alloc<CFErrorRefVar>()
        val der = SecKeyCreateSignature(key.private, kSecKeyAlgorithmECDSASignatureMessageX962SHA256, input, error.ptr)
        CFRelease(input)
        derToRaw(checkNotNull(der) { "Could not sign" }.consume())
    }

    actual suspend fun verify(publicKey: ByteArray, data: ByteArray, signature: ByteArray): Boolean = memScoped {
        val key = createKey(publicKey, kSecAttrKeyClassPublic) ?: return false
        val input = data.toCFData()
        val sig = rawToDer(signature).toCFData()
        val valid = SecKeyVerifySignature(key, kSecKeyAlgorithmECDSASignatureMessageX962SHA256, input, sig, null)
        CFRelease(sig)
        CFRelease(input)
        CFRelease(key)
        valid
    }

    actual suspend fun exportKey(key: KeyMaterial): ByteArray = export(key.private)

    actual suspend fun importKey(privateKey: ByteArray): KeyMaterial =
        KeyMaterial(requireNotNull(memScoped { createKey(privateKey, kSecAttrKeyClassPrivate) }) { "Not a P-256 private key" })

    private fun export(key: SecKeyRef): ByteArray = memScoped {
        val error = alloc<CFErrorRefVar>()
        checkNotNull(SecKeyCopyExternalRepresentation(key, error.ptr)) { "Could not export the key" }.consume()
    }

    private fun MemScope.createKey(bytes: ByteArray, keyClass: CFStringRef?): SecKeyRef? {
        val attributes = attributes(keyClass)
        val data = bytes.toCFData()
        val error = alloc<CFErrorRefVar>()
        val key = SecKeyCreateWithData(data, attributes, error.ptr)
        CFRelease(data)
        CFRelease(attributes)
        return key
    }

    private fun MemScope.attributes(keyClass: CFStringRef?): CFDictionaryRef {
        val bits = alloc<IntVar> { value = 256 }
        val size = CFNumberCreate(null, kCFNumberIntType, bits.ptr)
        val dictionary = CFDictionaryCreateMutable(null, 3, null, null)
        CFDictionaryAddValue(dictionary, kSecAttrKeyType, kSecAttrKeyTypeECSECPrimeRandom)
        CFDictionaryAddValue(dictionary, kSecAttrKeyClass, keyClass)
        CFDictionaryAddValue(dictionary, kSecAttrKeySizeInBits, size)
        CFRelease(size)
        return checkNotNull(dictionary)
    }

    private fun ByteArray.toCFData(): CFDataRef = checkNotNull(
        if (isEmpty()) CFDataCreate(null, null, 0) else usePinned { CFDataCreate(null, it.addressOf(0).reinterpret(), size.convert()) },
    )

    /** Copies the bytes out and releases the data. */
    private fun CFDataRef.consume(): ByteArray {
        val length: Int = CFDataGetLength(this).convert()
        val bytes = CFDataGetBytePtr(this)?.readBytes(length) ?: ByteArray(0)
        CFRelease(this as CFTypeRef)
        return bytes
    }
}
