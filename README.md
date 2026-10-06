This is a Kotlin Multiplatform project targeting Android, Wear OS, iOS, watchOS, Web, Server.

* [/app/iosApp](./app/iosApp/iosApp) contains an iOS application. Even if you’re sharing your UI with Compose Multiplatform,
  you need this entry point for your iOS app. This is also where you should add SwiftUI code for your project.

* [/app/iosApp/watchApp](./app/iosApp/watchApp) contains a SwiftUI watchOS application. It is a target of the
  iOS Xcode project, is embedded in the iOS app as its companion, and uses the `SharedLogic` framework.

* [/app/wearApp](./app/wearApp/src/main/kotlin) contains a Wear OS application built with Compose for Wear OS.
  It shares the phone app's `applicationId` and depends on [sharedUI](./app/sharedUI) and [sharedLogic](./app/sharedLogic).

* [/app/sharedLogic](./app/sharedLogic/src) is for the code that will be shared between app targets in the project.
  The most important subfolder is [commonMain](./app/sharedLogic/src/commonMain/kotlin). If preferred, you
  can add code to the platform-specific folders here too.

* [/app/sharedUI](./app/sharedUI/src) holds the Compose code the phone and watch apps share: the barcode
  image and bitmap, full screen brightness and opening the start barcode. It has no Material dependency; each
  app brings its own components (Material 3 on the phone, Wear Material 3 on the watch).

* [/app/webApp](./app/webApp) contains a React web application. It uses the Kotlin/JS library produced
  by the [sharedLogic](./app/sharedLogic) module.

* [/core](./core/src) is for the code that will be shared between all targets in the project.
  The most important subfolder is [commonMain](./core/src/commonMain/kotlin). If preferred, you
  can add code to the platform-specific folders here too.

* [/server](./server/src/main/kotlin) is for the Ktor server application.

Plans for organising timed community runs with Bibless are in [docs/community-runs.md](./docs/community-runs.md).

### Running the apps

Use the run configurations provided by the run widget in your IDE's toolbar. You can also use these commands and options:

- Android app: `./gradlew :app:androidApp:assembleDebug`
- Wear OS app: `./gradlew :app:wearApp:assembleDebug`
- Server: `./gradlew :server:run`
- Web app:
  1. Install [Node.js](https://nodejs.org/en/download) (which includes `npm`)
  2. Build and run the web application:
     ```shell
     npm run build:shared
     npm install
     npm run start
     ```
- iOS app: open the [/app/iosApp](./app/iosApp) directory in Xcode and run it from there.
- watchOS app: open the [/app/iosApp](./app/iosApp) directory in Xcode, select the `watchApp` scheme and run it.

### Running tests

Use the run button in your IDE's editor gutter, or run tests using Gradle tasks:

- Android tests: `./gradlew :app:sharedUI:testAndroidHostTest :app:sharedLogic:testAndroidHostTest`
- Server tests: `./gradlew :server:test`
- Web tests: `./gradlew :app:sharedLogic:jsTest`
- iOS tests: `./gradlew :app:sharedLogic:iosSimulatorArm64Test`
- watchOS tests: `./gradlew :app:sharedLogic:watchosSimulatorArm64Test`

---

Learn more about [Kotlin Multiplatform](https://www.jetbrains.com/help/kotlin-multiplatform-dev/get-started.html)…