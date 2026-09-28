import SwiftUI

@main
struct WatchApp: App {
    @StateObject private var store = BarcodeStore()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(store)
        }
    }
}
