import SharedLogic
import SwiftUI
import WatchKit

/// The watch stopwatch for timekeepers: one big button per finisher, with a tap on the wrist for
/// each, so their eyes can stay on the line. With the wrist down it shows the minutes and the count.
struct TimerView: View {
    @EnvironmentObject private var timer: TimerStore
    @Environment(\.isLuminanceReduced) private var isLuminanceReduced
    @State private var isConfirmingStop = false

    var body: some View {
        Group {
            if timer.isRunning {
                running
            } else {
                notStarted
            }
        }
        .navigationTitle("Timer")
    }

    private var notStarted: some View {
        ScrollView {
            VStack(spacing: 8) {
                Button("Start") { timer.start() }
                    .buttonStyle(.borderedProminent)
                Text("Times go to your iPhone when you stop, or later if it isn't nearby.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                Text("To keep the timer on screen with your wrist down, set Return to Clock for Bibless to After 1 Hour in Settings.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var running: some View {
        // The always-on display updates about once a minute, so it shows minutes only
        TimelineView(.periodic(from: .now, by: isLuminanceReduced ? 60 : 0.2)) { _ in
            let elapsed = timer.elapsedMs ?? 0
            if isLuminanceReduced {
                VStack {
                    Text("\(elapsed / 60_000) min")
                        .font(.title2)
                    Text(finishers(timer.times.count))
                        .font(.footnote)
                }
            } else {
                VStack(spacing: 4) {
                    Text(formatElapsed(elapsed))
                        .font(.title3.monospacedDigit())
                        .accessibilityIdentifier("elapsed")
                    Button(action: finish) {
                        VStack {
                            Text("Finish")
                                .font(.headline)
                            Text("P\(timer.times.count + 1)")
                                .font(.caption)
                        }
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .accessibilityIdentifier("finish")
                    HStack {
                        Button("Undo") {
                            timer.undo()
                            WKInterfaceDevice.current().play(.directionDown)
                        }
                        .disabled(timer.times.isEmpty)
                        Button("Stop") { isConfirmingStop = true }
                    }
                    Text(timer.times.last.map { "P\($0.position) \(formatElapsed($0.elapsedMs))" } ?? "No finishers yet")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
            }
        }
        .confirmationDialog("Stop the timer?", isPresented: $isConfirmingStop, titleVisibility: .visible) {
            Button("Stop Timer", role: .destructive) { timer.stop() }
        } message: {
            Text("\(finishers(timer.times.count)). The times go to your iPhone.")
        }
    }

    private func finish() {
        timer.finish()
        WKInterfaceDevice.current().play(.click)
    }

    private func finishers(_ count: Int) -> String {
        count == 1 ? "1 finisher" : "\(count) finishers"
    }
}
