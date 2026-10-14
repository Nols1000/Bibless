package com.github.nols1000.bibless

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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
 * The device's own colours, from its wallpaper, on Android 12 and later; null where it has none, and
 * the app falls back to the Bibless palette.
 */
@Composable
internal expect fun deviceColorScheme(dark: Boolean): ColorScheme?

/**
 * Dark: a black canvas with Carbon surfaces and Volt actions with black labels. Barcodes keep their
 * own pure white card in both schemes, see [BarcodeImage].
 */
internal val BiblessDarkColors = darkColorScheme(
    primary = BiblessColors.Volt,
    onPrimary = BiblessColors.Black,
    primaryContainer = Color(0xFF2E3800),
    onPrimaryContainer = Color(0xFFE6FF80),
    inversePrimary = Color(0xFF4C5D00),
    secondary = Color(0xFFC8CCD2),
    onSecondary = BiblessColors.Black,
    secondaryContainer = Color(0xFF23262B),
    onSecondaryContainer = BiblessColors.SoftWhite,
    tertiary = Color(0xFFC8CCD2),
    onTertiary = BiblessColors.Black,
    tertiaryContainer = Color(0xFF23262B),
    onTertiaryContainer = BiblessColors.SoftWhite,
    background = BiblessColors.Black,
    onBackground = BiblessColors.SoftWhite,
    surface = BiblessColors.Black,
    onSurface = BiblessColors.SoftWhite,
    surfaceVariant = Color(0xFF23262B),
    onSurfaceVariant = Color(0xFFA8ADB5),
    surfaceTint = BiblessColors.Volt,
    surfaceDim = BiblessColors.Black,
    surfaceBright = Color(0xFF2A2D32),
    surfaceContainerLowest = BiblessColors.Black,
    surfaceContainerLow = Color(0xFF0B0C0E),
    surfaceContainer = BiblessColors.Carbon,
    surfaceContainerHigh = Color(0xFF1A1D21),
    surfaceContainerHighest = Color(0xFF23262B),
    inverseSurface = BiblessColors.SoftWhite,
    inverseOnSurface = BiblessColors.Carbon,
    outline = Color(0xFF6B7078),
    outlineVariant = Color(0xFF2E3136),
)

/**
 * Light: a soft white canvas with white cards. Volt can't carry text on white, so actions are Carbon
 * with Volt labels, and Volt fills the containers behind black text.
 */
internal val BiblessLightColors = lightColorScheme(
    primary = BiblessColors.Carbon,
    onPrimary = BiblessColors.Volt,
    primaryContainer = BiblessColors.Volt,
    onPrimaryContainer = BiblessColors.Black,
    inversePrimary = BiblessColors.Volt,
    secondary = Color(0xFF4A4F57),
    onSecondary = BiblessColors.White,
    secondaryContainer = Color(0xFFE3E5E9),
    onSecondaryContainer = BiblessColors.Carbon,
    tertiary = Color(0xFF4A4F57),
    onTertiary = BiblessColors.White,
    tertiaryContainer = Color(0xFFE3E5E9),
    onTertiaryContainer = BiblessColors.Carbon,
    background = BiblessColors.SoftWhite,
    onBackground = BiblessColors.Carbon,
    surface = BiblessColors.SoftWhite,
    onSurface = BiblessColors.Carbon,
    surfaceVariant = Color(0xFFE3E5E9),
    onSurfaceVariant = Color(0xFF4A4F57),
    surfaceTint = BiblessColors.Carbon,
    surfaceDim = Color(0xFFDADCE0),
    surfaceBright = BiblessColors.White,
    surfaceContainerLowest = BiblessColors.White,
    surfaceContainerLow = BiblessColors.White,
    surfaceContainer = Color(0xFFECEEF1),
    surfaceContainerHigh = Color(0xFFE6E8EB),
    surfaceContainerHighest = Color(0xFFDFE1E5),
    inverseSurface = BiblessColors.Carbon,
    inverseOnSurface = BiblessColors.SoftWhite,
    outline = Color(0xFF7A7F87),
    outlineVariant = Color(0xFFC9CCD1),
)

