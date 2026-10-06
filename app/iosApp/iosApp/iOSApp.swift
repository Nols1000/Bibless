import SwiftUI

@main
struct iOSApp: App {
    @StateObject private var store = BarcodeStore()
    @StateObject private var timer = TimerStore()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(store)
                .environmentObject(timer)
        }
    }
}
