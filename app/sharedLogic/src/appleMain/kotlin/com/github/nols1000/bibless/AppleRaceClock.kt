package com.github.nols1000.bibless

import com.github.nols1000.bibless.sysctl.bibless_boot_session_uuid
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString
import platform.Foundation.NSDate
import platform.Foundation.timeIntervalSince1970
import platform.posix.CLOCK_MONOTONIC
import platform.posix.clock_gettime_nsec_np

/** [RaceClock] on Apple platforms: `CLOCK_MONOTONIC` keeps counting while the device sleeps. */
@OptIn(ExperimentalForeignApi::class)
class AppleRaceClock : RaceClock {
    private val boot: String by lazy {
        memScoped {
            val buffer = allocArray<ByteVar>(BOOT_ID_CAPACITY)
            if (bibless_boot_session_uuid(buffer, BOOT_ID_CAPACITY) == 0) buffer.toKString() else ""
        }
    }

    override fun wallMillis() = (NSDate().timeIntervalSince1970 * 1000).toLong()

    override fun monotonicMillis() = (clock_gettime_nsec_np(CLOCK_MONOTONIC.convert()) / 1_000_000u).toLong()

    override fun bootId() = boot

    private companion object {
        const val BOOT_ID_CAPACITY = 64
    }
}
