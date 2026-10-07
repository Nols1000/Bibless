package com.google.wear

import android.content.Context
import com.google.wear.services.ambient.AmbientManager

/**
 * Stands in for the Wear SDK that a watch provides at runtime, which Wear Compose's ambient mode
 * support calls into; on the JVM there is none. Only the ambient manager, which never goes ambient.
 */
object Sdk {
    @JvmStatic
    fun <T> getWearManager(context: Context, managerClass: Class<T>): T {
        check(managerClass == AmbientManager::class.java) { "No fake for $managerClass" }
        return managerClass.cast(AmbientManager())
    }
}