/**
 * The colours of a phone with a yellow wallpaper, as Android derives them (Material's tonal spot
 * scheme from #FFCC00). Screenshots and previews show the app in them, since most people see it in
 * their device's own colours, and Robolectric has no wallpaper to take them from.
 */
object SampleColors {
    /** Set by the screenshot tests, so the app uses these in place of the device's colours. */
    var replaceDeviceColors = false

    val light = lightColorScheme(
        primary = Color(0xFF735C0C),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFE08B),
        onPrimaryContainer = Color(0xFF584400),
        inversePrimary = Color(0xFFE3C46D),
        secondary = Color(0xFF695D3F),
        onSecondary = Color(0xFFFFFFFF),
        secondaryContainer = Color(0xFFF2E1BB),
        onSecondaryContainer = Color(0xFF50462A),
        tertiary = Color(0xFF47664A),
        onTertiary = Color(0xFFFFFFFF),
        tertiaryContainer = Color(0xFFC8ECC9),
        onTertiaryContainer = Color(0xFF2F4D34),
        background = Color(0xFFFFF8F1),
        onBackground = Color(0xFF1F1B13),
        surface = Color(0xFFFFF8F1),
        onSurface = Color(0xFF1F1B13),
        surfaceVariant = Color(0xFFEBE1CF),
        onSurfaceVariant = Color(0xFF4C4639),
        surfaceTint = Color(0xFF735C0C),
        inverseSurface = Color(0xFF343027),
        inverseOnSurface = Color(0xFFF8F0E2),
        outline = Color(0xFF7E7667),
        outlineVariant = Color(0xFFCFC6B4),
        surfaceBright = Color(0xFFFFF8F1),
        surfaceContainer = Color(0xFFF5EDDF),
        surfaceContainerHigh = Color(0xFFF0E7D9),
        surfaceContainerHighest = Color(0xFFEAE1D4),
        surfaceContainerLow = Color(0xFFFBF3E5),
        surfaceContainerLowest = Color(0xFFFFFFFF),
        surfaceDim = Color(0xFFE1D9CC),
    )

    val dark = darkColorScheme(
        primary = Color(0xFFE3C46D),
        onPrimary = Color(0xFF3D2F00),
        primaryContainer = Color(0xFF584400),
        onPrimaryContainer = Color(0xFFFFE08B),
        inversePrimary = Color(0xFF735C0C),
        secondary = Color(0xFFD5C5A1),
        onSecondary = Color(0xFF392F15),
        secondaryContainer = Color(0xFF50462A),
        onSecondaryContainer = Color(0xFFF2E1BB),
        tertiary = Color(0xFFADCFAE),
        onTertiary = Color(0xFF19361F),
        tertiaryContainer = Color(0xFF2F4D34),
        onTertiaryContainer = Color(0xFFC8ECC9),
        background = Color(0xFF16130B),
        onBackground = Color(0xFFEAE1D4),
        surface = Color(0xFF16130B),
        onSurface = Color(0xFFEAE1D4),
        surfaceVariant = Color(0xFF4C4639),
        onSurfaceVariant = Color(0xFFCFC6B4),
        surfaceTint = Color(0xFFE3C46D),
        inverseSurface = Color(0xFFEAE1D4),
        inverseOnSurface = Color(0xFF343027),
        outline = Color(0xFF989080),
        outlineVariant = Color(0xFF4C4639),
        surfaceBright = Color(0xFF3D392F),
        surfaceContainer = Color(0xFF231F17),
        surfaceContainerHigh = Color(0xFF2D2A21),
        surfaceContainerHighest = Color(0xFF38342B),
        surfaceContainerLow = Color(0xFF1F1B13),
        surfaceContainerLowest = Color(0xFF110E07),
        surfaceDim = Color(0xFF16130B),
    )
}
