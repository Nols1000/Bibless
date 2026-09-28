package com.github.nols1000.bibless

private val ATHLETE_ID = Regex("A\\d{1,10}")

/**
 * Normalizes user input into a parkrun athlete ID such as `A1234567`: trims it, uppercases it and
 * prefixes a bare number with `A`. Returns `null` if the result is not a valid ID.
 */
fun normalizeAthleteId(input: String): String? {
    val trimmed = input.trim().uppercase()
    val id = if (trimmed.isNotEmpty() && trimmed.all { it.isDigit() }) "A$trimmed" else trimmed
    return id.takeIf { ATHLETE_ID.matches(it) }
}
