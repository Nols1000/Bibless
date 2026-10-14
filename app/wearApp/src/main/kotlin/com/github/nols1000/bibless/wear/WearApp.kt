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
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.MotionScheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.dynamicColorScheme
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.currentBackStackEntryAsState
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.github.nols1000.bibless.BarcodeRepository
import com.github.nols1000.bibless.BiblessColors
import com.github.nols1000.bibless.Device
import com.github.nols1000.bibless.OpenStartBarcode
import com.github.nols1000.bibless.SampleColors
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
    // The watch face's colours on Wear OS 6 and later, like the system apps; the Bibless palette before
    val colorScheme = when {
        SampleColors.replaceDeviceColors -> SampleWearColors
        else -> remember(context) { dynamicColorScheme(context) } ?: BiblessWearColors
    }
    MaterialTheme(colorScheme = colorScheme, motionScheme = MotionScheme.standard()) {
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

/** The Bibless palette on the watch's black canvas: Volt actions with black labels, Carbon surfaces. */
private val BiblessWearColors = ColorScheme().copy(
    primary = BiblessColors.Volt,
    primaryDim = Color(0xFFAACC00),
    primaryContainer = Color(0xFF2E3800),
    onPrimary = BiblessColors.Black,
    onPrimaryContainer = Color(0xFFE6FF80),
    secondary = Color(0xFFC8CCD2),
    secondaryDim = Color(0xFF9AA0A8),
    secondaryContainer = Color(0xFF23262B),
    onSecondary = BiblessColors.Black,
    onSecondaryContainer = BiblessColors.SoftWhite,
    tertiary = Color(0xFFC8CCD2),
    tertiaryDim = Color(0xFF9AA0A8),
    tertiaryContainer = Color(0xFF23262B),
    onTertiary = BiblessColors.Black,
    onTertiaryContainer = BiblessColors.SoftWhite,
    surfaceContainerLow = Color(0xFF0B0C0E),
    surfaceContainer = BiblessColors.Carbon,
    surfaceContainerHigh = Color(0xFF23262B),
    onSurface = BiblessColors.SoftWhite,
    onSurfaceVariant = Color(0xFFA8ADB5),
    outline = Color(0xFF6B7078),
    outlineVariant = Color(0xFF2E3136),
    background = BiblessColors.Black,
    onBackground = BiblessColors.SoftWhite,
)

/**
 * The colours of a watch with a yellow watch face, as Wear OS derives them (Material's tonal spot
 * scheme from #FFCC00, as in [SampleColors]). Screenshots and previews show the app in them.
 */
internal val SampleWearColors = ColorScheme().copy(
    primary = Color(0xFFFFE08B),
    primaryDim = Color(0xFFE3C46D),
    primaryContainer = Color(0xFF584400),
    onPrimary = Color(0xFF241A00),
    onPrimaryContainer = Color(0xFFFFE08B),
    secondary = Color(0xFFF2E1BB),
    secondaryDim = Color(0xFFD5C5A1),
    secondaryContainer = Color(0xFF50462A),
    onSecondary = Color(0xFF231B04),
    onSecondaryContainer = Color(0xFFF2E1BB),
    tertiary = Color(0xFFC8ECC9),
    tertiaryDim = Color(0xFFADCFAE),
    tertiaryContainer = Color(0xFF2F4D34),
    onTertiary = Color(0xFF03210C),
    onTertiaryContainer = Color(0xFFC8ECC9),
    surfaceContainerLow = Color(0xFF1F1B13),
    surfaceContainer = Color(0xFF231F17),
    surfaceContainerHigh = Color(0xFF2D2A21),
    onSurface = Color(0xFFEAE1D4),
    onSurfaceVariant = Color(0xFFCFC6B4),
    outline = Color(0xFF989080),
    outlineVariant = Color(0xFF4C4639),
)
