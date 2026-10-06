import SwiftUI

@main
struct iOSApp: App {
    @StateObject private var store = BarcodeStore()
    @StateObject private var timer: TimerStore
    @StateObject private var scanner: ScannerStore

    init() {
        let device = makeRaceDevice()
        _timer = StateObject(wrappedValue: TimerStore(device: device))
        _scanner = StateObject(wrappedValue: ScannerStore(device: device))
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(store)
                .environmentObject(timer)
                .environmentObject(scanner)
        }
    }
}
