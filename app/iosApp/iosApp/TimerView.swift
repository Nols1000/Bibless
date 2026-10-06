import SharedLogic
import SwiftUI

/// How many of the latest finishes stay on screen.
private let recentFinishes = 5

/// The phone stopwatch: start the clock on the start signal, then one big button per finisher.
struct TimerView: View {
    @EnvironmentObject private var timer: TimerStore
    @Environment(\.dismiss) private var dismiss
    @State private var isConfirmingStop = false
    @State private var isShowingSessions = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 16) {
                if timer.isRunning {
                    running
                } else {
                    notStarted
                }
            }
            .padding()
            .navigationTitle("Timer")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
                if timer.isRunning {
                    ToolbarItem(placement: .destructiveAction) {
                        Button("Stop") { isConfirmingStop = true }
                    }
                }
            }
            .alert("Stop the timer?", isPresented: $isConfirmingStop) {
                Button("Stop", role: .destructive) { timer.stop() }
                Button("Keep Timing", role: .cancel) {}
            } message: {
                Text("\(finishers(timer.times.count)) recorded. They stay saved on this phone, but you can't add more finishers to this session.")
            }
        }
        // Stays on while timing; the screen locking mid-run would cost finishes
        .onAppear { UIApplication.shared.isIdleTimerDisabled = timer.isRunning }
        .onChange(of: timer.isRunning) { _, running in UIApplication.shared.isIdleTimerDisabled = running }
        .onDisappear { UIApplication.shared.isIdleTimerDisabled = false }
    }

    private var notStarted: some View {
        VStack(spacing: 16) {
            JoinedRunCard()
            Spacer()
            Text("Tap Start on the start signal, then Finish each time a runner crosses the line.")
                .font(.body)
            Button(action: timer.start) {
                Text("Start")
                    .font(.system(size: 40, weight: .semibold))
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            }
            .buttonStyle(.borderedProminent)
            .buttonBorderShape(.roundedRectangle(radius: 24))
            Button("Recorded Sessions") { isShowingSessions = true }
        }
        .sheet(isPresented: $isShowingSessions) { SessionsView() }
    }

    private var running: some View {
        VStack(spacing: 16) {
            SessionRunLine(run: timer.session?.run)
            // The runners' clock, readable at arm's length
            TimelineView(.periodic(from: .now, by: 0.1)) { _ in
                Text(formatElapsed(timer.elapsedMs ?? 0))
                    .font(.system(size: 64, weight: .medium, design: .monospaced))
                    .accessibilityIdentifier("elapsed")
            }
            Text(finishers(timer.times.count))
                .font(.headline)
            VStack(spacing: 4) {
                ForEach(timer.times.suffix(recentFinishes).reversed(), id: \.eventId) { time in
                    HStack {
                        Text("P\(time.position)")
                        Spacer()
                        Text(formatElapsed(time.elapsedMs)).monospacedDigit()
                    }
                    .font(.title2)
                }
                Spacer()
            }
            .frame(maxHeight: .infinity)
            Button(action: timer.undo) {
                Group {
                    if let last = timer.times.last {
                        Text("Undo P\(last.position) (\(formatElapsed(last.elapsedMs)))")
                    } else {
                        Text("Undo")
                    }
                }
                .frame(maxWidth: .infinity)
            }
            .buttonStyle(.bordered)
            .disabled(timer.times.isEmpty)
            Button(action: timer.finish) {
                Text("Finish")
                    .font(.system(size: 48, weight: .semibold))
                    .frame(maxWidth: .infinity, minHeight: 200)
            }
            .buttonStyle(.borderedProminent)
            .buttonBorderShape(.roundedRectangle(radius: 24))
            .sensoryFeedback(.impact, trigger: timer.times.count)
        }
    }
}

private func finishers(_ count: Int) -> String {
    count == 1 ? "1 finisher" : "\(count) finishers"
}
