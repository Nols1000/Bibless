package com.github.nols1000.bibless.wear

import android.content.pm.PackageManager
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowApplicationPackageManager

/**
 * Reports a watch, as the manifest's uses-feature asks for; Robolectric reports no features unless
 * told, and Bibless picks the watch's settings from this when the application starts, before a
 * test could set it. Applied to every test in robolectric.properties.
 */
@Implements(className = "android.app.ApplicationPackageManager", isInAndroidSdk = false)
class WatchPackageManager : ShadowApplicationPackageManager() {
    @Implementation
    override fun hasSystemFeature(name: String): Boolean =
        name == PackageManager.FEATURE_WATCH || super.hasSystemFeature(name)
}
