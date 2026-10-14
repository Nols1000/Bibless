package com.github.nols1000.bibless

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import com.github.nols1000.bibless.barcode.BarcodeFormat

/**
 * Shows one page per barcode in [barcodes], starting on [startId]; swiping up or down moves to
 * the next one, so runners scanning for several people needn't go back to the list. Swiping
 * sideways switches between the formats, starting on the default [format], for when the scanner
 * reads only the other one. [onShown] reports the barcode paged to.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeDetailScreen(
    barcodes: List<Barcode>,
    startId: String?,
    format: BarcodeFormat,
    onShown: (Barcode) -> Unit,
    onBack: () -> Unit,
) {
    // Follows the paging, so only deleting the barcode on screen ends up on the message below.
    var shownId by rememberSaveable { mutableStateOf(startId) }
    // Kept while paging through the barcodes, but not saved as the default; changing the default
    // switches it, as before.
    var shownFormat by rememberSaveable(format) { mutableStateOf(format) }
    val start = barcodes.indexOfFirst { it.id == shownId }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(barcodes.getOrNull(start)?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (start < 0) {
            Text("This barcode was deleted.", Modifier.padding(padding).padding(24.dp))
            return@Scaffold
        }
        FullBrightness()
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
        VerticalPager(
            state = pagerState,
            // Stays on until the volunteer has scanned it; the timeout returns with the list.
            modifier = Modifier.fillMaxSize().padding(padding).keepScreenOn(),
            key = { barcodes[it].id },
            // With a single barcode there's nothing to page to, so the screen stays as it was.
            userScrollEnabled = barcodes.size > 1,
        ) { page ->
            BarcodePage(barcodes[page], shownFormat, onFormatChange = { shownFormat = it })
        }
    }
}

@Composable
private fun BarcodePage(barcode: Barcode, format: BarcodeFormat, onFormatChange: (BarcodeFormat) -> Unit) {
    val formatState = rememberFormatPagerState(format, onFormatChange)
    Column(
        modifier = Modifier.fillMaxSize().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        // Fills the page, so the dots below stay put while the codes' heights differ.
        HorizontalPager(formatState, Modifier.weight(1f), verticalAlignment = Alignment.Top) { page ->
            // Black on white regardless of theme: scanners struggle with a code framed by a dark screen.
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color.White,
                contentColor = Color.Black,
            ) {
                Column(
                    modifier = Modifier.padding(bottom = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    BarcodeImage(
                        text = barcode.athleteId,
                        format = BarcodeFormat.entries[page],
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(barcode.athleteId, style = MaterialTheme.typography.headlineMedium)
                }
            }
        }
        PageDots(count = BarcodeFormat.entries.size, current = formatState.currentPage)
    }
}

/** One dot per format, the one on screen highlighted. */
@Composable
private fun PageDots(count: Int, current: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { index ->
            val color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (index == current) 1f else 0.3f)
            Box(Modifier.size(8.dp).background(color, CircleShape))
        }
    }
}
