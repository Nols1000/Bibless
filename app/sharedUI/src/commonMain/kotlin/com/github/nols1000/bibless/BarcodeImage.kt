package com.github.nols1000.bibless

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.github.nols1000.bibless.barcode.BarcodeFormat
import com.github.nols1000.bibless.barcode.layout
import kotlin.math.floor

/**
 * Draws [text] as a scannable code: black modules on white with a quiet zone, regardless of theme.
 */
@Composable
fun BarcodeImage(text: String, format: BarcodeFormat, modifier: Modifier = Modifier) {
    val layout = remember(text, format) { format.layout(text) }
    val matrix = layout.matrix
    val isLinear = layout.isLinear
    val quiet = layout.quiet
    val columns = layout.columns
    val rows = layout.rows

    Canvas(
        modifier
            .aspectRatio(layout.aspectRatio)
            .background(Color.White)
            .semantics { contentDescription = "${format.label} for $text" },
    ) {
        // Whole-pixel modules keep bar edges crisp, which matters on small watch screens.
        val module = floor(size.width / columns).coerceAtLeast(1f)
        val left = (size.width - module * columns) / 2 + quiet * module
        for (x in 0 until matrix.width) {
            if (isLinear) {
                if (matrix[x, 0]) {
                    drawRect(
                        Color.Black,
                        topLeft = Offset(left + x * module, quiet * module),
                        size = Size(module, size.height - 2 * quiet * module),
                    )
                }
            } else {
                val top = (size.height - module * rows) / 2 + quiet * module
                for (y in 0 until matrix.height) {
                    if (matrix[x, y]) {
                        drawRect(
                            Color.Black,
                            topLeft = Offset(left + x * module, top + y * module),
                            size = Size(module, module),
                        )
                    }
                }
            }
        }
    }
}
