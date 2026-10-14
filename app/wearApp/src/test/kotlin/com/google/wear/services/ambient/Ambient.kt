package com.google.wear.services.ambient

import android.app.Activity
import android.content.Context

// The part of the Wear SDK's ambient API that Wear Compose uses, as no-ops; see com.google.wear.Sdk.

class AmbientManager {
    @Suppress("UNUSED_PARAMETER")
    fun createController(context: Context, options: AmbientOptions) = Controller()

    class Controller {
        fun destroy() {}
    }

    interface AmbientComponentListener {
        fun onEnterAmbient(details: ConfigurationDetails, isUpdate: Boolean)
        fun onExitAmbient()
        fun onUpdateAmbient()
    }

    interface AmbientTransitionListener

    interface ConfigurationDetails {
        fun isBurnInProtectionEnabled(): Boolean
        fun isLowBitDepthEnabled(): Boolean
    }
}

class AmbientComponentState {
    class ActivityStateRegistry {
        fun onResume() {}
        fun onPause() {}
    }

    companion object {
        @JvmStatic
        fun makeActivityStateRegistry() = ActivityStateRegistry()
    }
}

class AmbientOptions {
    companion object {
        @Suppress("UNUSED_PARAMETER")
        @JvmStatic
        fun makeActivityOptions(
            activity: Activity,
            registry: AmbientComponentState.ActivityStateRegistry,
            listener: AmbientManager.AmbientComponentListener,
            transitionListener: AmbientManager.AmbientTransitionListener?,
        ) = AmbientOptions()
    }
}
