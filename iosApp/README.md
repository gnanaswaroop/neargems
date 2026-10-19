# Nearwise for iOS

The iOS app is a thin host. Every screen and every piece of logic comes from the Kotlin `shared` module, which is also used by the Android app.

The Swift files here are the only iOS code:

- `Nearwise/NearwiseApp.swift`: the SwiftUI app entry point.
- `Nearwise/ComposeScreen.swift`: hosts the Compose screen from the `Shared` framework.
- `Nearwise/Info.plist`: the location-permission text and bundle identifier.

## Building (needs a Mac with Xcode)

1. Create an iOS app project in Xcode named `Nearwise` with bundle identifier `com.nearwise.app`. Add the files above to it.
2. Add a Run Script build phase that runs `./gradlew :shared:embedAndSignAppleFrameworkForXcode` from the repository root. It builds the `Shared` framework for the selected simulator or device.
3. Add `Shared.framework` from the `shared/build` output to the target's frameworks.
4. Build and run on an iOS simulator.

## Not built yet

- **CarPlay.** The car screen needs Apple's CarPlay entitlement, which Apple grants only for specific app categories. Check that before building it.
- **Verification.** The iOS code in `shared/src/iosMain` could not be compiled on Windows, because the Apple SDK is needed. It must be compiled and run on a Mac before it is trusted.
