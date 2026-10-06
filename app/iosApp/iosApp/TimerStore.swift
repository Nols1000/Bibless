import Foundation
import SharedLogic

/// SwiftUI-facing wrapper around the shared Kotlin `TimerRepository`, the phone stopwatch.
@MainActor
final class TimerStore: ObservableObject {
    @Published private(set) var session: TimerSession?

    let repository: TimerRepository
    private var observation: KotlinAutoCloseable?

    init(device: RaceDevice) {
        repository = TimerRepository(device: device)
        observation = repository.observe { [weak self] state in
            if Thread.isMainThread {
                MainActor.assumeIsolated { self?.session = state.session }
            } else {
                DispatchQueue.main.async { self?.session = state.session }
            }
        }
    }

    deinit {
        observation?.close()
    }

    var isRunning: Bool { session != nil }

    /// Finishes still in effect, in order.
    var times: [FinishTime] { session?.times as? [FinishTime] ?? [] }

    /// Milliseconds since the clock started, or nil while the timer is stopped.
    var elapsedMs: Int64? { repository.elapsedMs()?.int64Value }

    func start() { repository.start() }

    func finish() { _ = repository.tap() }

    func undo() { repository.undo() }

    func stop() { repository.stop() }
}

/// What the timer and scanner record with; they share it so they sign with the same key.
@MainActor
func makeRaceDevice() -> RaceDevice {
    // Store screenshots and UI tests start without saved sessions
    let store: KeyValueStore = ProcessInfo.processInfo.arguments.contains(DemoData.shared.LAUNCH_ARGUMENT)
        ? InMemoryStore()
        : UserDefaultsStore()
    return RaceDevice(store: store, clock: AppleRaceClock())
}

/// Elapsed time the way results show it: `m:ss`, or `h:mm:ss` from an hour on.
func formatElapsed(_ millis: Int64) -> String {
    RaceClockKt.formatElapsed(millis: millis)
}
