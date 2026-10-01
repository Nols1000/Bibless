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
import androidx.wear.protolayout.DimensionBuilders.expand
import androidx.wear.protolayout.DimensionBuilders.sp
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.LayoutElementBuilders.Box
import androidx.wear.protolayout.LayoutElementBuilders.Column
import androidx.wear.protolayout.LayoutElementBuilders.FontStyle
import androidx.wear.protolayout.LayoutElementBuilders.Image
import androidx.wear.protolayout.LayoutElementBuilders.Spacer
import androidx.wear.protolayout.LayoutElementBuilders.Text
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.ResourceBuilders.Resources
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders.Tile
import androidx.wear.tiles.TileService
import com.github.nols1000.bibless.Barcode
import com.github.nols1000.bibless.BarcodeLink
import com.github.nols1000.bibless.Bibless
import com.github.nols1000.bibless.barcodeBitmap
import com.github.nols1000.bibless.barcode.BarcodeFormat
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
        immediate { TileContent.current(this, requestParams.deviceConfiguration).tile() }

    override fun onTileResourcesRequest(requestParams: RequestBuilders.ResourcesRequest): ListenableFuture<Resources> =
        immediate { TileContent.current(this, requestParams.deviceConfiguration).resources() }

    companion object {
        /** Asks the system to fetch the tile again, after the first barcode or the format changed. */
        fun requestUpdate(context: Context) {
            getUpdater(context).requestUpdate(BarcodeTileService::class.java)
        }
    }
}

private fun <T : Any> immediate(block: () -> T): ListenableFuture<T> =
    CallbackToFutureAdapter.getFuture { it.set(block()) }

/** What the tile shows for [barcode], sized for the watch in [device]. */
internal class TileContent(
    private val context: Context,
    private val barcode: Barcode?,
    private val format: BarcodeFormat,
    device: DeviceParameters,
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

    private fun layout(): LayoutElementBuilders.LayoutElement {
        if (barcode == null) {
            return Box.Builder()
                .setWidth(expand())
                .setHeight(expand())
                .setModifiers(modifiers(background = null))
                .addContent(label("Add a barcode in Bibless", argb(WHITE), sideDp))
                .build()
        }
        val bitmap = bitmap(barcode)
        return Box.Builder()
            .setWidth(expand())
            .setHeight(expand())
            // White behind everything, like the detail screen, so scanners find the code easily.
            .setModifiers(modifiers(background = argb(WHITE)))
            .addContent(
                Column.Builder()
                    .setWidth(dp(sideDp))
                    .addContent(label(barcode.name, argb(BLACK), sideDp))
                    .addContent(Spacer.Builder().setHeight(dp(2f)).build())
                    .addContent(
                        Image.Builder()
                            .setResourceId(IMAGE_ID)
                            .setWidth(dp(bitmap.width / density))
                            .setHeight(dp(bitmap.height / density))
                            .build(),
                    )
                    .addContent(Spacer.Builder().setHeight(dp(2f)).build())
                    .addContent(label(barcode.athleteId, argb(BLACK), sideDp))
                    .build(),
            )
            .build()
    }

    fun resources(): Resources {
        val builder = Resources.Builder().setVersion(version)
        if (barcode != null) {
            val bitmap = bitmap(barcode)
            val png = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            builder.addIdToImageMapping(
                IMAGE_ID,
                ResourceBuilders.ImageResource.Builder()
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
                    .build(),
            )
        }
        return builder.build()
    }

    // Drawn at the screen's own resolution, so the renderer shows each module crisp, unscaled.
    private fun bitmap(barcode: Barcode) = barcodeBitmap(barcode.athleteId, format, (sideDp * density).toInt())

    private fun modifiers(background: ColorProp?) =
        ModifiersBuilders.Modifiers.Builder()
            .setClickable(
                ModifiersBuilders.Clickable.Builder()
                    .setId("open")
                    .setOnClick(ActionBuilders.LaunchAction.Builder().setAndroidActivity(openActivity()).build())
                    .build(),
            )
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

    private fun label(text: String, color: ColorProp, widthDp: Float) =
        Box.Builder()
            .setWidth(dp(widthDp))
            .addContent(
                Text.Builder()
                    .setText(text)
                    .setMaxLines(1)
                    .setOverflow(LayoutElementBuilders.TEXT_OVERFLOW_ELLIPSIZE_END)
                    .setFontStyle(FontStyle.Builder().setColor(color).setSize(sp(13f)).build())
                    .build(),
            )
            .build()

    companion object {
        const val IMAGE_ID = "barcode"
        private const val WHITE = 0xFFFFFFFF.toInt()
        private const val BLACK = 0xFF000000.toInt()

        /** The first barcode in the list, in the watch's format. */
        fun current(context: Context, device: DeviceParameters): TileContent {
            val state = Bibless.repository(context).state.value
            return TileContent(context, state.barcodes.firstOrNull(), state.format, device)
        }
    }
}
