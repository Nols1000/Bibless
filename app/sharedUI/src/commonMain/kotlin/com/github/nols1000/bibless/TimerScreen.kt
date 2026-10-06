package com.github.nols1000.bibless

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.materialIcon
import androidx.compose.material.icons.materialPath
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.github.nols1000.bibless.race.RunHint
import kotlinx.coroutines.delay

/** How many of the latest finishes stay on screen. */
private const val RECENT_FINISHES = 5

/**
 * The phone stopwatch: start the clock on the start signal, then one big button per finisher.
 * [elapsedMs] is read for the running clock; it is null while the timer is stopped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerScreen(
    session: TimerSession?,
    joinedRun: RunHint?,
    onJoin: () -> Unit,
    onLeave: () -> Unit,
    elapsedMs: () -> Long?,
    onStart: () -> Unit,
    onFinish: () -> Unit,
    onUndo: () -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
) {
    var confirmingStop by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Timer") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (session == null) {
                JoinedRunCard(joinedRun, onJoin, onLeave)
                NotStarted(onStart)
            } else {
                runLine(session.run, joinedRun)?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
                Running(session, elapsedMs, onFinish, onUndo)
            }
        }
    }

    if (confirmingStop && session != null) {
        AlertDialog(
            onDismissRequest = { confirmingStop = false },
            title = { Text("Stop the timer?") },
            text = {
                Text(
                    "${finishers(session.times.size)} recorded. They stay saved on this phone, " +
                        "but you can't add more finishers to this session.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmingStop = false
                    onStop()
                }) { Text("Stop") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingStop = false }) { Text("Keep timing") }
            },
        )
    }
}

@Composable
private fun ColumnScope.NotStarted(onStart: () -> Unit) {
    Spacer(Modifier.weight(1f))
    Text(
        "Tap Start on the start signal, then Finish each time a runner crosses the line.",
        style = MaterialTheme.typography.bodyLarge,
    )
    Button(
        onClick = onStart,
        modifier = Modifier.fillMaxWidth().weight(1f),
        shape = RoundedCornerShape(24.dp),
    ) {
        Text("Start", fontSize = 40.sp)
    }
}

@Composable
private fun ColumnScope.Running(
    session: TimerSession,
    elapsedMs: () -> Long?,
    onFinish: () -> Unit,
    onUndo: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var elapsed by remember { mutableLongStateOf(elapsedMs() ?: 0) }
    LaunchedEffect(session.id) {
        while (true) {
            elapsed = elapsedMs() ?: 0
            delay(100)
        }
    }
    val last = session.times.lastOrNull()

    Text(
        formatElapsed(elapsed),
        // The runners' clock, readable at arm's length
        style = MaterialTheme.typography.displayLarge.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier.testTag("elapsed"),
    )
    Text(
        finishers(session.times.size),
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    Column(
        modifier = Modifier.fillMaxWidth().weight(1f),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        session.times.takeLast(RECENT_FINISHES).asReversed().forEach { time ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("P${time.position}", style = MaterialTheme.typography.titleLarge)
                Text(formatElapsed(time.elapsedMs), style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.Monospace))
            }
        }
    }
    OutlinedButton(onClick = onUndo, enabled = last != null, modifier = Modifier.fillMaxWidth()) {
        Text(if (last != null) "Undo P${last.position} (${formatElapsed(last.elapsedMs)})" else "Undo")
    }
    Button(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.Confirm)
            onFinish()
        },
        // Stays on while timing; the screen timing out mid-run would cost finishes
        modifier = Modifier.fillMaxWidth().height(220.dp).keepScreenOn(),
        shape = RoundedCornerShape(24.dp),
    ) {
        Text("Finish", fontSize = 48.sp)
    }
}

private fun finishers(count: Int) = if (count == 1) "1 finisher" else "$count finishers"

/** A stopwatch, after Material's "Timer" icon (Apache 2.0). */
val TimerIcon: ImageVector by lazy {
    materialIcon(name = "Filled.Timer") {
        materialPath {
            moveTo(15f, 1f); horizontalLineTo(9f); verticalLineToRelative(2f); horizontalLineToRelative(6f); verticalLineTo(1f); close()
            moveTo(11f, 14f); horizontalLineToRelative(2f); verticalLineTo(8f); horizontalLineToRelative(-2f); verticalLineToRelative(6f); close()
            moveTo(19.03f, 7.39f); lineToRelative(1.42f, -1.42f)
            curveToRelative(-0.43f, -0.51f, -0.9f, -0.99f, -1.41f, -1.41f)
            lineToRelative(-1.42f, 1.42f)
            curveTo(16.07f, 4.74f, 14.12f, 4f, 12f, 4f)
            curveToRelative(-4.97f, 0f, -9f, 4.03f, -9f, 9f)
            reflectiveCurveToRelative(4.02f, 9f, 9f, 9f)
            reflectiveCurveToRelative(9f, -4.03f, 9f, -9f)
            curveToRelative(0f, -2.12f, -0.74f, -4.07f, -1.97f, -5.61f)
            close()
            moveTo(12f, 20f)
            curveToRelative(-3.87f, 0f, -7f, -3.13f, -7f, -7f)
            reflectiveCurveToRelative(3.13f, -7f, 7f, -7f)
            reflectiveCurveToRelative(7f, 3.13f, 7f, 7f)
            reflectiveCurveToRelative(-3.13f, 7f, -7f, 7f)
            close()
        }
    }
}
