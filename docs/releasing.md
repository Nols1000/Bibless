# Releasing

All targets except `:server` are built and released from GitHub Actions.

| Workflow | Trigger | What it does |
|---|---|---|
| `ci.yml` | PRs, pushes to `main` | Tests, then builds Android, Wear OS, web and iOS (simulator, unsigned) |
| `release.yml` | Tag `vX.Y.Z` | Builds release bundles, deploys web to GitHub Pages, uploads to the Play internal track and TestFlight, and creates a GitHub Release |
| `store-metadata.yml` | Changes to `fastlane/metadata/**` or `fastlane/screenshots/**` on `main`, or manual run | Pushes store listings to Google Play and App Store Connect |

Signing and store steps are skipped automatically while their secrets are missing.

Store uploads are **opt-in**. By default a release only builds the artifacts and attaches them to the GitHub Release, where you can download them and upload by hand:
- **Android**: upload `androidApp-release.aab` and `wearApp-release.aab` in Play Console.
- **iOS**: upload `Bibless.ipa` with Apple's [Transporter](https://apps.apple.com/app/transporter/id1450874784) app.

To have CI upload to the Play internal track and TestFlight, set the repository variable `STORE_UPLOAD=true` under *Settings → Secrets and variables → Actions → Variables*.

## Cutting a release

```sh
git tag v1.2.3
git push origin v1.2.3
```

Versions are derived from the tag:
- Android `versionName` is `1.2.3`. `versionCode` is `major*1000000 + minor*10000 + patch*100`, plus 1 for Wear OS, because the two apps share an applicationId and each bundle needs a unique code.
- iOS `MARKETING_VERSION` is `1.2.3`, and `CURRENT_PROJECT_VERSION` is the workflow run number.

Local builds can override the version the same way: `./gradlew :app:androidApp:bundleRelease -PappVersionName=1.2.3 -PappVersionCode=1020300`.

## Store listings

The listings are kept in the repo and managed by [fastlane](https://docs.fastlane.tools):
- `fastlane/metadata/android/<locale>/`: Play texts, `changelogs/<versionCode>.txt` (falls back to `default.txt`), and `images/phoneScreenshots` and `images/wearScreenshots`.
- `fastlane/metadata/ios/<locale>/`: App Store texts. Put `release_notes.txt` here.
- `fastlane/screenshots/ios/<locale>/`: App Store screenshots for iPhone, iPad and Apple Watch, assigned to device slots by pixel size.

If a listing already exists in a store, pull it once before editing: `bundle exec fastlane supply init` and `bundle exec fastlane deliver download_metadata`.

## Secrets

Set these under *Settings → Secrets and variables → Actions*:

| Secret | Purpose |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | `base64 -i upload.jks` of the Play upload keystore |
| `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD` | Keystore credentials |
| `PLAY_SERVICE_ACCOUNT_JSON` | JSON key of a Google Cloud service account with access to the app in Play Console |
| `APPLE_TEAM_ID` | Apple Developer team ID |
| `ASC_KEY_ID`, `ASC_ISSUER_ID` | App Store Connect API key (Admin role, required for cloud-managed signing) |
| `ASC_KEY_P8_BASE64` | `base64 -i AuthKey_XXXX.p8` |
| `MATCH_PASSWORD` | Encrypts the iOS certificate and profiles in the match repo |
| `MATCH_DEPLOY_KEY` | Private SSH key of the read-write deploy key on `Nols1000/bibless-certificates` |

## One-time setup

1. **GitHub Pages**: *Settings → Pages → Source: GitHub Actions*. The web app is served at `https://<owner>.github.io/<repo>/`.
2. **Android upload key**:
   `keytool -genkeypair -v -keystore upload.jks -alias upload -keyalg RSA -keysize 2048 -validity 10000`
   Keep it outside the repo and back it up. Use Play App Signing.
3. **Google Play**:
   1. Create the app `com.github.nols1000.bibless` in Play Console.
   2. Upload the first signed AAB by hand, since the API refuses to create the app's first release. You can download it from a release workflow run once the keystore secrets are set.
   3. Add the Wear OS form factor under *Advanced settings → Form factors*.
   4. Create a service account in Google Cloud, enable the *Google Play Android Developer API*, and invite the account in Play Console with release permissions.
4. **Apple**:
   1. Create the app for bundle id `com.github.nols1000.bibless` in App Store Connect. The watch app `com.github.nols1000.bibless.watchkitapp` is embedded in the iOS app, and its identifier is registered automatically on the first cloud-signed build.
   2. Create a Team API key with the *Admin* role under *Users and Access → Integrations → App Store Connect API*. Admin is needed so Xcode can create the distribution certificate and profiles in the cloud.
   3. Signing uses [fastlane match](https://docs.fastlane.tools/actions/match/). The Apple Distribution certificate and the App Store profiles for the app and watch app are stored encrypted in the private repo `Nols1000/bibless-certificates`. The first CI run creates them through the API key, and later runs reuse them. To use them locally, run `bundle exec fastlane ios certificates readonly:true` (needs `MATCH_PASSWORD` and SSH access to that repo). The Xcode project keeps automatic signing for local development; CI switches the targets to manual signing only on the runner.
