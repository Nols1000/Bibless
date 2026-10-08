package com.github.nols1000.bibless

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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
