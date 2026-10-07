package com.github.nols1000.bibless.wear

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ListHeaderDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwipeToReveal
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TextDefaults
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.tooling.preview.devices.WearDevices
import com.github.nols1000.bibless.Barcode

@Composable
fun BarcodeListScreen(
    barcodes: List<Barcode>,
    onOpen: (Barcode) -> Unit,
    onAdd: () -> Unit,
    onDelete: (Barcode) -> Unit,
    onMoveToTop: (Barcode) -> Unit,
    onSettings: () -> Unit,
    /** Opens the stopwatch for timekeepers; [timerRunning] says it is recording. */
    onTimer: () -> Unit = {},
    timerRunning: Boolean = false,
) {
    // Dragging is fiddly on a watch, so a long press offers to move a barcode to the top instead.
    var moving by remember { mutableStateOf<Barcode?>(null) }
    val listState = rememberTransformingLazyColumnState()
    val transformationSpec = rememberTransformationSpec()
    ScreenScaffold(
        scrollState = listState,
        // Adding is the screen's main action, so it sits on the edge below the list
        edgeButton = {
            EdgeButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = null)
                Text("Add")
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
                ) { Text("Barcodes") }
            }
            if (barcodes.isEmpty()) {
                item {
                    Text(
                        "Add your athlete ID here or on your phone.",
                        modifier = Modifier.transformedHeight(this, transformationSpec)
                            .minimumVerticalContentPadding(
                                TextDefaults.minimumTopListContentPadding,
                                TextDefaults.minimumBottomListContentPadding,
                            )
                            .graphicsLayer { with(transformationSpec) { applyContentTransformation(scrollProgress) } }
                            .fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            items(barcodes, key = { it.id }) { barcode ->
                SwipeToReveal(
                    primaryAction = {
                        PrimaryActionButton(
                            onClick = { onDelete(barcode) },
                            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            text = { Text("Delete") },
                        )
                    },
                    onSwipePrimaryAction = { onDelete(barcode) },
                    modifier = Modifier.transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                        .fillMaxWidth(),
                ) {
                    Button(
                        onClick = { onOpen(barcode) },
                        modifier = Modifier.fillMaxWidth(),
                        transformation = SurfaceTransformation(transformationSpec),
                        onLongClick = if (barcode != barcodes.first()) {
                            { moving = barcode }
                        } else {
                            null
                        },
                        onLongClickLabel = "Move to top",
                        label = { Text(barcode.name) },
                        secondaryLabel = { Text(barcode.athleteId) },
                    )
                }
            }
            item {
                FilledTonalButton(
                    onClick = onTimer,
                    modifier = Modifier.transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                        .fillMaxWidth(),
                    transformation = SurfaceTransformation(transformationSpec),
                    icon = { Icon(Icons.Filled.PlayArrow, contentDescription = null) },
                    label = { Text("Timer") },
                    secondaryLabel = if (timerRunning) {
                        { Text("Recording") }
                    } else {
                        null
                    },
                )
            }
            item {
                FilledTonalButton(
                    onClick = onSettings,
                    modifier = Modifier.transformedHeight(this, transformationSpec)
                        .minimumVerticalContentPadding(ButtonDefaults.minimumVerticalListContentPadding)
                        .fillMaxWidth(),
                    transformation = SurfaceTransformation(transformationSpec),
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                )
            }
        }
    }

    // Keeps the name while the dialog animates out.
    var shown by remember { mutableStateOf<Barcode?>(null) }
    moving?.let { shown = it }
    AlertDialog(
        visible = moving != null,
        onDismissRequest = { moving = null },
        title = { Text(shown?.name.orEmpty()) },
        edgeButton = {
            AlertDialogDefaults.EdgeButton(
                onClick = {
                    moving?.let(onMoveToTop)
                    moving = null
                },
            ) {
                Text("Move to top")
            }
        },
    )
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
private fun BarcodeListPreview() {
    MaterialTheme {
        BarcodeListScreen(
            barcodes = listOf(Barcode("1", "Me", "A1234567", 0)),
            onOpen = {},
            onAdd = {},
            onDelete = {},
            onMoveToTop = {},
            onSettings = {},
        )
    }
}
