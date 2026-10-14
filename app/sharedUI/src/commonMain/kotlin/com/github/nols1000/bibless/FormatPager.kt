package com.github.nols1000.bibless

import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import com.github.nols1000.bibless.barcode.BarcodeFormat

/** A pager over the code formats, in [BarcodeFormat.entries] order, kept in step by [SyncFormat]. */
@Composable
fun rememberFormatPagerState(format: BarcodeFormat, onFormatChange: (BarcodeFormat) -> Unit): PagerState {
    val state = rememberPagerState(initialPage = format.ordinal) { BarcodeFormat.entries.size }
    SyncFormat(format, onFormatChange, currentPage = { state.currentPage }, scrollToPage = { state.scrollToPage(it) })
    return state
}

/**
 * Keeps a pager over [BarcodeFormat.entries] showing [format]. Swiping past the middle of another
 * page calls [onFormatChange]; pagers that share one format follow it, so the next athlete comes up
 * in the format the scanner just read. Follows the current page rather than the settled one, so a
 * swipe to the next athlete before the pager has settled keeps the format too. Takes the pager as
 * functions, since Wear has its own pager state.
 */
@Composable
fun SyncFormat(
    format: BarcodeFormat,
    onFormatChange: (BarcodeFormat) -> Unit,
    currentPage: () -> Int,
    scrollToPage: suspend (Int) -> Unit,
) {
    val currentOnFormatChange by rememberUpdatedState(onFormatChange)
    LaunchedEffect(Unit) {
        snapshotFlow(currentPage).collect { currentOnFormatChange(BarcodeFormat.entries[it]) }
    }
    LaunchedEffect(format) {
        // Never the pager being swiped: its current page is the format it just set.
        if (currentPage() != format.ordinal) scrollToPage(format.ordinal)
    }
}
