package com.github.nols1000.bibless.wear

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.navigation.currentBackStackEntryAsState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.dynamicColorScheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.github.nols1000.bibless.BarcodeRepository
import com.github.nols1000.bibless.Device
import com.github.nols1000.bibless.OpenStartBarcode
import com.github.nols1000.bibless.TimerRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Whether the watch is in ambient mode, and a count of the system's ambient updates, about one a minute. */
data class Ambient(val isAmbient: Boolean = false, val updates: Int = 0)

/**
 * The watch app. [openRequests] are barcodes to open from outside the app, see [OpenStartBarcode].
 * While [timer] runs, the app opens on it.
 */
@Composable
fun WearApp(
    repository: BarcodeRepository,
    timer: TimerRepository,
    openRequests: Flow<String> = emptyFlow(),
    ambient: Ambient = Ambient(),
) {
    val context = LocalContext.current
    // Colors from the watch face on Wear OS 6 and later, like the system apps; the defaults before
    val colorScheme = remember(context) { dynamicColorScheme(context) } ?: ColorScheme()
    MaterialTheme(colorScheme = colorScheme) {
        AppScaffold {
            val state by repository.state.collectAsStateWithLifecycle()
            val timerState by timer.state.collectAsStateWithLifecycle()
            val navController = rememberSwipeDismissableNavController()
            val entry by navController.currentBackStackEntryAsState()
            val onTimer = entry?.destination?.route == "timer"

            SwipeDismissableNavHost(navController, startDestination = "list") {
                composable("list") {
                    BarcodeListScreen(
                        barcodes = state.barcodes,
                        onOpen = { navController.navigate("detail/${it.id}") },
                        onAdd = { navController.navigate("add") },
                        onDelete = { repository.delete(it.id) },
                        onMoveToTop = { repository.moveToTop(it.id) },
                        onSettings = { navController.navigate("settings") },
                        onTimer = { navController.navigate("timer") },
                        timerRunning = timerState.session != null,
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
                    BarcodeDetailScreen(
                        barcodes = state.barcodes,
                        startId = entry.arguments?.getString("id"),
                        format = state.format,
                        onShown = { repository.shownBarcodeId = it.id },
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        format = state.format,
                        onFormatChange = { repository.setDefaultFormat(Device.WATCH, it) },
                        openOnLaunch = state.settings.openOnLaunch,
                        onOpenOnLaunchChange = repository::setOpenOnLaunch,
                    )
                }
                composable("timer") {
                    // Ambient updates recompose it, so the minutes move on
                    key(ambient.updates) {
                        TimerScreen(
                            session = timerState.session,
                            elapsedMs = timer::elapsedMs,
                            ambient = ambient.isAmbient,
                            onStart = timer::start,
                            onFinish = timer::tap,
                            onUndo = timer::undo,
                            onStop = timer::stop,
                        )
                    }
                }
            }
            val startRoute = remember { "timer".takeIf { timer.state.value.session != null } }
            OpenStartBarcode(navController, repository, openRequests, startRoute)

            // Elsewhere a dark screen: a bright barcode mustn't stay on in ambient mode
            if (ambient.isAmbient && !onTimer) {
                Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                    Text("Bibless", color = Color.White)
                }
            }
        }
    }
}
