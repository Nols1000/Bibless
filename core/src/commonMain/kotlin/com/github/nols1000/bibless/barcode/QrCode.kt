/*
 * QR Code encoder, trimmed Kotlin port of Project Nayuki's QR Code generator library
 * (byte mode only). https://www.nayuki.io/page/qr-code-generator-library
 *
 * Copyright (c) Project Nayuki. (MIT License)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 * - The above copyright notice and this permission notice shall be included in
 *   all copies or substantial portions of the Software.
 * - The Software is provided "as is", without warranty of any kind, express or
 *   implied, including but not limited to the warranties of merchantability,
 *   fitness for a particular purpose and noninfringement. In no event shall the
 *   authors or copyright holders be liable for any claim, damages or other
 *   liability, whether in an action of contract, tort or otherwise, arising from,
 *   out of or in connection with the Software or the use or other dealings in the
 *   Software.
 */
package com.github.nols1000.bibless.barcode

import kotlin.math.abs

enum class QrEcc(internal val formatBits: Int) { LOW(1), MEDIUM(0), QUARTILE(3), HIGH(2) }

/** Encodes [text] as UTF-8 bytes into the smallest QR code that fits. Excludes the quiet zone. */
fun encodeQr(text: String, ecc: QrEcc = QrEcc.MEDIUM): BitMatrix {
    val data = text.encodeToByteArray()
    val version = (1..40).firstOrNull { v ->
        4 + charCountBits(v) + data.size * 8 <= numDataCodewords(v, ecc) * 8
    } ?: throw IllegalArgumentException("Text too long for a QR code")

    val capacityBits = numDataCodewords(version, ecc) * 8
    val bits = BitBuffer()
    bits.append(0x4, 4)
    bits.append(data.size, charCountBits(version))
    data.forEach { bits.append(it.toInt() and 0xFF, 8) }
    bits.append(0, minOf(4, capacityBits - bits.size))
    bits.append(0, (8 - bits.size % 8) % 8)
    var pad = 0xEC
    while (bits.size < capacityBits) {
        bits.append(pad, 8)
        pad = pad xor 0xEC xor 0x11
    }
    val codewords = ByteArray(bits.size / 8)
    for (i in 0 until bits.size) {
        if (bits[i]) codewords[i ushr 3] = (codewords[i ushr 3].toInt() or (1 shl (7 - (i and 7)))).toByte()
    }

    return QrBuilder(version, ecc).build(codewords)
}

private fun charCountBits(version: Int) = if (version <= 9) 8 else 16

private class BitBuffer {
    private val bits = ArrayList<Boolean>()
    val size get() = bits.size
    operator fun get(i: Int) = bits[i]
    fun append(value: Int, length: Int) {
        for (i in length - 1 downTo 0) bits += ((value ushr i) and 1) != 0
    }
}

private class QrBuilder(private val version: Int, private val ecc: QrEcc) {
    private val size = version * 4 + 17
    private val modules = Array(size) { BooleanArray(size) }
    private val isFunction = Array(size) { BooleanArray(size) }

    fun build(dataCodewords: ByteArray): BitMatrix {
        drawFunctionPatterns()
        drawCodewords(addEccAndInterleave(dataCodewords))

        var bestMask = 0
        var minPenalty = Int.MAX_VALUE
        for (mask in 0 until 8) {
            applyMask(mask)
            drawFormatBits(mask)
            val penalty = penaltyScore()
            if (penalty < minPenalty) {
                bestMask = mask
                minPenalty = penalty
            }
            applyMask(mask) // XOR undoes it
        }
        applyMask(bestMask)
        drawFormatBits(bestMask)

        val matrix = BitMatrix(size, size)
        for (y in 0 until size) for (x in 0 until size) matrix[x, y] = modules[y][x]
        return matrix
    }

    private fun setFunctionModule(x: Int, y: Int, dark: Boolean) {
        modules[y][x] = dark
        isFunction[y][x] = true
    }

