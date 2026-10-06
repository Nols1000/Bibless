package com.github.nols1000.bibless

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A live camera view that reports every Code 128 and QR code it sees through [onCode], frame
 * after frame; the caller decides what counts as a new scan. Asks for camera access first.
 */
@Composable
expect fun CameraScanner(onCode: (String) -> Unit, modifier: Modifier = Modifier)

/** Plays the sound and vibration for a scan. */
interface ScanFeedback {
    fun play(kind: ScanFeedbackKind)
}

@Composable
expect fun rememberScanFeedback(): ScanFeedback
