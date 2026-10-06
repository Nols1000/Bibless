import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":app:sharedUI"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.wear.compose.foundation)
    implementation(libs.wear.compose.material3)
    implementation(libs.wear.compose.navigation)
    implementation(libs.wear.input)
    implementation(libs.wear.tiles)
    implementation(libs.wear.protolayout)
    implementation(libs.androidx.concurrent.futures)
    implementation(libs.compose.materialIconsCore)
    implementation(libs.androidx.lifecycle.runtimeCompose)

    implementation(libs.wear.toolingPreview)
    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)

    // Store screenshots via fastlane screengrab (ScreenshotTest)
    androidTestImplementation(libs.fastlane.screengrab)
    androidTestImplementation(libs.androidx.testExt.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.uiautomator)
    // Answers the system text input with the demo entry
    androidTestImplementation(libs.androidx.espresso.intents)
}

android {
    namespace = "com.github.nols1000.bibless.wear"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        // Same applicationId as :app:androidApp so the watch app is a companion of the phone app
        applicationId = "com.github.nols1000.bibless"
        minSdk = libs.versions.android.wear.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        // Its own code, since it shares the phone app's applicationId; wearVersionCode lets one Gradle
        // run build both bundles, appVersionCode is for building this one alone.
        versionCode = (providers.gradleProperty("wearVersionCode").orNull ?: providers.gradleProperty("appVersionCode").orNull)
            ?.toInt() ?: 2
        versionName = providers.gradleProperty("appVersionName").orNull ?: "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        // Release signing is configured via env vars in CI; without them release builds stay unsigned.
        System.getenv("ANDROID_KEYSTORE_PATH")?.let { keystorePath ->
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("ANDROID_KEY_ALIAS")
                keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
            }
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            // Watches have little storage, and the shared modules bring more than the watch uses.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}
