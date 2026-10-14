package com.github.nols1000.bibless

import androidx.compose.ui.graphics.Color

/**
 * The Bibless palette, shared with the landing page (app/webApp/src/landing/landing.css): Volt is
 * the only colour, and everything else is black, white and the greys between.
 */
object BiblessColors {
    /** Hyper Volt, the primary accent. Only ever behind or under dark ink, never as text on white. */
    val Volt = Color(0xFFD4FF00)

    val Black = Color(0xFF000000)

    /** The dark surfaces. */
    val Carbon = Color(0xFF121417)
    val White = Color(0xFFFFFFFF)

    /** The light canvas. */
    val SoftWhite = Color(0xFFF4F5F7)
}

/**
 * Screenshots and previews show the apps in the colours of a device with a yellow wallpaper, since
 * most people see them in their device's own colours, and Robolectric has no wallpaper to take them
 * from. Each app defines those colours for its own Material components.
 */
object SampleColors {
    /** Set by the screenshot tests, so the apps use the sample colours in place of the device's. */
    var replaceDeviceColors = false
}
