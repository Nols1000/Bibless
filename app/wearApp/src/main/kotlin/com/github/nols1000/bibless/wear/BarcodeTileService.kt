package com.github.nols1000.bibless.wear

import android.content.Context
import android.graphics.Bitmap
import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.ColorProp
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DeviceParametersBuilders.DeviceParameters
import androidx.wear.protolayout.DeviceParametersBuilders.SCREEN_SHAPE_ROUND
import androidx.wear.protolayout.DimensionBuilders.dp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Image
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ProtoLayoutScope
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material3.materialScope
import androidx.wear.protolayout.material3.primaryLayout
import androidx.wear.protolayout.material3.text
import androidx.wear.protolayout.material3.textEdgeButton
import androidx.wear.protolayout.types.layoutString
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.github.nols1000.bibless.Barcode
import com.github.nols1000.bibless.BarcodeLink
import com.github.nols1000.bibless.Bibless
import com.github.nols1000.bibless.barcode.BarcodeFormat
import com.github.nols1000.bibless.barcodeBitmap
import com.google.common.util.concurrent.ListenableFuture
import java.io.ByteArrayOutputStream
import kotlin.math.sqrt

/**
 * A tile with the first barcode, a swipe away from the watch face, so runners can show it without
 * opening the app. Tapping it opens that barcode in the app at full brightness. [requestUpdate]
 * keeps it in step with the list, including changes synced from the phone.
 */
class BarcodeTileService : TileService() {
    override fun onTileRequest(requestParams: RequestBuilders.TileRequest): ListenableFuture<Tile> =
        immediate { TileContent.current(this, requestParams.deviceConfiguration, requestParams.scope).tile() }

    companion object {
        /** Asks the system to fetch the tile again, after the first barcode or the format changed. */
        fun requestUpdate(context: Context) {
            getUpdater(context).requestUpdate(BarcodeTileService::class.java)
        }
    }
}

private fun <T : Any> immediate(block: () -> T): ListenableFuture<T> =
    CallbackToFutureAdapter.getFuture { it.set(block()) }

/**
 * What the tile shows for [barcode], sized for the watch in [device]. The code image goes into
 * [scope], which hands it to the system along with the tile.
 */
internal class TileContent(
    private val context: Context,
    private val barcode: Barcode?,
    private val format: BarcodeFormat,
    private val device: DeviceParameters,
    private val scope: ProtoLayoutScope,
) {
    // Like the detail screen: on round screens the code stays inside the largest square that fits
    // in the circle, square screens get a smaller margin.
    private val sideDp = minOf(device.screenWidthDp, device.screenHeightDp) *
        if (device.screenShape == SCREEN_SHAPE_ROUND) 1 / sqrt(2f) else 0.76f
    private val density = device.screenDensity

    /** Changes whenever the image does, so the system doesn't keep showing an old code. */
    val version: String = listOf(barcode?.athleteId, format, sideDp, density).hashCode().toString(16)

    fun tile(): Tile = Tile.Builder()
        .setResourcesVersion(version)
        .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout()))
        .build()

    private fun layout(): LayoutElementBuilders.LayoutElement = materialScope(context, device) {
        if (barcode == null) {
            return@materialScope primaryLayout(
                titleSlot = { text("Bibless".layoutString) },
                mainSlot = { text("Add a barcode in Bibless".layoutString) },
                onClick = openClickable(),
            )
        }
        val bitmap = bitmap(barcode)
        primaryLayout(
            titleSlot = { text(barcode.name.layoutString) },
            mainSlot = {
                Box.Builder()
                    .setWidth(dp(bitmap.width / density))
                    .setHeight(dp(bitmap.height / density))
                    .setModifiers(modifiers(background = argb(WHITE)))
                    .addContent(
                        Image.Builder(scope)
                            .setImageResource(imageResource(bitmap), IMAGE_ID)
                            .setWidth(dp(bitmap.width / density))
                            .setHeight(dp(bitmap.height / density))
                            .build(),
                    )
                    .build()
            },
            bottomSlot = {
                textEdgeButton(
                    onClick = openClickable(),
                    labelContent = { text(barcode.athleteId.layoutString) },
                )
            },
            onClick = openClickable(),
        )
    }

    private fun openClickable() = ModifiersBuilders.Clickable.Builder()
        .setId("open")
        .setOnClick(ActionBuilders.LaunchAction.Builder().setAndroidActivity(openActivity()).build())
        .build()

    private fun imageResource(bitmap: Bitmap): ResourceBuilders.ImageResource {
        val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return ResourceBuilders.ImageResource.Builder()
            .setInlineResource(
                ResourceBuilders.InlineImageResource.Builder()
                    .setData(png.toByteArray())
                    // Undefined means encoded data (here PNG), which the renderer decodes.
                    .setFormat(ResourceBuilders.IMAGE_FORMAT_UNDEFINED)
                    // Needed for encoded data too: the renderer scales the image to them.
                    .setWidthPx(bitmap.width)
                    .setHeightPx(bitmap.height)
                    .build(),
            )
            .build()
    }

    // Drawn at the screen's own resolution, so the renderer shows each module crisp, unscaled.
    private fun bitmap(barcode: Barcode) = barcodeBitmap(barcode.athleteId, format, (sideDp * density).toInt())

    private fun modifiers(background: ColorProp?) =
        ModifiersBuilders.Modifiers.Builder()
            .setClickable(openClickable())
            .apply { background?.let { setBackground(ModifiersBuilders.Background.Builder().setColor(it).build()) } }
            .build()

    /** The app, on this barcode when there is one; see [BarcodeLink]. */
    private fun openActivity() = ActionBuilders.AndroidActivity.Builder()
        .setPackageName(context.packageName)
        .setClassName(MainActivity::class.java.name)
        .apply {
            barcode?.let {
                addKeyToExtraMapping(
                    BarcodeLink.EXTRA_BARCODE_ID,
                    ActionBuilders.AndroidStringExtra.Builder().setValue(it.id).build(),
                )
            }
        }
        .build()

    companion object {
        const val IMAGE_ID = "barcode"
        private const val WHITE = 0xFFFFFFFF.toInt()

        /** The first barcode in the list, in the watch's format. */
        fun current(context: Context, device: DeviceParameters, scope: ProtoLayoutScope): TileContent {
            val state = Bibless.repository(context).state.value
            return TileContent(context, state.barcodes.firstOrNull(), state.format, device, scope)
        }
    }
}
