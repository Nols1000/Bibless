package com.github.nols1000.bibless

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/**
 * Opens [BarcodeRepository.startBarcodeId] on top of the list when the app starts, so runners
 * reach their code without searching for it, and records which barcode's detail screen is open so
 * the next start can return to it. Expects the routes `list` and `detail/{id}`.
 *
 * Each ID from [openRequests] (a tile, widget or shortcut tapped while the app runs) opens that
 * barcode on top of the list, closing whatever was open. To start on one, set
 * [BarcodeRepository.shownBarcodeId] before the first composition instead.
 */
@Composable
fun OpenStartBarcode(
    navController: NavHostController,
    repository: BarcodeRepository,
    openRequests: Flow<String> = emptyFlow(),
) {
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
    LaunchedEffect(openRequests) {
        openRequests.collect { id ->
            if (repository.find(id) == null) return@collect
            // A new entry even when this barcode is already open: a pager may have moved on from it.
            navController.navigate("detail/$id") { popUpTo("list") }
            // Also when the entry looks the same as before, so the effect below doesn't run again.
            repository.shownBarcodeId = id
        }
    }
    val entry by navController.currentBackStackEntryAsState()
    LaunchedEffect(entry) {
        val current = entry ?: return@LaunchedEffect
        repository.shownBarcodeId =
            if (current.destination.route == "detail/{id}") current.savedStateHandle.get<String>("id") else null
    }
}
