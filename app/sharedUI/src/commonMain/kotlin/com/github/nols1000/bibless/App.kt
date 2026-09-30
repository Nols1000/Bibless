package com.github.nols1000.bibless

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController

@Composable
fun App(repository: BarcodeRepository) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()) {
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
                val barcode = entry.savedStateHandle.get<String>("id")?.let(repository::find)
                BarcodeDetailScreen(
                    barcode = barcode,
                    format = state.format,
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
        OpenStartBarcode(navController, repository)
    }
}
