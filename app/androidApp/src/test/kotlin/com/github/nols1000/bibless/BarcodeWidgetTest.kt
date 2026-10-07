package com.github.nols1000.bibless

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.appwidget.testing.unit.hasStartActivityClickAction
import androidx.glance.testing.unit.hasContentDescription
import androidx.glance.testing.unit.hasText
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.nols1000.bibless.barcode.BarcodeFormat
import org.junit.Test
import org.junit.runner.RunWith

/** The home-screen widget's content. */
@RunWith(AndroidJUnit4::class)
class BarcodeWidgetTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val me = Barcode(id = "me", name = "Me", athleteId = "A0123456", updatedAt = 0)

    @Test
    fun showsTheBarcodeAndOpensIt() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(160.dp, 160.dp))
        provideComposable { BarcodeWidgetContent(me, BarcodeFormat.QR) }

        onNode(hasText("Me")).assertExists()
        onNode(hasText("A0123456")).assertExists()
        onNode(hasContentDescription("Barcode A0123456")).assertExists()
        onNode(hasStartActivityClickAction(BarcodeLink.intent(context, me.id))).assertExists()
    }

    @Test
    fun fitsAClassicBarcodeIntoAWideWidget() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(250.dp, 110.dp))
        provideComposable { BarcodeWidgetContent(me, BarcodeFormat.CODE128) }

        onNode(hasContentDescription("Barcode A0123456")).assertExists()
    }

    @Test
    fun asksForABarcodeWhenThereIsNone() = runGlanceAppWidgetUnitTest {
        setContext(context)
        setAppWidgetSize(DpSize(160.dp, 160.dp))
        provideComposable { BarcodeWidgetContent(null, BarcodeFormat.QR) }

        onNode(hasText("Add a barcode in Bibless")).assertExists()
        onNode(hasStartActivityClickAction(BarcodeLink.intent(context, null))).assertExists()
    }
}
