import SwiftUI

/// Hosts the shared Compose screen. All of the app's logic and UI lives in the Kotlin `shared` module.
@main
struct NearGemApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeScreen()
                .ignoresSafeArea()
        }
    }
}
