package com.github.nols1000.bibless

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.foundation.text.KeyboardOptions

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeListScreen(
    barcodes: List<Barcode>,
    onOpen: (Barcode) -> Unit,
    onAdd: (name: String, athleteId: String) -> Unit,
    onDelete: (Barcode) -> Unit,
    onReorder: (List<Barcode>) -> Unit,
    onSettings: () -> Unit,
) {
    var adding by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Barcodes") },
                actions = {
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { adding = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Add barcode")
            }
        },
    ) { padding ->
        if (barcodes.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding).padding(32.dp), contentAlignment = Alignment.Center) {
                Text(
                    "No barcodes yet. Tap + to add your athlete ID.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            // Local copy while dragging; the repository only hears about the final order on drop.
            // Kept as one state object, since the drag gesture outlives recompositions.
            var order by remember { mutableStateOf(barcodes) }
            val latestBarcodes by rememberUpdatedState(barcodes)
            var draggedId by remember { mutableStateOf<String?>(null) }
            var dragOffset by remember { mutableFloatStateOf(0f) }
            val listState = rememberLazyListState()
            LaunchedEffect(barcodes) {
                if (draggedId == null) order = barcodes
            }

            fun move(from: Int, to: Int) {
                order = order.toMutableList().apply { add(to, removeAt(from)) }
            }

            /** Swaps the dragged row with the one its center is dragged over. */
            fun dragBy(delta: Float) {
                dragOffset += delta
                val items = listState.layoutInfo.visibleItemsInfo
                val dragged = items.firstOrNull { it.key == draggedId } ?: return
                val center = dragged.offset + dragged.size / 2 + dragOffset
                val target = items.firstOrNull {
                    it.key != draggedId && center.toInt() in it.offset until it.offset + it.size
                } ?: return
                move(dragged.index, target.index)
                // The row now sits where the target was, so only the rest of the drag stays an offset.
                dragOffset -= target.offset - dragged.offset
            }

            LazyColumn(state = listState, contentPadding = padding) {
                itemsIndexed(order, key = { _, barcode -> barcode.id }) { index, barcode ->
                    val dragging = barcode.id == draggedId
                    val dismissState = rememberSwipeToDismissBoxState()
                    SwipeToDismissBox(
                        state = dismissState,
                        enableDismissFromStartToEnd = false,
                        onDismiss = { onDelete(barcode) },
                        modifier = if (dragging) {
                            Modifier.zIndex(1f).graphicsLayer { translationY = dragOffset }
                        } else {
                            Modifier.animateItem()
                        },
                        backgroundContent = {
                            if (dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                                Box(
                                    Modifier.fillMaxSize().padding(horizontal = 24.dp),
                                    contentAlignment = Alignment.CenterEnd,
                                ) {
                                    Icon(
                                        Icons.Filled.Delete,
                                        contentDescription = "Delete",
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                        },
                    ) {
                        ListItem(
                            headlineContent = { Text(barcode.name) },
                            supportingContent = { Text(barcode.athleteId) },
                            trailingContent = if (order.size > 1) {
                                {
                                    Icon(
                                        Icons.Filled.Menu,
                                        contentDescription = "Reorder",
                                        modifier = Modifier.pointerInput(barcode.id) {
                                            detectDragGestures(
                                                onDragStart = {
                                                    draggedId = barcode.id
                                                    dragOffset = 0f
                                                },
                                                onDrag = { change, amount ->
                                                    change.consume()
                                                    dragBy(amount.y)
                                                },
                                                onDragEnd = {
                                                    draggedId = null
                                                    onReorder(order)
                                                },
                                                onDragCancel = {
                                                    draggedId = null
                                                    order = latestBarcodes
                                                },
                                            )
                                        },
                                    )
                                }
                            } else {
                                null
                            },
                            shadowElevation = if (dragging) 8.dp else 0.dp,
                            modifier = Modifier
                                .clickable { onOpen(barcode) }
                                .semantics {
                                    // Dragging needs a pointer; screen readers move rows one step at a time.
                                    customActions = listOfNotNull(
                                        CustomAccessibilityAction("Move up") {
                                            move(index, index - 1)
                                            onReorder(order)
                                            true
                                        }.takeIf { index > 0 },
                                        CustomAccessibilityAction("Move down") {
                                            move(index, index + 1)
                                            onReorder(order)
                                            true
                                        }.takeIf { index < order.lastIndex },
                                    )
                                },
                        )
                    }
                }
            }
        }
    }

    if (adding) {
        AddBarcodeDialog(
            onDismiss = { adding = false },
            onSave = { name, id ->
                onAdd(name, id)
                adding = false
            },
        )
    }
}

@Composable
private fun AddBarcodeDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, athleteId: String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var athleteId by remember { mutableStateOf("") }
    val normalized = normalizeAthleteId(athleteId)
    val showError = athleteId.isNotBlank() && normalized == null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add barcode") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Next,
                    ),
                )
                OutlinedTextField(
                    value = athleteId,
                    onValueChange = { athleteId = it },
                    label = { Text("Athlete ID") },
                    placeholder = { Text("A1234567") },
                    singleLine = true,
                    isError = showError,
                    supportingText = if (showError) {
                        { Text("Use the format A1234567") }
                    } else null,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Characters,
                        imeAction = ImeAction.Done,
                    ),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, athleteId) }, enabled = normalized != null) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
