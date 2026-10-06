import Foundation
import SharedLogic
import WatchConnectivity

/// Syncs barcodes between iPhone and Apple Watch. Each side sends its full snapshot as the
/// application context (latest state wins, delivered in the background); the receiver merges it.
/// The watch also sends its stopped timer sessions to the iPhone, as files.
final class WatchSync: NSObject, BarcodeSync, WCSessionDelegate, @unchecked Sendable {
    private static let payloadKey = "payload"
    private static let timerSessionKey = "timerSession"

    /// Posted on the main queue once the session is ready to send.
    static let activated = Notification.Name("WatchSyncActivated")

    /// On the iPhone, where the watch's timer sessions are kept; set before the session activates.
    nonisolated(unsafe) static var receivedSessions: ReceivedSessions?

    private let repository: BarcodeRepository
    private let session: WCSession? = WCSession.isSupported() ? WCSession.default : nil

    init(repository: BarcodeRepository) {
        self.repository = repository
    }

    func activate() {
        session?.delegate = self
        session?.activate()
    }

    func push(payloadJson: String) {
        guard let session, session.activationState == .activated else { return }
        do {
            try session.updateApplicationContext([Self.payloadKey: payloadJson])
        } catch {
            // Expected when no counterpart app is installed.
            print("WatchSync: could not push barcodes: \(error.localizedDescription)")
        }
    }

    func session(
        _ session: WCSession,
        activationDidCompleteWith activationState: WCSessionActivationState,
        error: Error?
    ) {
        guard activationState == .activated else { return }
        let received = session.receivedApplicationContext[Self.payloadKey] as? String
        DispatchQueue.main.async {
            if let received {
                self.repository.applyRemote(payloadJson: received)
            }
            // Send what changed here while the session was inactive.
            self.push(payloadJson: self.repository.payload())
            NotificationCenter.default.post(name: Self.activated, object: self)
        }
    }

    func session(_ session: WCSession, didReceiveApplicationContext applicationContext: [String: Any]) {
        guard let payload = applicationContext[Self.payloadKey] as? String else { return }
        DispatchQueue.main.async {
            self.repository.applyRemote(payloadJson: payload)
        }
    }

    #if os(watchOS)
    /// Queues a stopped timer session's signed events for the iPhone. The system delivers the file
    /// whenever the iPhone is in reach, also after this app quits. False if it can't be queued now.
    static func sendTimerSession(id: String, json: String) -> Bool {
        guard WCSession.isSupported(), WCSession.default.activationState == .activated else { return false }
        let outbox = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
            .appendingPathComponent("timer-sessions", isDirectory: true)
        let file = outbox.appendingPathComponent("\(id).json")
        do {
            try FileManager.default.createDirectory(at: outbox, withIntermediateDirectories: true)
            try json.write(to: file, atomically: true, encoding: .utf8)
        } catch {
            return false
        }
        WCSession.default.transferFile(file, metadata: [timerSessionKey: id])
        return true
    }

    func session(_ session: WCSession, didFinish fileTransfer: WCSessionFileTransfer, error: Error?) {
        if let error {
            print("WatchSync: could not send a timer session: \(error.localizedDescription)")
        }
        // The system needed the file until now
        try? FileManager.default.removeItem(at: fileTransfer.file.fileURL)
    }
    #endif

    #if os(iOS)
    func session(_ session: WCSession, didReceive file: WCSessionFile) {
        guard file.metadata?[Self.timerSessionKey] != nil, let received = Self.receivedSessions else { return }
        // The file is gone once this returns, so read it now
        guard let json = try? String(contentsOf: file.fileURL, encoding: .utf8) else { return }
        Task { _ = try? await received.receive(payload: json) }
    }

    func sessionDidBecomeInactive(_ session: WCSession) {}

    func sessionDidDeactivate(_ session: WCSession) {
        // Re-activate for the next paired watch.
        session.activate()
    }
    #endif
}
