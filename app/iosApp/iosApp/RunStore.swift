import Foundation
import SharedLogic

/// The run this phone records for, joined by scanning the run's QR code.
@MainActor
final class RunStore: ObservableObject {
    /// UI tests can't scan on the simulator; with demo data they join the run in this variable.
    static let testRunVariable = "BIBLESS_JOIN_RUN"

    @Published private(set) var joinedRun: RunHint?

    private let device: RaceDevice
    private var observation: KotlinAutoCloseable?

    init(device: RaceDevice) {
        self.device = device
        observation = device.observeJoinedRun { [weak self] run in
            if Thread.isMainThread {
                MainActor.assumeIsolated { self?.joinedRun = run }
            } else {
                DispatchQueue.main.async { self?.joinedRun = run }
            }
        }
        let process = ProcessInfo.processInfo
        if process.arguments.contains(DemoData.shared.LAUNCH_ARGUMENT), let run = process.environment[Self.testRunVariable] {
            _ = device.join(text: run)
        }
    }

    deinit {
        observation?.close()
    }

    /// Joins the run whose QR code reads `text`; false if it isn't a run code.
    func join(_ text: String) -> Bool { device.join(text: text) != nil }

    func leave() { device.leave() }
}

extension RunHint {
    /// Date and timing mode, for one line under the name.
    var details: String {
        [date.flatMap(formatRunDate), format.label()].compactMap { $0 }.joined(separator: " · ")
    }
}

/// Formats an ISO `yyyy-MM-dd` date for people, in their locale.
private func formatRunDate(_ iso: String) -> String? {
    let parser = DateFormatter()
    parser.locale = Locale(identifier: "en_US_POSIX")
    parser.dateFormat = "yyyy-MM-dd"
    guard let date = parser.date(from: iso) else { return iso }
    return date.formatted(date: .abbreviated, time: .omitted)
}
