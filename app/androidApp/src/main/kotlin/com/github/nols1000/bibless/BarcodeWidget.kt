package com.github.nols1000.bibless

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.github.nols1000.bibless.barcode.BarcodeFormat

/**
 * A home-screen widget with the first barcode, scannable without opening the app. Tapping it opens
 * that barcode at full brightness. [PhoneApplication] redraws it when the list changes.
 */
class BarcodeWidget : GlanceAppWidget() {
    // The code is drawn to fit the widget's actual size, pixel for pixel.
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val repository = Bibless.repository(context)
        provideContent {
            val state = repository.state.value
            BarcodeWidgetContent(state.barcodes.firstOrNull(), state.format)
        }
    }
}

class BarcodeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget = BarcodeWidget()
}

/** The widget's content for [barcode] in [format]; asks for a barcode when there is none. */
@Composable
fun BarcodeWidgetContent(barcode: Barcode?, format: BarcodeFormat) {
    val context = LocalContext.current
    val size = LocalSize.current
    Column(
        GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(16.dp)
            // White in every theme, like the code in the app, so scanners find it easily
            .background(Color.White)
            .padding(PADDING)
            .clickable(actionStartActivity(BarcodeLink.intent(context, barcode?.id))),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (barcode == null) {
            Label("Add a barcode in Bibless")
            return@Column
        }
        Label(barcode.name, FontWeight.Medium)
        Spacer(GlanceModifier.height(GAP))
        // What's left between the labels; a classic barcode is 2.5 times as wide as it is tall
        val density = context.resources.displayMetrics.density
        val width = (size.width - PADDING * 2).value * density
        val height = (size.height - PADDING * 2 - (LABEL_HEIGHT + GAP) * 2).value * density
        val maxWidth = when (format) {
            BarcodeFormat.QR -> minOf(width, height)
            BarcodeFormat.CODE128 -> minOf(width, height / 0.4f)
        }
        val bitmap = barcodeBitmap(barcode.athleteId, format, maxWidth.toInt().coerceAtLeast(1))
        Image(
            ImageProvider(bitmap),
            contentDescription = "Barcode ${barcode.athleteId}",
            modifier = GlanceModifier.size((bitmap.width / density).dp, (bitmap.height / density).dp),
        )
        Spacer(GlanceModifier.height(GAP))
        Label(barcode.athleteId)
    }
}

@Composable
private fun Label(text: String, weight: FontWeight = FontWeight.Normal) {
    Text(
        text,
        maxLines = 1,
        style = TextStyle(
            color = ColorProvider(day = Color.Black, night = Color.Black),
            fontSize = 14.sp,
            fontWeight = weight,
            textAlign = TextAlign.Center,
        ),
    )
}

private val PADDING = 8.dp
private val GAP = 2.dp
/** A line of 14 sp text, at the default font scale. */
private val LABEL_HEIGHT = 20.dp
