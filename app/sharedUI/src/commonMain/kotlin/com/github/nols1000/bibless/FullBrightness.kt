package com.github.nols1000.bibless

import androidx.compose.runtime.Composable

/**
 * Turns the screen to full brightness while this is in the composition, so scanners read the code
 * in bright sunlight, and restores the previous brightness afterwards.
 */
@Composable
expect fun FullBrightness()
