package com.github.nols1000.bibless

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.nols1000.bibless.race.RunHint
import com.github.nols1000.bibless.race.TimingMode

/** Shares exported files through the system share sheet. */
@Composable
expect fun rememberShareFiles(): (List<ExportFile>) -> Unit

/** The device name export files carry, so the organiser can tell several phones' files apart. */
@Composable
expect fun rememberDeviceName(): String

/** Formats [millis] since the epoch as a local date and time for people. */
expect fun formatSessionStart(millis: Long): String

/** Minutes the local time zone is ahead of UTC at [millis]. */
expect fun utcOffsetMinutes(millis: Long): Int

/**
 * The timer and scanner sessions recorded on this phone, each shared as files for the organiser:
 * a CSV for people and a JSON bundle for the Bibless web app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SessionsScreen(
    sessions: List<RecordedSession>,
    joinedRun: RunHint?,
    onShare: (RecordedSession) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recorded sessions") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (sessions.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    "Timer and scanner sessions show up here, to share with the organiser.",
                    textAlign = TextAlign.Center,
                )
            }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            item {
                Text(
                    "Share a session with the organiser. The JSON file imports into the Bibless web app; " +
                        "the CSV opens in a spreadsheet.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            items(sessions, key = { it.id }) { session ->
                ListItem(
                    headlineContent = { Text(sessionTitle(session)) },
                    supportingContent = {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(formatSessionStart(session.startedAt))
                            Text(sessionDetails(session, joinedRun))
                        }
                    },
                    trailingContent = {
                        TextButton(onClick = { onShare(session) }, modifier = Modifier.testTag("share-${session.id}")) { Text("Share") }
                    },
                )
            }
        }
    }
}

private fun sessionTitle(session: RecordedSession) = when (session.kind) {
    SessionKind.TIMER -> "Timer"
    SessionKind.SCANNER -> if (session.mode == TimingMode.SCAN_ORDER) "Scanner, scan order" else "Scanner"
}

private fun sessionDetails(session: RecordedSession, joinedRun: RunHint?): String = listOfNotNull(
    when (session.kind) {
        SessionKind.TIMER -> if (session.count == 1) "1 finish time" else "${session.count} finish times"
        SessionKind.SCANNER -> if (session.count == 1) "1 finisher" else "${session.count} finishers"
    },
    when {
        session.run == null -> "no run"
        else -> joinedRun?.takeIf { it.run == session.run }?.name
    },
    "recording".takeIf { session.running },
).joinToString(" · ")
