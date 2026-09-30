package com.github.nols1000.bibless.wear

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.github.nols1000.bibless.BarcodeRepository
import com.github.nols1000.bibless.Device
import com.github.nols1000.bibless.OpenStartBarcode

@Composable
fun WearApp(repository: BarcodeRepository) {
    MaterialTheme {
        AppScaffold {
            val state by repository.state.collectAsStateWithLifecycle()
            val navController = rememberSwipeDismissableNavController()

            SwipeDismissableNavHost(navController, startDestination = "list") {
                composable("list") {
                    BarcodeListScreen(
                        barcodes = state.barcodes,
                        onOpen = { navController.navigate("detail/${it.id}") },
                        onAdd = { navController.navigate("add") },
                        onDelete = { repository.delete(it.id) },
                        onMoveToTop = { repository.moveToTop(it.id) },
                        onSettings = { navController.navigate("settings") },
                    )
                }
                composable("add") {
                    AddBarcodeScreen(
                        onSave = { name, id ->
                            repository.add(name, id)
                            navController.popBackStack()
                        },
                    )
                }
                composable("detail/{id}") { entry ->
                    val barcode = entry.arguments?.getString("id")?.let(repository::find)
                    BarcodeDetailScreen(barcode = barcode, format = state.format)
                }
                composable("settings") {
                    SettingsScreen(
                        format = state.format,
                        onFormatChange = { repository.setDefaultFormat(Device.WATCH, it) },
                        openOnLaunch = state.settings.openOnLaunch,
                        onOpenOnLaunchChange = repository::setOpenOnLaunch,
                    )
                }
            }
            OpenStartBarcode(navController, repository)
        }
    }
}
