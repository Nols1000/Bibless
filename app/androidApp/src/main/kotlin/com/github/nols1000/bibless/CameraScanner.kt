package com.github.nols1000.bibless

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageAnalysis
import androidx.camera.mlkit.vision.MlKitAnalyzer
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode

/**
 * A live camera view that reports every Code 128 and QR code it sees through [onCode], frame
 * after frame; the caller decides what counts as a new scan. Asks for camera access first.
 */
@Composable
fun CameraScanner(onCode: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var granted by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it }
    LaunchedEffect(Unit) {
        if (!granted) request.launch(Manifest.permission.CAMERA)
    }
    if (!granted) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                "Allow camera access to scan, or type the codes in.",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp),
            )
        }
        return
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnCode by rememberUpdatedState(onCode)
    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_CODE_128, Barcode.FORMAT_QR_CODE).build(),
        )
    }
    val controller = remember {
        LifecycleCameraController(context).apply {
            imageAnalysisBackpressureStrategy = ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST
            val main = ContextCompat.getMainExecutor(context)
            setImageAnalysisAnalyzer(
                main,
                MlKitAnalyzer(listOf(scanner), ImageAnalysis.COORDINATE_SYSTEM_ORIGINAL, main) { result ->
                    result.getValue(scanner)?.forEach { barcode -> barcode.rawValue?.let { currentOnCode(it) } }
                },
            )
        }
    }
    DisposableEffect(lifecycleOwner) {
        controller.bindToLifecycle(lifecycleOwner)
        onDispose {
            controller.unbind()
            scanner.close()
        }
    }
    AndroidView(
        factory = { PreviewView(it).apply { this.controller = controller } },
        modifier = modifier,
    )
}

/** Plays the sound and vibration for a scan. */
interface ScanFeedback {
    fun play(kind: ScanFeedbackKind)
}

@Composable
fun rememberScanFeedback(): ScanFeedback {
    val haptics = LocalHapticFeedback.current
    val tones = remember { runCatching { ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90) }.getOrNull() }
    DisposableEffect(tones) { onDispose { tones?.release() } }
    return remember(haptics, tones) {
        object : ScanFeedback {
            override fun play(kind: ScanFeedbackKind) {
                when (kind) {
                    ScanFeedbackKind.RECORDED -> {
                        tones?.startTone(ToneGenerator.TONE_PROP_BEEP2, 150)
                        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    }
                    ScanFeedbackKind.WAITING -> {
                        tones?.startTone(ToneGenerator.TONE_PROP_BEEP, 80)
                        haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    }
                    ScanFeedbackKind.ERROR -> {
                        tones?.startTone(ToneGenerator.TONE_SUP_ERROR, 400)
                        haptics.performHapticFeedback(HapticFeedbackType.Reject)
                    }
                }
            }
        }
    }
}
