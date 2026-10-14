package com.github.nols1000.bibless.wear

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.CurvedDirection
import androidx.wear.compose.foundation.CurvedLayout
import androidx.wear.compose.foundation.CurvedModifier
import androidx.wear.compose.foundation.padding
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.VerticalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.foundation.rememberAmbientModeManager
import androidx.wear.compose.material3.HorizontalPageIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.VerticalPagerScaffold
import androidx.wear.compose.material3.curvedText
import com.github.nols1000.bibless.Barcode
import com.github.nols1000.bibless.BarcodeImage
import com.github.nols1000.bibless.FullBrightness
import com.github.nols1000.bibless.SyncFormat
import com.github.nols1000.bibless.barcode.BarcodeFormat
import kotlin.math.sqrt

/**
 * Shows one page per barcode in [barcodes], starting on [startId]; swiping up or turning the crown
 * moves to the next one, so runners scanning for several people needn't go back to the list.
 * Swiping sideways switches between the formats, starting on the default [format], for when the
 * scanner reads only the other one. [onShown] reports the barcode paged to.
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
    // Kept while paging through the barcodes, but not saved as the default; changing the default
    // switches it, as before.
    var shownFormat by rememberSaveable(format) { mutableStateOf(format) }
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
        ScreenScaffold(timeText = {}) { BarcodePage(barcodes.single(), shownFormat) { shownFormat = it } }
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
            ScreenScaffold(timeText = {}) { BarcodePage(barcodes[page], shownFormat) { shownFormat = it } }
        }
    }
}

/** Pages sideways through the formats of [barcode], showing [format]. */
@Composable
private fun BarcodePage(barcode: Barcode, format: BarcodeFormat, onFormatChange: (BarcodeFormat) -> Unit) {
    val formatState = rememberPagerState(initialPage = format.ordinal) { BarcodeFormat.entries.size }
    SyncFormat(format, onFormatChange, currentPage = { formatState.currentPage }, scrollToPage = { formatState.scrollToPage(it) })
    // Stays on until the volunteer has scanned it; the timeout returns with the list.
    Box(Modifier.fillMaxSize().background(Color.White).keepScreenOn()) {
        // The crown stays with the barcodes; swiping right on the first format still goes back.
        HorizontalPager(formatState, rotaryScrollableBehavior = null) { page ->
            CodePage(barcode, BarcodeFormat.entries[page])
        }
        // Styled like the barcodes' indicator by the crown.
        HorizontalPageIndicator(formatState, Modifier.align(Alignment.BottomCenter))
    }
}

/** Shows the code alone on a white screen, as large as fits, so scanners pick it up easily. */
@Composable
private fun CodePage(barcode: Barcode, format: BarcodeFormat) {
    val isRound = LocalConfiguration.current.isScreenRound
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // On round screens, keep the code inside the largest square that fits in the circle.
        // Square screens get a smaller margin, still tall enough for the name.
        val inset = minOf(maxWidth, maxHeight) * if (isRound) (1 - 1 / sqrt(2f)) / 2 else 0.12f
        val side = minOf(maxWidth, maxHeight) - inset * 2
        if (isRound) {
            // The labels follow the edge, like the time text, where a straight line as wide as the
            // code would cut long names short. The code's quiet zone keeps its modules clear of them.
            BarcodeImage(text = barcode.athleteId, format = format, modifier = Modifier.width(side))
            CurvedLabel(barcode.name, Alignment.TopCenter, inset)
            CurvedLabel(barcode.athleteId, Alignment.BottomCenter, inset)
            return@BoxWithConstraints
        }
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

/** [text] along the top or bottom edge of a round screen, as [alignment] says, in the [inset] around the code. */
@Composable
private fun BoxScope.CurvedLabel(text: String, alignment: Alignment, inset: Dp) {
    val top = alignment == Alignment.TopCenter
    val style = MaterialTheme.typography.arcMedium
    CurvedLayout(
        Modifier.fillMaxSize(),
        anchor = if (top) 270f else 90f,
        // Bottom text runs the other way round, so it reads left to right too.
        angularDirection = if (top) CurvedDirection.Angular.Clockwise else CurvedDirection.Angular.CounterClockwise,
    ) {
        curvedText(
            text,
            // The bottom one sits further in, clear of the formats' page indicator along the edge.
            modifier = CurvedModifier.padding(
                outer = if (top) 4.dp else 4.dp + PAGE_INDICATOR_ROOM,
                inner = 4.dp,
                before = 0.dp,
                after = 0.dp,
            ),
            maxSweepAngle = MAX_LABEL_SWEEP,
            color = Color.Black,
            style = style,
            overflow = TextOverflow.Ellipsis,
        )
    }
    // Curved text isn't read out, so the band it sits in carries it for TalkBack and tests. A layout
    // as large as the screen can't: Compose hides nodes that a later one covers, as the other label would.
    Box(Modifier.align(alignment).fillMaxWidth().height(inset).semantics { this.text = AnnotatedString(text) })
}

/** Keeps the top and bottom labels apart, with room for names about twice as long as before. */
private const val MAX_LABEL_SWEEP = 140f

/** The height of the formats' page indicator at the bottom edge, with a little air. */
private val PAGE_INDICATOR_ROOM = 12.dp

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
