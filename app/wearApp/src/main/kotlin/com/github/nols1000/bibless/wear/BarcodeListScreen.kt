package com.github.nols1000.bibless.wear

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AlertDialogDefaults
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonGroup
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwipeToReveal
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.tooling.preview.devices.WearDevices
import com.github.nols1000.bibless.Barcode
import kotlinx.coroutines.delay

@Composable
fun BarcodeListScreen(
    barcodes: List<Barcode>,
    onOpen: (Barcode) -> Unit,
    onAdd: () -> Unit,
    onDelete: (Barcode) -> Unit,
    onMoveToTop: (Barcode) -> Unit,
    onSettings: () -> Unit,
) {
    // Dragging is fiddly on a watch, so a long press offers to move a barcode to the top instead.
    var moving by remember { mutableStateOf<Barcode?>(null) }

    // A deleted barcode keeps its row, with an undo button, for a moment before it's gone for good
    // on both devices. Deleting another one or leaving the list ends that moment early.
    var pendingDelete by remember { mutableStateOf<Barcode?>(null) }
    val currentOnDelete by rememberUpdatedState(onDelete)
    val delete = { barcode: Barcode ->
        pendingDelete?.takeIf { it.id != barcode.id }?.let(currentOnDelete)
        pendingDelete = barcode
    }
    LaunchedEffect(pendingDelete) {
        val barcode = pendingDelete ?: return@LaunchedEffect
        delay(UNDO_MILLIS)
        pendingDelete = null
        currentOnDelete(barcode)
    }
    DisposableEffect(Unit) {
        onDispose { pendingDelete?.let(currentOnDelete) }
    }

    val listState = rememberTransformingLazyColumnState()
    // Items shrink and morph at the top and bottom edges, so the rows fit the round screen.
    val transformationSpec = rememberTransformationSpec()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader(
                    modifier = Modifier.transformedHeight(this, transformationSpec),
                    transformation = SurfaceTransformation(transformationSpec),
                ) { Text("Barcodes") }
            }
            if (barcodes.isEmpty()) {
                item {
                    Text(
                        "Add your athlete ID here or on your phone.",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            items(barcodes, key = { it.id }) { barcode ->
                SwipeToReveal(
                    primaryAction = {
                        PrimaryActionButton(
                            onClick = { delete(barcode) },
                            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            text = { Text("Delete") },
                        )
                    },
                    onSwipePrimaryAction = { delete(barcode) },
                    undoPrimaryAction = {
                        UndoActionButton(
                            onClick = { pendingDelete = null },
                            text = { Text("Undo") },
                        )
                    },
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
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
                ButtonGroup(
                    modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
                ) {
                    FilledTonalButton(
                        onClick = onAdd,
                        modifier = Modifier.weight(1f),
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        label = { Text("Add") },
                    )
                    FilledTonalButton(
                        onClick = onSettings,
                        modifier = Modifier.weight(1f),
                        icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                        label = { Text("Settings") },
                    )
                }
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

/** How long the undo button stays after a delete. */
internal const val UNDO_MILLIS = 5_000L

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
