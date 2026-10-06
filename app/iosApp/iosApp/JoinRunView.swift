import SharedLogic
import SwiftUI

/// Which run this phone records for, on the timer's and scanner's start screens. Joining is only
/// offered there: a session keeps the run it started with.
struct JoinedRunCard: View {
    @EnvironmentObject private var runs: RunStore
    @State private var isJoining = false

    var body: some View {
        HStack(alignment: .center, spacing: 8) {
            VStack(alignment: .leading, spacing: 2) {
                if let run = runs.joinedRun {
                    Text(run.name).font(.headline)
                    Text(run.details).font(.subheadline).foregroundStyle(.secondary)
                } else {
                    Text("No Run").font(.headline)
                    Text("Scan the run's QR code from the organiser, or record now and add it to the run later.")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
            }
            Spacer()
            if runs.joinedRun != nil {
                Button("Leave", action: runs.leave)
            } else {
                Button("Join Run") { isJoining = true }
            }
        }
        .padding()
        .background(.quaternary, in: RoundedRectangle(cornerRadius: 12))
        .sheet(isPresented: $isJoining) {
            JoinRunView()
        }
    }
}

/// The run a running session records for, as one line.
struct SessionRunLine: View {
    @EnvironmentObject private var runs: RunStore
    let run: String?

    var body: some View {
        if let run {
            Text(runs.joinedRun?.run == run ? runs.joinedRun!.name : "Joined run")
                .font(.subheadline)
        }
    }
}

/// Scans a run's QR code and joins it.
private struct JoinRunView: View {
    @EnvironmentObject private var runs: RunStore
    @Environment(\.dismiss) private var dismiss
    @State private var isNotARun = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                CameraScannerView { code in
                    if runs.join(code) {
                        UINotificationFeedbackGenerator().notificationOccurred(.success)
                        dismiss()
                    } else if !isNotARun {
                        isNotARun = true
                        UINotificationFeedbackGenerator().notificationOccurred(.error)
                    }
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(.black)
                .clipShape(RoundedRectangle(cornerRadius: 16))
                Text(isNotARun
                     ? "That's not a run's QR code. Scan the code on the organiser's run sheet or screen."
                     : "Scan the QR code on the organiser's run sheet or screen.")
                    .foregroundStyle(isNotARun ? .red : .primary)
            }
            .padding()
            .navigationTitle("Join a Run")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
            }
        }
    }
}
