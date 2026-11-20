import SwiftUI
import WidgetKit
import ComposeApp

@main
struct iOSApp: App {

    @Environment(\.scenePhase) private var scenePhase

    init() {
        // WidgetKit solo existe en Swift: Kotlin avisa por este puente cuando cambian los datos.
        IosHooks.shared.reloadWidgets = {
            WidgetCenter.shared.reloadAllTimelines()
        }
    }

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea()
        }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active {
                MainViewControllerKt.onAppForeground()
            }
        }
    }
}

struct ComposeView: UIViewControllerRepresentable {

    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
