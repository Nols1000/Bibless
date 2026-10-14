import Foundation
import SharedLogic

/// The timer sessions the Apple Watch sent to this iPhone, shared like the phone's own.
@MainActor
final class ReceivedStore: ObservableObject {
    /// Counts sessions kept since launch, so lists know to refresh.
    @Published private(set) var version = 0

    let sessions: ReceivedSessions
    private var observation: KotlinAutoCloseable?

    init() {
        // Store screenshots and UI tests start without received sessions
        let store: KeyValueStore = ProcessInfo.processInfo.arguments.contains(DemoData.shared.LAUNCH_ARGUMENT)
            ? InMemoryStore()
            : UserDefaultsStore()
        sessions = ReceivedSessions(store: store)
        WatchSync.receivedSessions = sessions
        observation = sessions.observe { [weak self] in
            DispatchQueue.main.async { self?.version += 1 }
        }
    }

    deinit {
        observation?.close()
    }
}
