package com.github.nols1000.bibless

import android.content.Context
import android.os.SystemClock
import android.provider.Settings

/** [RaceClock] on Android: `elapsedRealtime` counts through deep sleep and restarts with the device. */
class AndroidRaceClock(context: Context) : RaceClock {
    private val resolver = context.applicationContext.contentResolver

    override fun wallMillis() = System.currentTimeMillis()

    override fun monotonicMillis() = SystemClock.elapsedRealtime()

    override fun bootId() = Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT, 0).toString()
}
