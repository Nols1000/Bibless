package com.github.nols1000.bibless

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_FULL
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext

// The override belongs to the window, so Android drops it on its own while the app is in the background.
@Composable
actual fun FullBrightness() {
    val window = LocalContext.current.findActivity()?.window ?: return
    DisposableEffect(window) {
        val previous = window.attributes.screenBrightness
        window.attributes = window.attributes.apply { screenBrightness = BRIGHTNESS_OVERRIDE_FULL }
        onDispose { window.attributes = window.attributes.apply { screenBrightness = previous } }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
