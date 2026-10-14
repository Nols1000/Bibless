import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

val storeScreenshots = providers.gradleProperty("storeScreenshots").isPresent
val siteScreenshots = providers.gradleProperty("siteScreenshots").isPresent
val screenshotDir = rootProject.file("fastlane/metadata/android/en-US/images/phoneScreenshots").path
val siteScreenshotDir = rootProject.file("app/webApp/public/screens/phone").path

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":app:sharedUI"))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.glance.appwidget)
    implementation(libs.compose.material3)
    implementation(libs.compose.materialIconsCore)
    implementation(libs.androidx.lifecycle.runtimeCompose)
    implementation(libs.navigation.compose)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    implementation(libs.androidx.camera.mlkitVision)
    implementation(libs.mlkit.barcodeScanning)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)

    // UI tests run on the JVM with Robolectric; Roborazzi takes the store screenshots (ScreenshotTest)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.testExt.junit)
    testImplementation(libs.androidx.test.rules)
    testImplementation(libs.glance.appwidget.testing)
    testImplementation(libs.compose.uiTest.junit4)
    testImplementation(libs.compose.foundation)
    testImplementation(libs.androidx.espresso.core)
    testImplementation(libs.androidx.espresso.intents)
    debugImplementation(libs.compose.uiTest.manifest)
}

android {
    namespace = "com.github.nols1000.bibless"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.github.nols1000.bibless"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = providers.gradleProperty("appVersionCode").orNull?.toInt() ?: 1
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
                // -PsiteScreenshots: SiteScreenshotTest writes the landing page's screens
                if (siteScreenshots) {
                    test.systemProperty("roborazzi.test.record", "true")
                    test.systemProperty("siteScreenshotDir", siteScreenshotDir)
                    test.outputs.upToDateWhen { false }
                }
            }
        }
    }
}