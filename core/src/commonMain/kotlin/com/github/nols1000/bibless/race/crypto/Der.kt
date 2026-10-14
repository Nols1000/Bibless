package com.github.nols1000.bibless.race.crypto

/**
 * Converts between the DER `SEQUENCE { INTEGER r, INTEGER s }` that the JVM and Apple produce and
 * the raw `r||s` that WebCrypto uses and Bibless stores.
 */
internal fun derToRaw(der: ByteArray): ByteArray {
    var i = 0
    fun next(): Int = der[i++].toInt() and 0xFF
    fun length(): Int {
        val first = next()
        if (first < 0x80) return first
        return (0 until (first and 0x7F)).fold(0) { acc, _ -> acc shl 8 or next() }
    }
    fun integer(): ByteArray {
        require(next() == 0x02) { "Expected an INTEGER" }
        val len = length()
        val value = der.copyOfRange(i, i + len)
        i += len
        return value.leftPad()
    }
    require(next() == 0x30) { "Expected a SEQUENCE" }
    length()
    return integer() + integer()
}

internal fun rawToDer(raw: ByteArray): ByteArray {
    require(raw.size == 2 * P256_SIZE) { "Expected ${2 * P256_SIZE} bytes, got ${raw.size}" }
    fun integer(value: ByteArray): ByteArray {
        var start = 0
        while (start < value.size - 1 && value[start] == 0.toByte()) start++
        val trimmed = value.copyOfRange(start, value.size)
        // A leading 1 bit would make the INTEGER negative
        val bytes = if (trimmed[0] < 0) byteArrayOf(0) + trimmed else trimmed
        return byteArrayOf(0x02, bytes.size.toByte()) + bytes
    }
    val body = integer(raw.copyOfRange(0, P256_SIZE)) + integer(raw.copyOfRange(P256_SIZE, raw.size))
    return byteArrayOf(0x30, body.size.toByte()) + body
}

/** Drops a sign byte and pads with zeros to exactly [P256_SIZE] bytes. */
internal fun ByteArray.leftPad(size: Int = P256_SIZE): ByteArray {
    var start = 0
    while (this.size - start > size && this[start] == 0.toByte()) start++
    val trimmed = copyOfRange(start, this.size)
    require(trimmed.size <= size) { "Value longer than $size bytes" }
    return ByteArray(size - trimmed.size) + trimmed
}
