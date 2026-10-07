package com.github.nols1000.bibless.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ButtonGroup
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextButton
import androidx.wear.compose.material3.TextButtonDefaults
import androidx.wear.compose.material3.TextDefaults
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.tooling.preview.devices.WearDevices
import com.github.nols1000.bibless.FinishTime
import com.github.nols1000.bibless.TimerSession
import com.github.nols1000.bibless.formatElapsed
import kotlinx.coroutines.delay

/**
 * The watch stopwatch for timekeepers: one big button per finisher, with a buzz for each, so
 * their eyes can stay on the line. In ambient mode it shows the minutes and the count only.
 */
@Composable
fun TimerScreen(
    session: TimerSession?,
    elapsedMs: () -> Long?,
    ambient: Boolean,
    onStart: () -> Unit,
    onFinish: () -> FinishTime?,
    onUndo: () -> Unit,
    onStop: () -> Unit,
) {
    when {
        session == null -> Stopped(onStart)
        ambient -> Ambient(session, elapsedMs() ?: 0)
        else -> Running(session, elapsedMs, onFinish, onUndo, onStop)
    }
}

@Composable
private fun Stopped(onStart: () -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    ScreenScaffold(
        scrollState = listState,
        edgeButton = {
            EdgeButton(onClick = onStart) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                Text("Start")
            }
        },
    ) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader(
                    modifier = Modifier.transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(
                            ListHeaderDefaults.minimumTopListContentPadding,
                            ListHeaderDefaults.minimumBottomListContentPadding,
                        ),
                    transformation = SurfaceTransformation(transformationSpec),
                ) { Text("Timer") }
            }
            item {
                Text(
                    "Times go to your phone when you stop, or later if it isn't nearby.",
                    modifier = Modifier.transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(
                            TextDefaults.minimumTopListContentPadding,
                            TextDefaults.minimumBottomListContentPadding,
                        )
                        .graphicsLayer { with(transformationSpec) { applyContentTransformation(scrollProgress) } }
                        .fillMaxWidth(),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun Running(
    session: TimerSession,
    elapsedMs: () -> Long?,
    onFinish: () -> FinishTime?,
    onUndo: () -> Unit,
    onStop: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    var elapsed by remember { mutableLongStateOf(elapsedMs() ?: 0) }
    var confirmStop by remember { mutableStateOf(false) }
    LaunchedEffect(session.id) {
        while (true) {
            elapsed = elapsedMs() ?: 0
            delay(200)
        }
    }
    val last = session.times.lastOrNull()

    // The elapsed time takes the clock's place at the top
    ScreenScaffold(timeText = {}) {
        BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            // As big as the round screen allows between the clock and the controls
            val finishSize = maxHeight * 0.48f
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(formatElapsed(elapsed), fontSize = 30.sp, style = MaterialTheme.typography.numeralSmall, modifier = Modifier.testTag("elapsed"))
                // Squares up while pressed, so each finisher gets a visible as well as a felt response
                TextButton(
                    onClick = {
                        if (onFinish() != null) haptics.performHapticFeedback(HapticFeedbackType.Confirm)
                    },
                    modifier = Modifier.size(finishSize),
                    shapes = TextButtonDefaults.animatedShapes(),
                    colors = TextButtonDefaults.filledTextButtonColors(),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Finish", style = MaterialTheme.typography.titleMedium)
                        Text("P${session.times.size + 1}", style = MaterialTheme.typography.bodySmall)
                    }
                }
                // The pressed button grows into its neighbour; wide enough that the other keeps its label
                val undoInteraction = remember { MutableInteractionSource() }
                val stopInteraction = remember { MutableInteractionSource() }
                ButtonGroup(Modifier.width(finishSize * 1.8f)) {
                    CompactButton(
                        onClick = {
                            onUndo()
                            haptics.performHapticFeedback(HapticFeedbackType.Reject)
                        },
                        modifier = Modifier.animateWidth(undoInteraction),
                        enabled = last != null,
                        interactionSource = undoInteraction,
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        label = { Text("Undo", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                    )
                    CompactButton(
                        onClick = { confirmStop = true },
                        modifier = Modifier.animateWidth(stopInteraction),
                        interactionSource = stopInteraction,
                        colors = ButtonDefaults.filledTonalButtonColors(),
                        label = { Text("Stop", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                    )
                }
                Text(
                    last?.let { "P${it.position} ${formatElapsed(it.elapsedMs)}" } ?: "No finishers yet",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    AlertDialog(
        visible = confirmStop,
        onDismissRequest = { confirmStop = false },
        title = { Text("Stop the timer?") },
        text = { Text(if (session.times.size == 1) "1 finisher. The times go to your phone." else "${session.times.size} finishers. The times go to your phone.") },
        confirmButton = {
            AlertDialogDefaults.ConfirmButton(onClick = {
                confirmStop = false
                onStop()
            })
        },
        dismissButton = { AlertDialogDefaults.DismissButton(onClick = { confirmStop = false }) },
    )
}

/** Dark, without seconds or buttons: the screen updates about once a minute in ambient mode. */
@Composable
private fun Ambient(session: TimerSession, elapsedMs: Long) {
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("${elapsedMs / 60_000} min", color = Color.White, fontSize = 30.sp)
            Text(
                if (session.times.size == 1) "1 finisher" else "${session.times.size} finishers",
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium.copy(fontFeatureSettings = "tnum"),
            )
        }
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
private fun TimerRunningPreview() {
    MaterialTheme {
        TimerScreen(
            session = TimerSession("s", 0, listOf(FinishTime("e", 1, 1_022_000))),
            elapsedMs = { 1_043_000 },
            ambient = false,
            onStart = {},
            onFinish = { null },
            onUndo = {},
            onStop = {},
        )
    }
}
