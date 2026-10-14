package com.github.nols1000.bibless

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.github.nols1000.bibless.race.RunHint

/** Name, date and timing mode of [run], for one line of text. */
fun describeRun(run: RunHint): String =
    listOfNotNull(run.date?.let(::formatRunDate), run.format.label()).joinToString(" · ")

/**
 * Which run this device records for, on the timer's and scanner's start screens. Joining is only
 * offered there: a session keeps the run it started with.
 */
@Composable
fun JoinedRunCard(run: RunHint?, onJoin: () -> Unit, onLeave: () -> Unit) {
    Card(Modifier.fillMaxWidth().testTag("joinedRun")) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                if (run == null) {
                    Text("No run", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Scan the run's QR code from the organiser, or record now and add it to the run later.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                } else {
                    Text(run.name, style = MaterialTheme.typography.titleMedium)
                    Text(describeRun(run), style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (run == null) {
                TextButton(onClick = onJoin) { Text("Join run") }
            } else {
                TextButton(onClick = onLeave) { Text("Leave") }
            }
        }
    }
}

/** The run a running session records for, as one line; null without a run. */
fun runLine(sessionRun: String?, joined: RunHint?): String? = when {
    sessionRun == null -> null
    joined?.run == sessionRun -> joined.name
    else -> "Joined run"
}

/**
 * Scans a run's QR code. [onCode] gets every code the camera sees and returns the run it joined,
 * or null for codes that aren't run codes; the screen closes through [onJoined].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun JoinRunScreen(
    onCode: (String) -> RunHint?,
    onJoined: () -> Unit,
    onBack: () -> Unit,
    camera: @Composable (onCode: (String) -> Unit, modifier: Modifier) -> Unit = { code, modifier -> CameraScanner(code, modifier) },
) {
    var notARun by remember { mutableStateOf(false) }
    val feedback = rememberScanFeedback()
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Join a run") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            camera(
                { code ->
                    if (onCode(code) != null) {
                        feedback.play(ScanFeedbackKind.RECORDED)
                        onJoined()
                    } else if (!notARun) {
                        notARun = true
                        feedback.play(ScanFeedbackKind.ERROR)
                    }
                },
                Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(16.dp)),
            )
            Text(
                if (notARun) {
                    "That's not a run's QR code. Scan the code on the organiser's run sheet or screen."
                } else {
                    "Scan the QR code on the organiser's run sheet or screen."
                },
                style = MaterialTheme.typography.bodyLarge,
                color = if (notARun) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
