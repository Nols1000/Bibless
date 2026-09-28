import SwiftUI

@main
struct iOSApp: App {
    @StateObject private var store = BarcodeStore()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(store)
        }
    }
}
