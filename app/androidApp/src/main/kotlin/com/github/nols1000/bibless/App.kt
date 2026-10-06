package com.github.nols1000.bibless

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** The phone app. [openRequests] are barcodes to open from outside the app, see [OpenStartBarcode]. */
@Composable
fun App(repository: BarcodeRepository, openRequests: Flow<String> = emptyFlow()) {
    BiblessTheme {
        val state by repository.state.collectAsStateWithLifecycle()
        val navController = rememberNavController()

        NavHost(navController, startDestination = "list") {
            composable("list") {
                BarcodeListScreen(
                    barcodes = state.barcodes,
                    onOpen = { navController.navigate("detail/${it.id}") },
                    onAdd = repository::add,
                    onDelete = { repository.delete(it.id) },
                    onReorder = { order -> repository.setOrder(order.map { it.id }) },
                    onSettings = { navController.navigate("settings") },
                )
            }
            composable("detail/{id}") { entry ->
                BarcodeDetailScreen(
                    barcodes = state.barcodes,
                    startId = entry.savedStateHandle.get<String>("id"),
                    format = state.format,
                    onShown = { repository.shownBarcodeId = it.id },
                    onBack = { navController.popBackStack() },
                )
            }
            composable("settings") {
                SettingsScreen(
                    settings = state.settings,
                    barcodeCount = state.barcodes.size,
                    onDefaultFormatChange = repository::setDefaultFormat,
                    onOpenOnLaunchChange = repository::setOpenOnLaunch,
                    onBack = { navController.popBackStack() },
                )
            }
        }
        OpenStartBarcode(navController, repository, openRequests)
    }
}

/**
 * Only the first barcode, for showing on the lock screen: no way to the list, the settings or
 * editing. Calls [onClose] on Back, and when there is no barcode to show.
 */
@Composable
fun FirstBarcodeApp(repository: BarcodeRepository, onClose: () -> Unit) {
    BiblessTheme {
        val state by repository.state.collectAsStateWithLifecycle()
        val barcode = state.barcodes.firstOrNull()
        if (barcode == null) {
            LaunchedEffect(Unit) { onClose() }
            return@BiblessTheme
        }
        // Just this one: the other barcodes stay behind the lock, but the other format is a swipe away.
        // Starts over when another barcode comes first, which would otherwise read as deleted.
        key(barcode.id) {
            BarcodeDetailScreen(
                barcodes = listOf(barcode),
                startId = barcode.id,
                format = state.format,
                onShown = {},
                onBack = onClose,
            )
        }
    }
}

@Composable
private fun BiblessTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(), content = content)
}
