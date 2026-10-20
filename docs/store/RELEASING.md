# Releasing Nearwise to Google Play

## One-time setup

1. **Upload key.** The release is signed with an upload key. Its keystore and passwords are kept
   outside the repository, in `%USERPROFILE%\.nearwise\`:
   - `nearwise-upload.jks`: the keystore
   - `upload-signing.properties`: the paths and passwords that Gradle reads

   Back both files up somewhere safe (a password manager, plus an encrypted copy). Without them
   the app cannot be updated under the same listing. Play App Signing keeps the real app-signing
   key on Google's side, and the upload key can be reset through Play Console if it is lost.

   To make the same key on another machine, copy both files into `%USERPROFILE%\.nearwise\`.
   Never commit them.

2. **Play Console.** Create the app with the package name `com.nearwise.app`, enrol in Play App
   Signing, and upload the first bundle to the Internal testing track.

## Each release

1. Bump `versionCode` (it must go up every upload) and `versionName` in `androidApp/build.gradle.kts`.
2. Run the checks:
   ```powershell
   .\gradlew.bat :androidApp:testDebugUnitTest
   .\gradlew.bat :androidApp:bundleRelease
   ```
3. Check the signer is the upload key:
   ```powershell
   apksigner verify --print-certs androidApp\build\outputs\apk\release\androidApp-release.apk
   ```
   (`apksigner` is in the Android SDK's `build-tools` folder.)
4. Upload `androidApp\build\outputs\bundle\release\androidApp-release.aab` to the track you want.

## Listing material in this folder

| File | Use |
| --- | --- |
| `play-icon-512.png` | Play Console hi-res icon (512 × 512) |
| `app-store-1024.png` | App icon for the iOS App Store (1024 × 1024) |
| `screenshot-phone-card.png` | Phone screenshot. Play asks for at least two; capture more from a phone or emulator |
| `LISTING.md` | Title, descriptions, data-safety answers and release notes |
| `PRIVACY_POLICY.md` | Privacy policy text. Publish it at a public URL and link it in Play Console |

Still to make before publishing: a feature graphic (1024 × 500), more phone screenshots, and
the privacy policy at a public URL.
