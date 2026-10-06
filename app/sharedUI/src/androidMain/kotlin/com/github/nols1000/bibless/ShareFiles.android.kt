package com.github.nols1000.bibless

import android.content.ClipData
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import java.io.File
import java.text.DateFormat
import java.util.Date
import java.util.TimeZone

/** Files go to the cache's `exports` folder, shared through the app's `<package>.exports` FileProvider. */
@Composable
actual fun rememberShareFiles(): (List<ExportFile>) -> Unit {
    val context = LocalContext.current
    return remember(context) {
        { files ->
            // A fresh folder per share, so an earlier share's files never go along
            val folder = File(context.cacheDir, "exports/${System.currentTimeMillis()}").apply { mkdirs() }
            val uris = files.map { file ->
                val written = File(folder, file.name).apply { writeText(file.content) }
                FileProvider.getUriForFile(context, "${context.packageName}.exports", written)
            }
            val send = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "*/*"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                clipData = ClipData.newRawUri(null, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

@Composable
actual fun rememberDeviceName(): String {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME)?.takeIf { it.isNotBlank() } ?: Build.MODEL
    }
}

actual fun formatSessionStart(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))

actual fun utcOffsetMinutes(millis: Long): Int = TimeZone.getDefault().getOffset(millis) / 60_000
