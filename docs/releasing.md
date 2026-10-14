# Releasing

All targets except `:server` are built and released from GitHub Actions.

| Workflow | Trigger | What it does |
|---|---|---|
| `ci.yml` | PRs, pushes to `main` | Tests, then builds Android, Wear OS, web and iOS (simulator, unsigned). On `main` it also deploys the landing page, the web app and the privacy policies to GitHub Pages |
| `release.yml` | Tag `vX.Y.Z` | Builds the signed bundles, captures the store screenshots, uploads the builds and listings to Play closed testing and TestFlight, then creates a GitHub Release with the `.aab` and `.ipa` files |

Store logic lives in fastlane (`fastlane/Fastfile`); the workflows only set up the toolchain and call `fastlane android release` and `fastlane ios release`. A release needs all the secrets and variables below.

## Cutting a release

```sh
git tag v1.2.3
git push origin v1.2.3
```

Versions are derived from the tag:
- Android `versionName` is `1.2.3`. `versionCode` is `major*1000000 + minor*10000 + patch*100`, plus 1 for Wear OS, because the two apps share an applicationId and each bundle needs a unique code.
- iOS `MARKETING_VERSION` is `1.2.3`, and `CURRENT_PROJECT_VERSION` is the workflow run number.

Release notes and listing changes go live with the release that ships them, so commit them before tagging.

Never move or reuse a tag once its build has reached a store: Play rejects a `versionCode` it has already seen. Tag the next patch version instead.

Each platform is one job. On Google Play, the phone bundle, the Wear OS bundle and the listing are uploaded one after another, since Play allows one edit at a time. If a job fails, fix the cause and use *Re-run failed jobs* on the same run: the upload skips a bundle the track already has. If Play has no such track, the error lists the tracks it does have.

Local builds can override the version the same way: `./gradlew :app:androidApp:bundleRelease :app:wearApp:bundleRelease -PappVersionName=1.2.3 -PappVersionCode=1020300 -PwearVersionCode=1020301`. `appVersionCode` alone sets both.

## Store listings

