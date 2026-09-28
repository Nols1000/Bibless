package com.github.nols1000.bibless.barcode

/**
 * Encodes [text] (printable ASCII) as a Code 128 barcode, switching between code sets B and C so
 * digit runs take half the width. The result excludes the quiet zone; renderers add it.
 */
fun encodeCode128(text: String): BitMatrix {
    require(text.isNotEmpty()) { "Nothing to encode" }
    require(text.all { it in ' '..'\u007F' }) { "Code 128 set B only supports printable ASCII" }

    val symbols = code128Symbols(text)
    val checksum = symbols.withIndex().sumOf { (i, v) -> if (i == 0) v else i * v } % 103
    val widths = (symbols + checksum).joinToString("") { PATTERNS[it] } + STOP_PATTERN

    val matrix = BitMatrix(widths.sumOf { it.digitToInt() }, 1)
    var x = 0
    widths.forEachIndexed { i, w ->
        repeat(w.digitToInt()) { matrix[x++, 0] = i % 2 == 0 }
    }
    return matrix
}

/** Symbol values from the start code up to (not including) the checksum. */
internal fun code128Symbols(text: String): List<Int> {
    fun digitRun(from: Int): Int {
        var end = from
        while (end < text.length && text[end].isDigit()) end++
        return end - from
    }

    val symbols = mutableListOf<Int>()
    var inC: Boolean
    var i = 0

    val leadingDigits = digitRun(0)
    if (leadingDigits >= 4 || (leadingDigits == text.length && leadingDigits % 2 == 0)) {
        symbols += START_C
        inC = true
    } else {
        symbols += START_B
        inC = false
    }

    while (i < text.length) {
        if (inC) {
            if (digitRun(i) >= 2) {
                symbols += (text[i] - '0') * 10 + (text[i + 1] - '0')
                i += 2
            } else {
                symbols += CODE_B
                inC = false
            }
        } else {
            val run = digitRun(i)
            if (run >= 6 || (run >= 4 && i + run == text.length)) {
                // Odd runs keep their first digit in set B so the rest pairs up in set C.
                if (run % 2 == 1) {
                    symbols += text[i] - ' '
                    i++
                }
                symbols += CODE_C
                inC = true
            } else {
                symbols += text[i] - ' '
                i++
            }
        }
    }
    return symbols
}

private const val CODE_C = 99
private const val CODE_B = 100
private const val START_B = 104
private const val START_C = 105

private const val STOP_PATTERN = "2331112"

/** Bar/space module widths for symbol values 0..105. */
internal val PATTERNS = listOf(
    "212222", "222122", "222221", "121223", "121322", "131222", "122213", "122312", "132212", "221213",
    "221312", "231212", "112232", "122132", "122231", "113222", "123122", "123221", "223211", "221132",
    "221231", "213212", "223112", "312131", "311222", "321122", "321221", "312212", "322112", "322211",
    "212123", "212321", "232121", "111323", "131123", "131321", "112313", "132113", "132311", "211313",
    "231113", "231311", "112133", "112331", "132131", "113123", "113321", "133121", "313121", "211331",
    "231131", "213113", "213311", "213131", "311123", "311321", "331121", "312113", "312311", "332111",
    "314111", "221411", "431111", "111224", "111422", "121124", "121421", "141122", "141221", "112214",
    "112412", "122114", "122411", "142112", "142211", "241211", "221114", "413111", "241112", "134111",
    "111242", "121142", "121241", "114212", "124112", "124211", "411212", "421112", "421211", "212141",
    "214121", "412121", "111143", "111341", "131141", "114113", "114311", "411113", "411311", "113141",
    "114131", "311141", "411131", "211412", "211214", "211232",
)
