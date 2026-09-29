package com.github.nols1000.bibless.wear

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.foundation.rememberAmbientModeManager
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.github.nols1000.bibless.Barcode
import com.github.nols1000.bibless.BarcodeImage
import com.github.nols1000.bibless.FullBrightness
import com.github.nols1000.bibless.barcode.BarcodeFormat
import kotlin.math.sqrt

/** Shows the code alone on a white screen, as large as fits, so scanners pick it up easily. */
@Composable
fun BarcodeDetailScreen(barcode: Barcode?, format: BarcodeFormat) {
    ScreenScaffold(timeText = {}) {
        if (barcode == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("This barcode was deleted.", textAlign = TextAlign.Center)
            }
            return@ScreenScaffold
        }
        FullBrightness()
        // Keeps the code on screen when the watch dims instead of falling back to the watch face.
        // The ambient API comes with Wear OS 6; older watches keep their default behavior.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA) {
            rememberAmbientModeManager()
        }
        val isRound = LocalConfiguration.current.isScreenRound
        BoxWithConstraints(
            // Stays on until the volunteer has scanned it; the timeout returns with the list.
            Modifier.fillMaxSize().background(Color.White).keepScreenOn(),
            contentAlignment = Alignment.Center,
        ) {
            // On round screens, keep the code inside the largest square that fits in the circle.
            // Square screens get a smaller margin, still tall enough for the name.
            val inset = minOf(maxWidth, maxHeight) * if (isRound) (1 - 1 / sqrt(2f)) / 2 else 0.12f
            BarcodeImage(
                text = barcode.athleteId,
                format = format,
                modifier = Modifier.padding(inset),
            )
            // Labels fill the margins above and below the code, so the code itself stays centered.
            listOf(Alignment.TopCenter to barcode.name, Alignment.BottomCenter to barcode.athleteId)
                .forEach { (alignment, label) ->
                    Box(
                        Modifier.align(alignment).fillMaxWidth().height(inset).padding(horizontal = inset),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            color = Color.Black,
                            style = MaterialTheme.typography.labelMedium,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
        }
    }
}
