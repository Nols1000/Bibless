package com.github.nols1000.bibless.wear

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.LayoutElement
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ProtoLayoutScope
import androidx.wear.tiles.TileBuilders.Tile
import com.github.nols1000.bibless.BarcodeLink
import com.github.nols1000.bibless.Bibless
import com.github.nols1000.bibless.DemoData
import com.github.nols1000.bibless.Device
import com.github.nols1000.bibless.barcode.BarcodeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** What the tile offers: the first barcode, a link to it, and a code image that matches it. */
@RunWith(AndroidJUnit4::class)
class BarcodeTileTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val repository = Bibless.repository(context)

    private val watch = DeviceParametersBuilders.DeviceParameters.Builder()
        .setScreenWidthDp(225)
        .setScreenHeightDp(225)
        .setScreenDensity(2f)
        .setScreenShape(DeviceParametersBuilders.SCREEN_SHAPE_ROUND)
        .build()

    @Before
    fun setUp() {
        // DemoData replaces the saved barcodes, so never run this on a real device
        check(Build.HARDWARE == "ranchu") { "UI tests only run on an emulator (they replace saved barcodes)" }
        DemoData.load(repository)
    }

    @Test
    fun showsTheFirstBarcodeAndOpensIt() {
        val scope = ProtoLayoutScope()
        val tile = content(scope).tile()

        assertEquals(listOf("Me", "A0123456"), tile.texts())
        assertEquals(id("Me"), tile.linkedBarcodeId())
        assertTrue(TileContent.IMAGE_ID in scope.collectResources().idToImageMapping)
    }

    @Test
    fun followsTheOrder() {
        repository.moveToTop(id("Sam"))

        val tile = content().tile()
        assertEquals(listOf("Sam", "A0246802"), tile.texts())
        assertEquals(id("Sam"), tile.linkedBarcodeId())
    }

    @Test
    fun drawsANewImageForAnotherFormat() {
        val qr = content().tile().resourcesVersion

        repository.setDefaultFormat(Device.WATCH, BarcodeFormat.CODE128)

        assertNotEquals(qr, content().tile().resourcesVersion)
    }

    @Test
    fun asksForABarcodeWhenThereIsNone() {
        repository.state.value.barcodes.forEach { repository.delete(it.id) }

        val scope = ProtoLayoutScope()
        val tile = content(scope).tile()
        assertEquals(listOf("Add a barcode in Bibless"), tile.texts())
        assertNull(tile.linkedBarcodeId())
        assertFalse(scope.hasResources())
    }

    private fun content(scope: ProtoLayoutScope = ProtoLayoutScope()) = TileContent.current(context, watch, scope)

    private fun id(name: String) = repository.state.value.barcodes.first { it.name == name }.id

    private fun Tile.root(): LayoutElement = checkNotNull(tileTimeline?.timelineEntries?.single()?.layout?.root)

    private fun Tile.texts(): List<String> = root().descendants().filterIsInstance<Text>().mapNotNull { it.text?.value }.toList()

    /** The barcode the tap opens; null if it opens the app on its usual screen. */
    private fun Tile.linkedBarcodeId(): String? {
        val action = (root() as Box).modifiers?.clickable?.onClick as ActionBuilders.LaunchAction
        val activity = checkNotNull(action.androidActivity)
        assertEquals(MainActivity::class.java.name, activity.className)
        return (activity.keyToExtraMapping[BarcodeLink.EXTRA_BARCODE_ID] as ActionBuilders.AndroidStringExtra?)?.value
    }

    private fun LayoutElement.descendants(): Sequence<LayoutElement> = sequence {
        yield(this@descendants)
        val children = when (val element = this@descendants) {
            is Box -> element.contents
            is Column -> element.contents
            else -> emptyList()
        }
        children.forEach { yieldAll(it.descendants()) }
    }
}
