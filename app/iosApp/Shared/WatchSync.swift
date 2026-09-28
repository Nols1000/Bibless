import Foundation
import SharedLogic
import WatchConnectivity

/// Syncs barcodes between iPhone and Apple Watch. Each side sends its full snapshot as the
/// application context (latest state wins, delivered in the background); the receiver merges it.
final class WatchSync: NSObject, BarcodeSync, WCSessionDelegate, @unchecked Sendable {
    private static let payloadKey = "payload"

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
        }
    }

    func session(_ session: WCSession, didReceiveApplicationContext applicationContext: [String: Any]) {
        guard let payload = applicationContext[Self.payloadKey] as? String else { return }
        DispatchQueue.main.async {
            self.repository.applyRemote(payloadJson: payload)
        }
    }

    #if os(iOS)
    func sessionDidBecomeInactive(_ session: WCSession) {}

    func sessionDidDeactivate(_ session: WCSession) {
        // Re-activate for the next paired watch.
        session.activate()
    }
    #endif
}
