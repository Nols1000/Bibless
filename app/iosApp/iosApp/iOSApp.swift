import SwiftUI

@main
struct iOSApp: App {
    @StateObject private var store = BarcodeStore()
    @StateObject private var timer: TimerStore
    @StateObject private var scanner: ScannerStore
    @StateObject private var runs: RunStore

    init() {
        let device = makeRaceDevice()
        _timer = StateObject(wrappedValue: TimerStore(device: device))
        _scanner = StateObject(wrappedValue: ScannerStore(device: device))
        _runs = StateObject(wrappedValue: RunStore(device: device))
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(store)
                .environmentObject(timer)
                .environmentObject(scanner)
                .environmentObject(runs)
        }
    }
}
