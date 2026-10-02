package com.github.nols1000.bibless.race.crypto

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A key and signature made with `openssl`, so every platform is checked against the same outside source. */
private const val PUBLIC = "BIEMaEivZPDJimoBRwEY60pg4xG4LDMiBV2Rx4F7V4d0jah3WWKurb2d6sn3cGfI0WiwkRDJGIxL2L-w1CuiHZs"
private const val PRIVATE_KEY =
    "BIEMaEivZPDJimoBRwEY60pg4xG4LDMiBV2Rx4F7V4d0jah3WWKurb2d6sn3cGfI0WiwkRDJGIxL2L-w1CuiHZsKuulCxtdLqjvUty_TgbcpnN43l5NnOrh00hvb1x4duA"
private const val MESSAGE = "Bibless test vector"
private const val DER =
    "3045022100d654121642cb62eaa98c4ce1676e045782c1150bf8bf0ea035d9292e4cac1e03022049bb80a0078cf2ad921340c28cad92d082ee9053d7e55690867657deefe81cdc"
private const val RAW =
    "d654121642cb62eaa98c4ce1676e045782c1150bf8bf0ea035d9292e4cac1e0349bb80a0078cf2ad921340c28cad92d082ee9053d7e55690867657deefe81cdc"

private fun String.hex(): ByteArray = chunked(2).map { it.toInt(16).toByte() }.toByteArray()

private fun ByteArray.hex(): String = joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

class CryptoTest {

    @Test
    fun derConvertsToRawAndBack() {
        assertEquals(RAW, derToRaw(DER.hex()).hex())
        assertEquals(DER, rawToDer(RAW.hex()).hex())
    }

    @Test
    fun derKeepsShortIntegersPadded() {
        val raw = ByteArray(31) + byteArrayOf(5) + ByteArray(31) + byteArrayOf(0x7F)
        assertEquals("300602010502017f", rawToDer(raw).hex())
        assertContentEquals(raw, derToRaw(rawToDer(raw)))
    }

    @Test
    fun sha256MatchesKnownDigests() = runTest {
        assertEquals("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855", sha256(ByteArray(0)).hex())
        assertEquals(
            "d2907fa87f4b6679f5c9e478d2cd7985518a7fee5c8ea3652c2de3df201fe69b",
            sha256(MESSAGE.encodeToByteArray()).hex(),
        )
    }

    @Test
    fun opensslSignatureVerifies() = runTest {
        assertTrue(verifySignature(PUBLIC, MESSAGE.encodeToByteArray(), RAW.hex()))
        assertFalse(verifySignature(PUBLIC, "Bibless test vectoR".encodeToByteArray(), RAW.hex()))
    }

    @Test
    fun importedOpensslKeySignsVerifiably() = runTest {
        val key = DeviceKey.import(PRIVATE_KEY)
        assertEquals(PUBLIC, key.author)
        assertEquals(PRIVATE_KEY, key.export())
        val signature = key.sign(MESSAGE.encodeToByteArray())
        assertTrue(verifySignature(PUBLIC, MESSAGE.encodeToByteArray(), signature))
    }

    @Test
    fun generatedKeyRoundTrips() = runTest {
        val key = DeviceKey.generate()
        val restored = DeviceKey.import(key.export())
        assertEquals(key.author, restored.author)
        val data = "finish".encodeToByteArray()
        assertTrue(verifySignature(key.author, data, restored.sign(data)))
        assertFalse(verifySignature(DeviceKey.generate().author, data, key.sign(data)))
    }

    @Test
    fun malformedInputIsRejected() = runTest {
        val data = "finish".encodeToByteArray()
        assertFalse(verifySignature("not a key", data, RAW.hex()))
        assertFalse(verifySignature(PUBLIC, data, ByteArray(10)))
        assertFailsWith<IllegalArgumentException> { DeviceKey.import(PUBLIC) }
    }
}
