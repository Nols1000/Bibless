package com.github.nols1000.bibless

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.github.nols1000.bibless.barcode.BarcodeFormat

val BarcodeFormat.label: String
    get() = if (this == BarcodeFormat.QR) "QR code" else "Barcode"

val LaunchScreen.label: String
    get() = if (this == LaunchScreen.FIRST_BARCODE) "First barcode" else "Barcode list"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settings: Settings,
    barcodeCount: Int,
    onDefaultFormatChange: (Device, BarcodeFormat) -> Unit,
    onOpenOnLaunchChange: (LaunchScreen) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(contentPadding = padding) {
            Device.entries.forEach { device ->
                item(key = device.name) {
                    Text(
                        if (device == Device.PHONE) "Phone" else "Watch",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
                    )
                }
                item(key = "${device.name}-options") {
                    Column(Modifier.selectableGroup()) {
                        BarcodeFormat.entries.forEach { format ->
                            val selected = settings.formatFor(device) == format
                            ListItem(
                                headlineContent = { Text(format.label) },
                                leadingContent = { RadioButton(selected = selected, onClick = null) },
                                modifier = Modifier.selectable(
                                    selected = selected,
                                    role = Role.RadioButton,
                                    onClick = { onDefaultFormatChange(device, format) },
                                ),
                            )
                        }
                    }
                }
            }
            item(key = "start") {
                Text(
                    "Open on launch",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
                )
            }
            item(key = "start-options") {
                Column(Modifier.selectableGroup()) {
                    LaunchScreen.entries.forEach { screen ->
                        val selected = settings.openOnLaunch == screen
                        ListItem(
                            headlineContent = { Text(screen.label) },
                            supportingContent = when {
                                screen == LaunchScreen.FIRST_BARCODE -> {
                                    { Text("Drag barcodes in the list to choose which comes first") }
                                }
                                barcodeCount == 1 -> {
                                    { Text("Your only barcode opens either way") }
                                }
                                else -> null
                            },
                            leadingContent = { RadioButton(selected = selected, onClick = null) },
                            modifier = Modifier.selectable(
                                selected = selected,
                                role = Role.RadioButton,
                                onClick = { onOpenOnLaunchChange(screen) },
                            ),
                        )
                    }
                }
            }
        }
    }
}
