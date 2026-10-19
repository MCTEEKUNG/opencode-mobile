import SwiftUI

@main struct OpenCodeMobileApp: App {
    @State private var model = MobileModel()
    @Environment(\.scenePhase) private var phase
    var body: some Scene {
        WindowGroup {
            ContentView(model: model)
                .preferredColorScheme(.dark)
                .tint(Color(red: 0.55, green: 0.91, blue: 0.74))
                .onAppear { model.setActive(true) }
                .onChange(of: phase) { _, value in model.setActive(value == .active) }
        }
    }
}
