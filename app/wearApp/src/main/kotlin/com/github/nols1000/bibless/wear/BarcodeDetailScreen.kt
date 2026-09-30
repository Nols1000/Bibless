package com.github.nols1000.bibless.wear

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.pager.VerticalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.foundation.rememberAmbientModeManager
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.VerticalPagerScaffold
import com.github.nols1000.bibless.Barcode
import com.github.nols1000.bibless.BarcodeImage
import com.github.nols1000.bibless.FullBrightness
import com.github.nols1000.bibless.barcode.BarcodeFormat
import kotlin.math.sqrt

/**
 * Shows one page per barcode in [barcodes], starting on [startId]; swiping or turning the crown
 * moves to the next one, so runners scanning for several people needn't go back to the list.
 * [onShown] reports the barcode paged to.
 */
@Composable
fun BarcodeDetailScreen(
    barcodes: List<Barcode>,
    startId: String?,
    format: BarcodeFormat,
    onShown: (Barcode) -> Unit,
) {
    // Follows the paging, so only deleting the barcode on screen ends up on the message below.
    var shownId by rememberSaveable { mutableStateOf(startId) }
    val start = barcodes.indexOfFirst { it.id == shownId }
    if (start < 0) {
        ScreenScaffold(timeText = {}) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("This barcode was deleted.", textAlign = TextAlign.Center)
            }
        }
        return
    }
    FullBrightness()
    // Keeps the code on screen when the watch dims instead of falling back to the watch face.
    // The ambient API comes with Wear OS 6; older watches keep their default behavior.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
        rememberAmbientModeManager()
    }
    // With a single barcode there's nothing to page to, so the screen stays as it was.
    if (barcodes.size == 1) {
        ScreenScaffold(timeText = {}) { BarcodePage(barcodes.single(), format) }
        return
    }
    val pagerState = rememberPagerState(initialPage = start) { barcodes.size }
    val currentBarcodes by rememberUpdatedState(barcodes)
    val currentOnShown by rememberUpdatedState(onShown)
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            currentBarcodes.getOrNull(page)?.let {
                shownId = it.id
                currentOnShown(it)
            }
        }
    }
    VerticalPagerScaffold(pagerState = pagerState) {
        VerticalPager(state = pagerState, key = { barcodes[it].id }) { page ->
            ScreenScaffold(timeText = {}) { BarcodePage(barcodes[page], format) }
        }
    }
}

/** Shows the code alone on a white screen, as large as fits, so scanners pick it up easily. */
@Composable
private fun BarcodePage(barcode: Barcode, format: BarcodeFormat) {
    val isRound = LocalConfiguration.current.isScreenRound
    BoxWithConstraints(
        // Stays on until the volunteer has scanned it; the timeout returns with the list.
        Modifier.fillMaxSize().background(Color.White).keepScreenOn(),
        contentAlignment = Alignment.Center,
    ) {
        // On round screens, keep the code inside the largest square that fits in the circle.
        // Square screens get a smaller margin, still tall enough for the name.
        val inset = minOf(maxWidth, maxHeight) * if (isRound) (1 - 1 / sqrt(2f)) / 2 else 0.12f
        val side = minOf(maxWidth, maxHeight) - inset * 2
        // The labels sit right against the code, above and below it, whatever its shape.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        ) {
            Label(barcode.name, side)
            BarcodeImage(text = barcode.athleteId, format = format, modifier = Modifier.width(side))
            Label(barcode.athleteId, side)
        }
    }
}

@Composable
private fun Label(text: String, width: Dp) {
    Text(
        text,
        modifier = Modifier.width(width).padding(horizontal = 8.dp),
        color = Color.Black,
        style = MaterialTheme.typography.labelMedium,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}