    private fun drawFunctionPatterns() {
        for (i in 0 until size) {
            setFunctionModule(6, i, i % 2 == 0)
            setFunctionModule(i, 6, i % 2 == 0)
        }
        drawFinderPattern(3, 3)
        drawFinderPattern(size - 4, 3)
        drawFinderPattern(3, size - 4)

        val positions = alignmentPatternPositions()
        val n = positions.size
        for (i in 0 until n) for (j in 0 until n) {
            if (!(i == 0 && j == 0 || i == 0 && j == n - 1 || i == n - 1 && j == 0)) {
                drawAlignmentPattern(positions[i], positions[j])
            }
        }
        drawFormatBits(0) // Placeholder so the format area is reserved; redrawn per mask.
        drawVersion()
    }

    private fun drawFormatBits(mask: Int) {
        val data = ecc.formatBits shl 3 or mask
        var rem = data
        repeat(10) { rem = (rem shl 1) xor ((rem ushr 9) * 0x537) }
        val bits = (data shl 10 or rem) xor 0x5412

        for (i in 0..5) setFunctionModule(8, i, bit(bits, i))
        setFunctionModule(8, 7, bit(bits, 6))
        setFunctionModule(8, 8, bit(bits, 7))
        setFunctionModule(7, 8, bit(bits, 8))
        for (i in 9 until 15) setFunctionModule(14 - i, 8, bit(bits, i))

        for (i in 0 until 8) setFunctionModule(size - 1 - i, 8, bit(bits, i))
        for (i in 8 until 15) setFunctionModule(8, size - 15 + i, bit(bits, i))
        setFunctionModule(8, size - 8, true)
    }

    private fun drawVersion() {
        if (version < 7) return
        var rem = version
        repeat(12) { rem = (rem shl 1) xor ((rem ushr 11) * 0x1F25) }
        val bits = version shl 12 or rem
        for (i in 0 until 18) {
            val dark = bit(bits, i)
            val a = size - 11 + i % 3
            val b = i / 3
            setFunctionModule(a, b, dark)
            setFunctionModule(b, a, dark)
        }
    }

    private fun drawFinderPattern(x: Int, y: Int) {
        for (dy in -4..4) for (dx in -4..4) {
            val dist = maxOf(abs(dx), abs(dy))
            val xx = x + dx
            val yy = y + dy
            if (xx in 0 until size && yy in 0 until size) setFunctionModule(xx, yy, dist != 2 && dist != 4)
        }
    }

    private fun drawAlignmentPattern(x: Int, y: Int) {
        for (dy in -2..2) for (dx in -2..2) setFunctionModule(x + dx, y + dy, maxOf(abs(dx), abs(dy)) != 1)
    }

    private fun alignmentPatternPositions(): IntArray {
        if (version == 1) return IntArray(0)
        val n = version / 7 + 2
        val step = (version * 8 + n * 3 + 5) / (n * 4 - 4) * 2
        val result = IntArray(n)
        result[0] = 6
        var pos = size - 7
        for (i in n - 1 downTo 1) {
            result[i] = pos
            pos -= step
        }
        return result
    }

    private fun addEccAndInterleave(data: ByteArray): ByteArray {
        val numBlocks = NUM_ERROR_CORRECTION_BLOCKS[ecc.ordinal][version]
        val blockEccLen = ECC_CODEWORDS_PER_BLOCK[ecc.ordinal][version]
        val rawCodewords = numRawDataModules(version) / 8
        val numShortBlocks = numBlocks - rawCodewords % numBlocks
        val shortBlockLen = rawCodewords / numBlocks

        val divisor = reedSolomonDivisor(blockEccLen)
        val blocks = ArrayList<ByteArray>()
        var k = 0
        for (i in 0 until numBlocks) {
            val datLen = shortBlockLen - blockEccLen + if (i < numShortBlocks) 0 else 1
            val dat = data.copyOfRange(k, k + datLen)
            k += datLen
            val block = ByteArray(shortBlockLen + 1)
            dat.copyInto(block)
            reedSolomonRemainder(dat, divisor).copyInto(block, block.size - blockEccLen)
            blocks += block
        }

        val result = ByteArray(rawCodewords)
        k = 0
        for (i in blocks[0].indices) {
            for (j in blocks.indices) {
                // Short blocks have no byte at the padding position.
                if (i != shortBlockLen - blockEccLen || j >= numShortBlocks) result[k++] = blocks[j][i]
            }
        }
        return result
    }

