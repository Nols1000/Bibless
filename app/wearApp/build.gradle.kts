import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

val storeScreenshots = providers.gradleProperty("storeScreenshots").isPresent
val screenshotDir = rootProject.file("fastlane/metadata/android/en-US/images/wearScreenshots").path

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
    // AmbientLifecycleObserver, so the timer stays on screen with the wrist down
    implementation(libs.wear)
    implementation(libs.wear.input)
    implementation(libs.wear.tiles)
    implementation(libs.wear.protolayout)
    implementation(libs.androidx.concurrent.futures)
    implementation(libs.compose.materialIconsCore)
    implementation(libs.androidx.lifecycle.runtimeCompose)

    implementation(libs.wear.toolingPreview)
    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
    debugImplementation(libs.compose.uiTest.manifest)

    // UI tests run on the JVM with Robolectric; Roborazzi takes the store screenshots (ScreenshotTest)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.testExt.junit)
    testImplementation(libs.androidx.test.rules)
    testImplementation(libs.compose.uiTest.junit4)
    // Answers the system text input with the demo entry
    testImplementation(libs.androidx.espresso.intents)
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
            isMinifyEnabled = false
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
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                test.systemProperty("robolectric.graphicsMode", "NATIVE")
                // Robolectric reaches into FileDescriptor through the JDK's internals
                test.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
                // -PstoreScreenshots: ScreenshotTest writes the Play screenshots (fastlane android screenshots)
                if (storeScreenshots) {
                    test.systemProperty("roborazzi.test.record", "true")
                    test.systemProperty("screenshotDir", screenshotDir)
                    test.outputs.upToDateWhen { false }
                }
            }
        }
    }
}
