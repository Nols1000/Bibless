package com.github.nols1000.bibless.wear

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.github.nols1000.bibless.Barcode
import com.github.nols1000.bibless.barcode.BarcodeFormat
import com.github.nols1000.bibless.label

/**
 * Lets the watch pick its own default format (the phone default is set on the phone) and the
 * barcode both apps open on launch.
 */
@Composable
fun SettingsScreen(
    format: BarcodeFormat,
    onFormatChange: (BarcodeFormat) -> Unit,
    barcodes: List<Barcode>,
    defaultBarcodeId: String?,
    onDefaultBarcodeChange: (String?) -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            item {
                ListHeader { Text("Default format") }
            }
            items(BarcodeFormat.entries) { option ->
                RadioButton(
                    selected = option == format,
                    onSelect = { onFormatChange(option) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(option.label) },
                )
            }
            item {
                ListHeader { Text("Open on launch") }
            }
            val defaultId = defaultBarcodeId?.takeIf { id -> barcodes.any { it.id == id } }
            items(listOf(null to "Barcode list") + barcodes.map { it.id to it.name }) { (id, label) ->
                RadioButton(
                    selected = id == defaultId,
                    onSelect = { onDefaultBarcodeChange(id) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(label) },
                )
            }
        }
    }
}