    private fun drawCodewords(data: ByteArray) {
        var i = 0
        var right = size - 1
        while (right >= 1) {
            if (right == 6) right = 5
            for (vert in 0 until size) {
                for (j in 0 until 2) {
                    val x = right - j
                    val upward = ((right + 1) and 2) == 0
                    val y = if (upward) size - 1 - vert else vert
                    if (!isFunction[y][x] && i < data.size * 8) {
                        modules[y][x] = bit(data[i ushr 3].toInt(), 7 - (i and 7))
                        i++
                    }
                }
            }
            right -= 2
        }
    }

    private fun applyMask(mask: Int) {
        for (y in 0 until size) for (x in 0 until size) {
            val invert = when (mask) {
                0 -> (x + y) % 2 == 0
                1 -> y % 2 == 0
                2 -> x % 3 == 0
                3 -> (x + y) % 3 == 0
                4 -> (x / 3 + y / 2) % 2 == 0
                5 -> x * y % 2 + x * y % 3 == 0
                6 -> (x * y % 2 + x * y % 3) % 2 == 0
                else -> ((x + y) % 2 + x * y % 3) % 2 == 0
            }
            modules[y][x] = modules[y][x] xor (invert && !isFunction[y][x])
        }
    }

    private fun penaltyScore(): Int {
        var result = 0
        for (horizontal in listOf(true, false)) {
            for (a in 0 until size) {
                var runColor = false
                var run = 0
                val history = IntArray(7)
                for (b in 0 until size) {
                    val color = if (horizontal) modules[a][b] else modules[b][a]
                    if (color == runColor) {
                        run++
                        if (run == 5) result += PENALTY_N1 else if (run > 5) result++
                    } else {
                        addHistory(run, history)
                        if (!runColor) result += countFinderPatterns(history) * PENALTY_N3
                        runColor = color
                        run = 1
                    }
                }
                if (runColor) {
                    addHistory(run, history)
                    run = 0
                }
                addHistory(run + size, history)
                result += countFinderPatterns(history) * PENALTY_N3
            }
        }
        for (y in 0 until size - 1) for (x in 0 until size - 1) {
            val c = modules[y][x]
            if (c == modules[y][x + 1] && c == modules[y + 1][x] && c == modules[y + 1][x + 1]) result += PENALTY_N2
        }
        val dark = modules.sumOf { row -> row.count { it } }
        val total = size * size
        val k = (abs(dark * 20 - total * 10) + total - 1) / total - 1
        result += k * PENALTY_N4
        return result
    }

    private fun addHistory(runLength: Int, history: IntArray) {
        val length = if (history[0] == 0) runLength + size else runLength
        history.copyInto(history, 1, 0, history.size - 1)
        history[0] = length
    }

    private fun countFinderPatterns(h: IntArray): Int {
        val n = h[1]
        val core = n > 0 && h[2] == n && h[3] == n * 3 && h[4] == n && h[5] == n
        return (if (core && h[0] >= n * 4 && h[6] >= n) 1 else 0) +
            (if (core && h[6] >= n * 4 && h[0] >= n) 1 else 0)
    }
}

private fun bit(x: Int, i: Int) = ((x ushr i) and 1) != 0

internal fun numRawDataModules(version: Int): Int {
    var result = (16 * version + 128) * version + 64
    if (version >= 2) {
        val n = version / 7 + 2
        result -= (25 * n - 10) * n - 55
        if (version >= 7) result -= 36
    }
    return result
}

