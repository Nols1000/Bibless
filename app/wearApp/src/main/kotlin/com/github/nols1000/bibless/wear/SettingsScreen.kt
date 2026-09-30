package com.github.nols1000.bibless.wear

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.RadioButton
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.github.nols1000.bibless.LaunchScreen
import com.github.nols1000.bibless.barcode.BarcodeFormat
import com.github.nols1000.bibless.label

/**
 * Lets the watch pick what both apps open on launch and its own default format (the phone default
 * is set on the phone).
 */
@Composable
fun SettingsScreen(
    format: BarcodeFormat,
    onFormatChange: (BarcodeFormat) -> Unit,
    openOnLaunch: LaunchScreen,
    onOpenOnLaunchChange: (LaunchScreen) -> Unit,
) {
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
            // First, since skipping the list is what matters most on the wrist
            item {
                ListHeader { Text("Open on launch") }
            }
            items(LaunchScreen.entries) { screen ->
                RadioButton(
                    selected = screen == openOnLaunch,
                    onSelect = { onOpenOnLaunchChange(screen) },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(screen.label) },
                )
            }
            item {
                Text(
                    "Long-press a barcode in the list to move it to the top.",
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
        }
    }
}
