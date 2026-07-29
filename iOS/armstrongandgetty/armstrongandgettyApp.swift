//
//  armstrongandgettyApp.swift
//  armstrongandgetty
//
//  The entire app lives in the Kotlin Multiplatform `Shared` framework — this file is
//  the platform-mandated Swift entry point and nothing more.
//

import SwiftUI
import Shared

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}

@main
struct armstrongandgettyApp: App {
    init() {
        // BGTaskScheduler handlers must be registered before launch completes.
        BackgroundRefreshKt.registerBackgroundRefresh()
    }

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea(.all)
        }
    }
}