internal fun numDataCodewords(version: Int, ecc: QrEcc) =
    numRawDataModules(version) / 8 -
        ECC_CODEWORDS_PER_BLOCK[ecc.ordinal][version] * NUM_ERROR_CORRECTION_BLOCKS[ecc.ordinal][version]

private fun reedSolomonDivisor(degree: Int): IntArray {
    val result = IntArray(degree)
    result[degree - 1] = 1
    var root = 1
    repeat(degree) {
        for (j in 0 until degree) {
            result[j] = reedSolomonMultiply(result[j], root)
            if (j + 1 < degree) result[j] = result[j] xor result[j + 1]
        }
        root = reedSolomonMultiply(root, 0x02)
    }
    return result
}

private fun reedSolomonRemainder(data: ByteArray, divisor: IntArray): ByteArray {
    val result = IntArray(divisor.size)
    for (b in data) {
        val factor = (b.toInt() and 0xFF) xor result[0]
        result.copyInto(result, 0, 1, result.size)
        result[result.size - 1] = 0
        for (i in result.indices) result[i] = result[i] xor reedSolomonMultiply(divisor[i], factor)
    }
    return ByteArray(result.size) { result[it].toByte() }
}

private fun reedSolomonMultiply(x: Int, y: Int): Int {
    var z = 0
    for (i in 7 downTo 0) {
        z = (z shl 1) xor ((z ushr 7) * 0x11D)
        z = z xor (((y ushr i) and 1) * x)
    }
    return z
}

private const val PENALTY_N1 = 3
private const val PENALTY_N2 = 3
private const val PENALTY_N3 = 40
private const val PENALTY_N4 = 10

// Indexed by QrEcc.ordinal (LOW, MEDIUM, QUARTILE, HIGH), then by version (index 0 unused).
private val ECC_CODEWORDS_PER_BLOCK = arrayOf(
    intArrayOf(-1, 7, 10, 15, 20, 26, 18, 20, 24, 30, 18, 20, 24, 26, 30, 22, 24, 28, 30, 28, 28, 28, 28, 30, 30, 26, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),
    intArrayOf(-1, 10, 16, 26, 18, 24, 16, 18, 22, 22, 26, 30, 22, 22, 24, 24, 28, 28, 26, 26, 26, 26, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28, 28),
    intArrayOf(-1, 13, 22, 18, 26, 18, 24, 18, 22, 20, 24, 28, 26, 24, 20, 30, 24, 28, 28, 26, 30, 28, 30, 30, 30, 30, 28, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),
    intArrayOf(-1, 17, 28, 22, 16, 22, 28, 26, 26, 24, 28, 24, 28, 22, 24, 24, 30, 28, 28, 26, 28, 30, 24, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30),
)

private val NUM_ERROR_CORRECTION_BLOCKS = arrayOf(
    intArrayOf(-1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 4, 4, 4, 4, 4, 6, 6, 6, 6, 7, 8, 8, 9, 9, 10, 12, 12, 12, 13, 14, 15, 16, 17, 18, 19, 19, 20, 21, 22, 24, 25),
    intArrayOf(-1, 1, 1, 1, 2, 2, 4, 4, 4, 5, 5, 5, 8, 9, 9, 10, 10, 11, 13, 14, 16, 17, 17, 18, 20, 21, 23, 25, 26, 28, 29, 31, 33, 35, 37, 38, 40, 43, 45, 47, 49),
    intArrayOf(-1, 1, 1, 2, 2, 4, 4, 6, 6, 8, 8, 8, 10, 12, 16, 12, 17, 16, 18, 21, 20, 23, 23, 25, 27, 29, 34, 34, 35, 38, 40, 43, 45, 48, 51, 53, 56, 59, 62, 65, 68),
    intArrayOf(-1, 1, 1, 2, 4, 4, 4, 5, 6, 8, 8, 11, 11, 16, 16, 18, 16, 19, 21, 25, 25, 25, 34, 30, 32, 35, 37, 40, 42, 45, 48, 51, 54, 57, 60, 63, 66, 70, 74, 77, 81),
)
