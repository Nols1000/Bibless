package com.github.nols1000.bibless

import com.github.nols1000.bibless.race.TimingMode

/** How the apps name a timing mode. */
fun TimingMode.label(): String = when (this) {
    TimingMode.TOKENS -> "Finish tokens"
    TimingMode.SCAN_ORDER -> "Scan order"
}
