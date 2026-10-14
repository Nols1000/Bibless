import SharedLogic
import SwiftUI
import WatchKit

@main
struct WatchApp: App {
    @StateObject private var store = BarcodeStore()
    @StateObject private var timer: TimerStore
    private let sender: WatchSessionSender

    init() {
        let timer = TimerStore(device: makeRaceDevice())
        _timer = StateObject(wrappedValue: timer)
        let isDemo = ProcessInfo.processInfo.arguments.contains(DemoData.shared.LAUNCH_ARGUMENT)
        sender = WatchSessionSender(
            timer: timer.repository,
            store: isDemo ? InMemoryStore() : UserDefaultsStore(),
            source: WKInterfaceDevice.current().name
        ) { id, json in
            KotlinBoolean(bool: WatchSync.sendTimerSession(id: id, json: json))
        }
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(store)
                .environmentObject(timer)
                // Stopped sessions go to the iPhone: when the timer stops, and those the watch
                // session couldn't take yet once it is ready
                .onChange(of: timer.isRunning) { _, running in
                    if !running { sendStopped() }
                }
                .onReceive(NotificationCenter.default.publisher(for: WatchSync.activated)) { _ in sendStopped() }
        }
    }

    private func sendStopped() {
        Task { try? await sender.sendStopped() }
    }
}
