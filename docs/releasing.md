# Releasing

All targets except `:server` are built and released from GitHub Actions.

| Workflow | Trigger | What it does |
|---|---|---|
| `ci.yml` | PRs, pushes to `main` | Tests, then builds Android, Wear OS, web and iOS (simulator, unsigned) |
| `release.yml` | Tag `vX.Y.Z` | Builds release bundles, uploads to Play closed testing and TestFlight, then updates the store listings (`store-metadata.yml`) and creates a GitHub Release |
| `pages.yml` | Changes to the web app on `main`, or manual run | Deploys the web app and the privacy policies to GitHub Pages |
| `store-metadata.yml` | Called by `release.yml`, or manual run; changes to the screenshot tests on `main` capture only | Captures the screenshots and pushes the store listings to Google Play and App Store Connect |
| `certificates.yml` | Manual run | Registers the Apple bundle ids and regenerates the App Store profiles in the match repo |

Signing and store steps are skipped automatically while their secrets or variables are missing.

Store uploads are **opt-in**. By default a release only builds the artifacts and attaches them to the GitHub Release, where you can download them and upload by hand:
- **Android**: upload `androidApp-release.aab` and `wearApp-release.aab` in Play Console.
- **iOS**: upload `Bibless.ipa` with Apple's [Transporter](https://apps.apple.com/app/transporter/id1450874784) app.

To have CI upload to Play closed testing and TestFlight, set the repository variable `STORE_UPLOAD=true` under *Settings → Secrets and variables → Actions → Variables*.

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

Local builds can override the version the same way: `./gradlew :app:androidApp:bundleRelease -PappVersionName=1.2.3 -PappVersionCode=1020300`.

## Store listings

The listings are kept in the repo and managed by [fastlane](https://docs.fastlane.tools):
- `fastlane/metadata/android/<locale>/`: Play texts, `changelogs/<versionCode>.txt` (falls back to `default.txt`), and `images/phoneScreenshots` and `images/wearScreenshots`. `images/featureGraphic.png` is drawn by `tools/screenshots/feature_graphic.py` (needs `pip install pillow segno`) and committed.
- `fastlane/metadata/ios/<locale>/`: App Store texts. Put `release_notes.txt` here.
- `fastlane/screenshots/ios/<locale>/`: App Store screenshots for iPhone and Apple Watch, assigned to device slots by pixel size.

If a listing already exists in a store, pull it once before editing: `bundle exec fastlane supply init` and `bundle exec fastlane deliver download_metadata`.

### Screenshots

Screenshots are captured by UI tests through fastlane ([snapshot](https://docs.fastlane.tools/actions/snapshot/) and [screengrab](https://docs.fastlane.tools/actions/screengrab/)). The tests start the apps with sample barcodes from `DemoData` (`app/sharedLogic`), so no real codes appear in the stores.

```sh
# iPhone 17 Pro Max (6.9") and Apple Watch Ultra 3 simulators
bundle exec fastlane ios screenshots

# Running phone and Wear OS emulators, by their `adb devices` serials
bundle exec fastlane android screenshots phone:emulator-5554 wear:emulator-5556
```

The tests are `app/iosApp/iosAppUITests`, `app/iosApp/watchAppUITests` and `ScreenshotTest` in the Android apps' `androidTest` sources. The Android tests replace the app's saved barcodes, so they refuse to run on a real device.

The images are not committed (they are git-ignored). `store-metadata.yml` captures them on CI simulators and emulators and uploads them with the listing; each run also keeps them as workflow artifacts for review. Releases run it after uploading the builds, so the listing only shows what's available; a listing sync while a Play upload is open would discard the upload. Changes to the screenshot tests run it on `main` without uploading. To update a listing between releases, run it manually: `gh workflow run store-metadata.yml`. The local commands above are for previewing.

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
| `STORE_UPLOAD` | `true` to upload releases to the stores (see above) |

## One-time setup

1. **GitHub Pages**: *Settings → Pages → Source: GitHub Actions*. The web app is served at `https://<owner>.github.io/<repo>/`.
2. **Android upload key**:
   `keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000`
   Keep it outside the repo and back it up. Use Play App Signing.
3. **Google Play**:
   1. Create the app `com.github.nols1000.bibless` in Play Console.
   2. Upload the first signed AAB by hand, since the API refuses to create the app's first release. You can download it from a release workflow run once the keystore secrets are set.
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
   1. Create the app for bundle id `com.github.nols1000.bibless` in App Store Connect. The watch app `com.github.nols1000.bibless.watchkitapp` is embedded in the iOS app, and its Smart Stack widget `com.github.nols1000.bibless.watchkitapp.widget` in the watch app; `fastlane ios certificates` registers both identifiers.
   2. The watch app and the widget share the app group `group.com.github.nols1000.bibless`. App groups can't be set up through the API: create it under *Certificates, Identifiers & Profiles → Identifiers → App Groups*, enable it on both watch identifiers, then run `certificates.yml` to regenerate the profiles.
   3. Create a Team API key with the *Admin* role under *Users and Access → Integrations → App Store Connect API*. Admin is needed so Xcode can create the distribution certificate and profiles in the cloud.
   4. Signing uses [fastlane match](https://docs.fastlane.tools/actions/match/). The Apple Distribution certificate and the App Store profiles for the app, watch app and widget are stored encrypted in the private repo `Nols1000/bibless-certificates`. The first CI run creates them through the API key, and later runs reuse them. To use them locally, run `bundle exec fastlane ios certificates readonly:true` (needs `MATCH_PASSWORD` and SSH access to that repo). The Xcode project keeps automatic signing for local development; CI switches the targets to manual signing only on the runner.
