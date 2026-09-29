package com.github.nols1000.bibless

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState

/**
 * Records which barcode's detail screen is open, and on launch reopens it on top of the list, so
 * the code is still there if the app gets closed in the finish queue. Expects the routes `list`
 * and `detail/{id}`.
 */
@Composable
fun ReopenShownBarcode(navController: NavHostController, repository: BarcodeRepository) {
    // Read before the effect below overwrites it with the start destination.
    val reopenId = remember { repository.shownBarcodeId }
    LaunchedEffect(Unit) {
        // After a configuration change the restored back stack already holds the detail screen.
        if (reopenId != null && navController.currentDestination?.route == "list") {
            navController.navigate("detail/$reopenId")
        }
    }
    val entry by navController.currentBackStackEntryAsState()
    LaunchedEffect(entry) {
        val current = entry ?: return@LaunchedEffect
        repository.shownBarcodeId =
            if (current.destination.route == "detail/{id}") current.savedStateHandle.get<String>("id") else null
    }
}
