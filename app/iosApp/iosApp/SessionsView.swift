import SharedLogic
import SwiftUI
import UIKit

/// The timer and scanner sessions recorded on this phone, each shared as files for the organiser:
/// a CSV for people and a JSON bundle for the Bibless web app.
struct SessionsView: View {
    @EnvironmentObject private var timer: TimerStore
    @EnvironmentObject private var scanner: ScannerStore
    @EnvironmentObject private var runs: RunStore
    @EnvironmentObject private var received: ReceivedStore
    @Environment(\.dismiss) private var dismiss
    @State private var sessions: [RecordedSession] = []
    @State private var sharing: SharedFiles?

    private var exporter: SessionExporter {
        SessionExporter(timer: timer.repository, scanner: scanner.repository, received: received.sessions)
    }

    var body: some View {
        NavigationStack {
            List {
                if !sessions.isEmpty {
                    Section {
                        ForEach(sessions, id: \.id) { session in
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(title(session))
                                    Text(session.startDate.formatted(date: .abbreviated, time: .shortened))
                                        .font(.subheadline)
                                        .foregroundStyle(.secondary)
                                    Text(details(session))
                                        .font(.subheadline)
                                        .foregroundStyle(.secondary)
                                }
                                Spacer()
                                Button("Share") { share(session) }
                                    .buttonStyle(.borderless)
                                    .accessibilityIdentifier("share-\(session.id)")
                            }
                        }
                    } footer: {
                        Text("The JSON file imports into the Bibless web app; the CSV opens in a spreadsheet.")
                    }
                }
            }
            .overlay {
                if sessions.isEmpty {
                    ContentUnavailableView(
                        "No Sessions",
                        systemImage: "tray",
                        description: Text("Timer and scanner sessions show up here, to share with the organiser.")
                    )
                }
            }
            .navigationTitle("Recorded Sessions")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
            .onAppear { sessions = exporter.sessions() }
            // A session from the watch can arrive while the list is open
            .onChange(of: received.version) { sessions = exporter.sessions() }
            .sheet(item: $sharing) { files in
                ShareSheet(items: files.urls)
            }
        }
    }

    private func share(_ session: RecordedSession) {
        Task { @MainActor in
            let offset = TimeZone.current.secondsFromGMT(for: session.startDate) / 60
            guard let files = try? await exporter.export(
                kind: session.kind,
                id: session.id,
                deviceName: UIDevice.current.name,
                utcOffsetMinutes: Int32(offset)
            ) else { return }
            // A fresh folder per share, so an earlier share's files never go along
            let folder = FileManager.default.temporaryDirectory
                .appendingPathComponent("exports/\(UUID().uuidString)", isDirectory: true)
            try? FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
            let urls = files.compactMap { file -> URL? in
                let url = folder.appendingPathComponent(file.name)
                return (try? file.content.write(to: url, atomically: true, encoding: .utf8)) != nil ? url : nil
            }
            sharing = SharedFiles(urls: urls)
        }
    }

    private func title(_ session: RecordedSession) -> String {
        let title = switch session.kind {
        case .timer: "Timer"
        default: session.mode == .scanOrder ? "Scanner, Scan Order" : "Scanner"
        }
        return session.source.map { "\(title) on \($0)" } ?? title
    }

    private func details(_ session: RecordedSession) -> String {
        let count = Int(session.count)
        var parts = [session.kind == .timer
            ? (count == 1 ? "1 finish time" : "\(count) finish times")
            : (count == 1 ? "1 finisher" : "\(count) finishers")]
        if session.run == nil {
            parts.append("no run")
        } else if let run = runs.joinedRun, run.run == session.run {
            parts.append(run.name)
        }
        if session.running { parts.append("recording") }
        return parts.joined(separator: " · ")
    }
}

private struct SharedFiles: Identifiable {
    let id = UUID()
    let urls: [URL]
}

private struct ShareSheet: UIViewControllerRepresentable {
    let items: [URL]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }

    func updateUIViewController(_ controller: UIActivityViewController, context: Context) {}
}

private extension RecordedSession {
    var startDate: Date { Date(timeIntervalSince1970: TimeInterval(startedAt) / 1000) }
}
