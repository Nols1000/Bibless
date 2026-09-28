package com.github.nols1000.bibless.wear

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwipeToReveal
import androidx.wear.compose.material3.Text
import androidx.wear.tooling.preview.devices.WearDevices
import com.github.nols1000.bibless.Barcode

@Composable
fun BarcodeListScreen(
    barcodes: List<Barcode>,
    onOpen: (Barcode) -> Unit,
    onAdd: () -> Unit,
    onDelete: (Barcode) -> Unit,
    onSettings: () -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader { Text("Barcodes") }
            }
            if (barcodes.isEmpty()) {
                item {
                    Text(
                        "Add your parkrun ID here or on your phone.",
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
                            onClick = { onDelete(barcode) },
                            icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                            text = { Text("Delete") },
                        )
                    },
                    onSwipePrimaryAction = { onDelete(barcode) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Button(
                        onClick = { onOpen(barcode) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(barcode.name) },
                        secondaryLabel = { Text(barcode.athleteId) },
                    )
                }
            }
            item {
                FilledTonalButton(
                    onClick = onAdd,
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    label = { Text("Add") },
                )
            }
            item {
                FilledTonalButton(
                    onClick = onSettings,
                    modifier = Modifier.fillMaxWidth(),
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("Settings") },
                )
            }
        }
    }
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
            onSettings = {},
        )
    }
}
