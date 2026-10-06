package com.github.nols1000.bibless

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** How many of the latest finishers stay on screen. */
private const val RECENT_RECORDS = 5

/**
 * The finish scanner: the camera pairs each runner's barcode with their finish token. [onScan]
 * gets codes from the camera, [onEnter] typed ones; both return what happened, for the feedback.
 * [camera] is the live camera view, replaceable in tests.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    session: ScannerSession?,
    onStart: () -> Unit,
    onScan: (String) -> ScanOutcome,
    onEnter: (String) -> ScanOutcome,
    onUndo: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
    camera: @Composable (onCode: (String) -> Unit, modifier: Modifier) -> Unit = { onCode, modifier -> CameraScanner(onCode, modifier) },
) {
    var confirmingStop by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scanner") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (session != null) TextButton(onClick = { confirmingStop = true }) { Text("Stop") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (session == null) {
                Spacer(Modifier.weight(1f))
                Text(
                    "Scan each runner's barcode and their finish token, in either order. " +
                        "Tap Start when the first runners come in.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Button(onClick = onStart, modifier = Modifier.fillMaxWidth().height(120.dp), shape = RoundedCornerShape(24.dp)) {
                    Text("Start", fontSize = 40.sp)
                }
                Spacer(Modifier.weight(1f))
            } else {
                Scanning(session, onScan, onEnter, onUndo, camera)
            }
        }
    }

    if (confirmingStop && session != null) {
        AlertDialog(
            onDismissRequest = { confirmingStop = false },
            title = { Text("Stop scanning?") },
            text = {
                Text(
                    "${finishers(session.records.size)} recorded. They stay saved on this phone, " +
                        "but you can't add more to this session.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingStop = false
                    onStop()
                }) { Text("Stop") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingStop = false }) { Text("Keep scanning") }
            },
        )
    }
}

@Composable
private fun ColumnScope.Scanning(
    session: ScannerSession,
    onScan: (String) -> ScanOutcome,
    onEnter: (String) -> ScanOutcome,
    onUndo: () -> Unit,
    camera: @Composable (onCode: (String) -> Unit, modifier: Modifier) -> Unit,
) {
    val feedback = rememberScanFeedback()
    var message by remember { mutableStateOf<ScanMessage?>(null) }
    var typing by remember { mutableStateOf(false) }
    val handle = { outcome: ScanOutcome ->
        outcome.message()?.let {
            message = it
            feedback.play(it.kind)
        }
    }

    camera(
        { code -> handle(onScan(code)) },
        // Stays on while scanning; the screen timing out would stop the camera mid-run
        Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp)).background(Color.Black).keepScreenOn(),
    )
    Text(session.nextStep(), style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("nextStep"))
    message?.let {
        val error = it.kind == ScanFeedbackKind.ERROR
        Text(
            it.text,
            color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.semantics { liveRegion = if (error) LiveRegionMode.Assertive else LiveRegionMode.Polite },
        )
    }
    Text(finishers(session.records.size), style = MaterialTheme.typography.titleSmall)
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        session.records.takeLast(RECENT_RECORDS).asReversed().forEach { record ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("P${record.position}", style = MaterialTheme.typography.titleMedium)
                Text(record.athleteId ?: "Unknown", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val pending = session.pendingAthleteId != null || session.pendingPosition != null
        val last = session.records.lastOrNull()
        OutlinedButton(
            onClick = {
                onUndo()
                message = null
            },
            enabled = pending || last != null,
            modifier = Modifier.weight(1f),
        ) {
            Text(
                when {
                    pending -> "Clear scan"
                    last != null -> "Undo P${last.position}"
                    else -> "Undo"
                },
            )
        }
        OutlinedButton(onClick = { typing = true }, modifier = Modifier.weight(1f)) { Text("Type a code") }
    }

    if (typing) {
        TypeCodeDialog(
            onEnter = {
                typing = false
                handle(onEnter(it))
            },
            onDismiss = { typing = false },
        )
    }
}

@Composable
private fun TypeCodeDialog(onEnter: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Type a code") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Athlete ID or token") },
                placeholder = { Text("A1234567 or P0042") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank()) onEnter(text) }),
            )
        },
        confirmButton = {
            TextButton(onClick = { onEnter(text) }, enabled = text.isNotBlank()) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun finishers(count: Int) = if (count == 1) "1 finisher" else "$count finishers"

/** A code scanner, after Material's "QR code scanner" icon (Apache 2.0). */
val ScannerIcon: ImageVector by lazy {
    ImageVector.Builder("Filled.QrCodeScanner", 24.dp, 24.dp, 24f, 24f)
        .addPath(
            addPathNodes(
                "M9.5,6.5v3h-3v-3H9.5M11,5H5v6h6V5L11,5zM9.5,14.5v3h-3v-3H9.5M11,13H5v6h6V13L11,13z" +
                    "M17.5,6.5v3h-3v-3H17.5M19,5h-6v6h6V5L19,5zM13,13h1.5v1.5H13V13zM14.5,14.5H16V16h-1.5V14.5z" +
                    "M16,13h1.5v1.5H16V13zM13,16h1.5v1.5H13V16zM14.5,17.5H16V19h-1.5V17.5zM16,16h1.5v1.5H16V16z" +
                    "M17.5,14.5H19V16h-1.5V14.5zM17.5,17.5H19V19h-1.5V17.5zM22,7h-2V4h-3V2h5V7zM22,22v-5h-2v3h-3v2H22z" +
                    "M2,22h5v-2H4v-3H2V22zM2,2v5h2V4h3V2H2z",
            ),
            fill = SolidColor(Color.Black),
        )
        .build()
}
