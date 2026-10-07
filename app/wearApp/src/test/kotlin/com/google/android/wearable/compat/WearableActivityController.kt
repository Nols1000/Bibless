package com.google.android.wearable.compat

import android.app.Activity
import android.os.Bundle
import java.io.FileDescriptor
import java.io.PrintWriter

/**
 * Stands in for the Wear OS shared library that a watch provides at runtime, which
 * AmbientLifecycleObserver drives; on the JVM there is none. The app never goes ambient here: tests
 * pass Ambient(isAmbient = true) to WearApp instead.
 */
@Suppress("unused", "UNUSED_PARAMETER")
class WearableActivityController(tag: String, activity: Activity, callback: AmbientCallback) {
    fun onCreate() {}
    fun onResume() {}
    fun onPause() {}
    fun onStop() {}
    fun onDestroy() {}
    fun setAmbientEnabled() {}
    fun setAutoResumeEnabled(enabled: Boolean) {}
    fun setAmbientOffloadEnabled(enabled: Boolean) {}
    fun isAmbient() = false
    fun dump(prefix: String, fd: FileDescriptor, writer: PrintWriter, args: Array<String>) {}

    abstract class AmbientCallback {
        open fun onEnterAmbient(ambientDetails: Bundle) {}
        open fun onUpdateAmbient() {}
        open fun onExitAmbient() {}
        open fun onInvalidateAmbientOffload() {}
    }

    companion object {
        const val EXTRA_BURN_IN_PROTECTION = "com.google.android.wearable.compat.extra.BURN_IN_PROTECTION"
        const val EXTRA_LOWBIT_AMBIENT = "com.google.android.wearable.compat.extra.LOWBIT_AMBIENT"
    }
}