The listings are kept in the repo and managed by [fastlane](https://docs.fastlane.tools):
- `fastlane/metadata/android/<locale>/`: Play texts, `changelogs/<versionCode>.txt` (falls back to `default.txt`), and `images/phoneScreenshots` and `images/wearScreenshots`. `images/featureGraphic.png` (1024x500, no transparency) is committed; edit it by hand.
- `fastlane/metadata/ios/<locale>/`: App Store texts. Put `release_notes.txt` here.
- `fastlane/screenshots/ios/<locale>/`: App Store screenshots for iPhone and Apple Watch, assigned to device slots by pixel size.

If a listing already exists in a store, pull it once before editing: `bundle exec fastlane supply init` and `bundle exec fastlane deliver download_metadata`.

### Screenshots

Screenshots are captured by UI tests: on iOS through fastlane [snapshot](https://docs.fastlane.tools/actions/snapshot/), on Android with [Robolectric](https://robolectric.org) and [Roborazzi](https://github.com/takahirom/roborazzi), which render the screens on the JVM. The tests start the apps with sample barcodes from `DemoData` (`app/sharedLogic`), so no real codes appear in the stores.

```sh
# iPhone 17 Pro Max (6.9") and Apple Watch Ultra 3 simulators
bundle exec fastlane ios screenshots

# A 1080x2160 phone and a large round Wear OS screen; no emulator needed
bundle exec fastlane android screenshots
```

The tests are `app/iosApp/iosAppUITests`, `app/iosApp/watchAppUITests` and `ScreenshotTest` in the Android apps' `test` sources. The Android ones run with the other unit tests too, but only write images with `-PstoreScreenshots`, which the lane passes. Robolectric draws no system bars, so the Android screenshots have no status bar.

The stores get the screens as captured, with no headlines or device frames; the landing page (`app/webApp/src/landing`) frames them instead. The file names set the store order (`01-barcode`, `02-barcode-code128`, …). Play phone screenshots are 1080x2160, since Google Play takes none longer than twice its width.

The images are not committed (they are git-ignored). The release lanes capture them on the runner right before uploading the listing, so the listing changes together with the build it describes. Listing changes between releases go out with the next release; tag a patch version. The local commands above are for previewing.

## Secrets

Set these under *Settings → Secrets and variables → Actions*:

| Secret | Purpose |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | `base64 -i upload.jks` of the Play upload keystore |
| `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` | Keystore credentials |
| `APPLE_TEAM_ID` | Apple Developer team ID |
| `ASC_KEY_ID`, `ASC_ISSUER_ID` | App Store Connect API key (Admin role, required for cloud-managed signing) |
| `ASC_KEY_P8_BASE64` | `base64 -i AuthKey_XXXX.p8` |
| `MATCH_PASSWORD` | Encrypts the iOS certificate and profiles in the match repo |
| `MATCH_DEPLOY_KEY` | Private SSH key of the read-write deploy key on `Nols1000/bibless-certificates` |

Google Play needs no secret: CI signs in through [Workload Identity Federation](https://github.com/google-github-actions/auth#workload-identity-federation-through-a-service-account) with the GitHub OIDC token. Set these repository **variables**:

| Variable | Value |
|---|---|
| `GCP_WIF_PROVIDER` | `projects/905727411115/locations/global/workloadIdentityPools/github/providers/github` |
| `PLAY_SERVICE_ACCOUNT` | `fastlane@fastlane-510106.iam.gserviceaccount.com` |

## One-time setup

1. **GitHub Pages**: *Settings → Pages → Source: GitHub Actions*. The landing page is served at `https://<owner>.github.io/<repo>/`, the organiser web app at `…/<repo>/organise/`.
2. **Android upload key**:
   `keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000`
   Keep it outside the repo and back it up. Use Play App Signing.
3. **Google Play**:
   1. Create the app `com.github.nols1000.bibless` in Play Console.
   2. Upload the first signed AAB by hand, since the API refuses to create the app's first release. You can download it from the `android` artifact of a release workflow run; the Play upload after it fails until this is done.
   3. Add the Wear OS form factor under *Advanced settings → Form factors*.
   4. Set up closed testing (the `alpha` track, `PLAY_TRACK` in the Fastfile) with a testers list, for the phone and the Wear OS form factor.
   5. Invite the service account `fastlane@fastlane-510106.iam.gserviceaccount.com` in Play Console with release permissions.
   6. Let this repository's workflows impersonate it through the `github` workload identity pool. The pool's provider must map `attribute.repository=assertion.repository`:
      ```sh
      gcloud iam service-accounts add-iam-policy-binding fastlane@fastlane-510106.iam.gserviceaccount.com \
        --project=fastlane-510106 --role=roles/iam.workloadIdentityUser \
        --member="principalSet://iam.googleapis.com/projects/905727411115/locations/global/workloadIdentityPools/github/attribute.repository/Nols1000/bibless"
      gcloud services enable iamcredentials.googleapis.com androidpublisher.googleapis.com --project=fastlane-510106
      gh variable set GCP_WIF_PROVIDER --body projects/905727411115/locations/global/workloadIdentityPools/github/providers/github
      gh variable set PLAY_SERVICE_ACCOUNT --body fastlane@fastlane-510106.iam.gserviceaccount.com
      ```
      To run the Play lanes locally, export a JSON key of the service account as `PLAY_SERVICE_ACCOUNT_JSON`, or point `GOOGLE_APPLICATION_CREDENTIALS` at a credentials file.
4. **Apple**:
   1. Create the app for bundle id `com.github.nols1000.bibless` in App Store Connect. The watch app `com.github.nols1000.bibless.watchkitapp` and the phone widgets `com.github.nols1000.bibless.widget` are embedded in the iOS app, and the watch's Smart Stack widget `com.github.nols1000.bibless.watchkitapp.widget` in the watch app; `fastlane ios certificates` registers all identifiers.
   2. Each app shares the app group `group.com.github.nols1000.bibless` with its widgets. App groups can't be set up through the API: create it under *Certificates, Identifiers & Profiles → Identifiers → App Groups*, enable it on all four identifiers, then regenerate the profiles with `bundle exec fastlane ios certificates force:true` (needs the App Store Connect key variables, `MATCH_PASSWORD` and SSH access to the match repo).
   3. Create a Team API key with the *Admin* role under *Users and Access → Integrations → App Store Connect API*. Admin is needed so Xcode can create the distribution certificate and profiles in the cloud.
   4. The app, the watch app and both widgets each have a privacy manifest (`PrivacyInfo.xcprivacy` in their folder). It declares no tracking and no collected data, and the reasons for the APIs Apple requires them for: `UserDefaults`, and file timestamps in the shared Kotlin framework. Update it when the code starts using another of Apple's [required reason APIs](https://developer.apple.com/documentation/bundleresources/describing-use-of-required-reason-api), or App Review rejects the build.
   5. Signing uses [fastlane match](https://docs.fastlane.tools/actions/match/). The Apple Distribution certificate and the App Store profiles for the app, watch app and widgets are stored encrypted in the private repo `Nols1000/bibless-certificates`. The first CI run creates them through the API key, and later runs reuse them. To use them locally, run `bundle exec fastlane ios certificates readonly:true` (needs `MATCH_PASSWORD` and SSH access to that repo). The Xcode project keeps automatic signing for local development; CI switches the targets to manual signing only on the runner.
