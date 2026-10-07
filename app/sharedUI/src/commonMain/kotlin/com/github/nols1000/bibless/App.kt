package com.github.nols1000.bibless

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow

/**
 * The phone app. [openRequests] are barcodes to open from outside the app, see [OpenStartBarcode].
 * While [timer] or [scanner] runs, the app starts on it.
 */
@Composable
fun App(
    repository: BarcodeRepository,
    timer: TimerRepository,
    scanner: ScannerRepository,
    device: RaceDevice,
    openRequests: Flow<String> = emptyFlow(),
    shareFiles: ((List<ExportFile>) -> Unit)? = null,
    /** On the phone, the timer sessions its watch sent; they are shared like the phone's own. */
    received: ReceivedSessions? = null,
) {
    BiblessTheme {
        val state by repository.state.collectAsStateWithLifecycle()
        val timerState by timer.state.collectAsStateWithLifecycle()
        val scannerState by scanner.state.collectAsStateWithLifecycle()
        val joinedRun by device.joinedRun.collectAsStateWithLifecycle()
        val exporter = remember(timer, scanner, received) { SessionExporter(timer, scanner, received) }
        val receivedCount by remember(received) { received?.received ?: MutableStateFlow(0) }.collectAsStateWithLifecycle()
        val share = shareFiles ?: rememberShareFiles()
        val deviceName = rememberDeviceName()
        val scope = rememberCoroutineScope()
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
                    onTimer = { navController.navigate("timer") },
                    onScanner = { navController.navigate("scanner") },
                )
            }
            composable("timer") {
                TimerScreen(
                    session = timerState.session,
                    joinedRun = joinedRun,
                    onJoin = { navController.navigate("join") },
                    onLeave = device::leave,
                    onSessions = { navController.navigate("sessions") },
                    elapsedMs = timer::elapsedMs,
                    onStart = timer::start,
                    onFinish = { timer.tap() },
                    onUndo = timer::undo,
                    onStop = timer::stop,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("scanner") {
                ScannerScreen(
                    session = scannerState.session,
                    joinedRun = joinedRun,
                    onJoin = { navController.navigate("join") },
                    onLeave = device::leave,
                    onSessions = { navController.navigate("sessions") },
                    onStart = scanner::start,
                    onScan = scanner::scan,
                    onEnter = scanner::enter,
                    onUnknown = scanner::addUnknown,
                    onUndo = scanner::undo,
                    onStop = scanner::stop,
                    onBack = { navController.popBackStack() },
                )
            }
            composable("sessions") {
                val sessions = remember(timerState, scannerState, receivedCount) { exporter.sessions() }
                SessionsScreen(
                    sessions = sessions,
                    joinedRun = joinedRun,
                    onShare = { session ->
                        scope.launch {
                            exporter.export(session.kind, session.id, deviceName, utcOffsetMinutes(session.startedAt))?.let(share)
                        }
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable("join") {
                JoinRunScreen(
                    onCode = device::join,
                    onJoined = { navController.popBackStack() },
                    onBack = { navController.popBackStack() },
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
        OpenStartBarcode(navController, repository, openRequests, startRoute = when {
            timerState.session != null -> "timer"
            scannerState.session != null -> "scanner"
            else -> null
        })
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
        BarcodeDetailScreen(barcode = barcode, format = state.format, onBack = onClose)
    }
}

@Composable
private fun BiblessTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) BiblessDarkColors else BiblessLightColors, content = content)
}
