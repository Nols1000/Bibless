package com.github.nols1000.bibless

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

/**
 * Opens [BarcodeRepository.startBarcodeId] on top of the list when the app starts, so runners
 * reach their code without searching for it, and records which barcode's detail screen is open so
 * the next start can return to it. Expects the routes `list` and `detail/{id}`.
 */
@Composable
fun OpenStartBarcode(navController: NavHostController, repository: BarcodeRepository) {
    // Saved across configuration changes, where the restored back stack is already right.
    var started by rememberSaveable { mutableStateOf(false) }
    if (!started) {
        // Read before the effect below records the list as the open screen.
        val startId = repository.startBarcodeId()
        LaunchedEffect(Unit) {
            startId?.let { navController.navigate("detail/$it") }
            started = true
        }
    }
    val entry by navController.currentBackStackEntryAsState()
    LaunchedEffect(entry) {
        val current = entry ?: return@LaunchedEffect
        repository.shownBarcodeId =
            if (current.destination.route == "detail/{id}") current.savedStateHandle.get<String>("id") else null
    }
}
