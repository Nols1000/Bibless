package com.github.nols1000.bibless

/** How a scan should sound and feel: a recorded pair, half a pair, or something to look at. */
enum class ScanFeedbackKind { RECORDED, WAITING, ERROR }

/** What the scanner screen says after a scan. Null for scans that changed nothing. */
data class ScanMessage(val text: String, val kind: ScanFeedbackKind)

fun ScanOutcome.message(): ScanMessage? = when (this) {
    is ScanOutcome.Recorded -> warnings.message(record.athleteId, record.position)
        ?: ScanMessage("P${record.position}: ${record.athleteId ?: "Unknown"}", ScanFeedbackKind.RECORDED)
    is ScanOutcome.Waiting -> warnings.message(athleteId, position) ?: ScanMessage(
        if (athleteId != null) "$athleteId: now scan their finish token" else "Token P$position: now scan the runner's barcode",
        ScanFeedbackKind.WAITING,
    )
    is ScanOutcome.NotRecognised -> ScanMessage("Not an athlete barcode or finish token: $text", ScanFeedbackKind.ERROR)
    ScanOutcome.NotRunning, ScanOutcome.Repeat -> null
}

private fun List<ScanWarning>.message(athleteId: String?, position: Int?): ScanMessage? {
    val text = map {
        when (it) {
            ScanWarning.DUPLICATE_ATHLETE -> "$athleteId was already recorded."
            ScanWarning.DUPLICATE_TOKEN -> "Token P$position was already used."
            ScanWarning.REPLACED_ATHLETE -> "The previous runner had no token yet and was dropped."
            ScanWarning.REPLACED_TOKEN -> "The previous token had no runner yet and was dropped."
        }
    }
    return if (text.isEmpty()) null else ScanMessage(text.joinToString(" "), ScanFeedbackKind.ERROR)
}

/** What the scanner still needs, for the line under the camera. */
fun ScannerSession.nextStep(): String = when {
    pendingAthleteId != null -> "Scan the finish token for $pendingAthleteId"
    pendingPosition != null -> "Scan the runner's barcode for token P$pendingPosition"
    else -> "Scan a runner's barcode and their finish token"
}
