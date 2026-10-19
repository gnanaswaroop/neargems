import SwiftUI
import Shared

/// Wraps the Compose view controller exported by the Kotlin framework (see MainViewController.kt).
struct ComposeScreen: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
