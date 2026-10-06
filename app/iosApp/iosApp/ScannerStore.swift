import AudioToolbox
import Foundation
import SharedLogic
import UIKit

/// SwiftUI-facing wrapper around the shared Kotlin `ScannerRepository`, the finish scanner.
@MainActor
final class ScannerStore: ObservableObject {
    @Published private(set) var session: ScannerSession?
    /// What the last scan did, shown under the camera.
    @Published private(set) var message: ScanMessage?

    let repository: ScannerRepository
    private var observation: KotlinAutoCloseable?

    init(device: RaceDevice) {
        repository = ScannerRepository(device: device)
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

    /// Finishers in the order they were recorded, without undone ones.
    var records: [ScanRecord] { session?.records as? [ScanRecord] ?? [] }

    var hasPending: Bool { session?.pendingAthleteId != nil || session?.pendingPosition != nil }

    var isScanOrder: Bool { session?.mode == .scanOrder }

    /// Starts a session that gives positions by `mode`; the joined run's mode, if there is one.
    func start(mode: TimingMode) { repository.start(mode: mode) }

    /// A code from the camera, which reports the same code again while it stays in view.
    func scan(_ text: String) { handle(repository.scan(text: text)) }

    /// A typed code, taken every time.
    func enter(_ text: String) { handle(repository.enter(text: text)) }

    /// A finisher without a barcode, with the name they gave, if any.
    func addUnknown(name: String) { handle(repository.addUnknown(name: name)) }

    func undo() {
        repository.undo()
        message = nil
    }

    func stop() {
        repository.stop()
        message = nil
    }

    private func handle(_ outcome: ScanOutcome) {
        guard let message = ScanMessageKt.message(outcome) else { return }
        self.message = message
        switch message.kind {
        case .recorded:
            AudioServicesPlaySystemSound(1057)
            UINotificationFeedbackGenerator().notificationOccurred(.success)
        case .waiting:
            AudioServicesPlaySystemSound(1104)
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
        default:
            AudioServicesPlaySystemSound(1073)
            UINotificationFeedbackGenerator().notificationOccurred(.error)
        }
    }
}
