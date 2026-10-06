import SharedLogic
import SwiftUI
import VisionKit

/// How many of the latest finishers stay on screen.
private let recentRecords = 5

/// The finish scanner: the camera pairs each runner's barcode with their finish token.
struct ScannerView: View {
    @EnvironmentObject private var scanner: ScannerStore
    @Environment(\.dismiss) private var dismiss
    @State private var isConfirmingStop = false
    @State private var isTyping = false
    @State private var typedCode = ""
    @State private var isAddingUnknown = false
    @State private var isShowingSessions = false
    @State private var unknownName = ""
    @State private var chosenMode: TimingMode = .tokens
    @EnvironmentObject private var runs: RunStore

    /// A joined run decides the mode; without one the volunteer picks it.
    private var mode: TimingMode { runs.joinedRun?.format ?? chosenMode }

    var body: some View {
        NavigationStack {
            VStack(spacing: 12) {
                if let session = scanner.session {
                    scanning(session)
                } else {
                    notStarted
                }
            }
            .padding()
            .navigationTitle("Scanner")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Close") { dismiss() }
                }
                if scanner.isRunning {
                    ToolbarItem(placement: .destructiveAction) {
                        Button("Stop") { isConfirmingStop = true }
                    }
                }
            }
            .alert("Stop scanning?", isPresented: $isConfirmingStop) {
                Button("Stop", role: .destructive) { scanner.stop() }
                Button("Keep Scanning", role: .cancel) {}
            } message: {
                Text("\(finishers(scanner.records.count)) recorded. They stay saved on this phone, but you can't add more to this session.")
            }
            .alert("Type a Code", isPresented: $isTyping) {
                TextField("A1234567 or P0042", text: $typedCode)
                    .textInputAutocapitalization(.characters)
                    .autocorrectionDisabled()
                Button("Add") {
                    scanner.enter(typedCode)
                    typedCode = ""
                }
                Button("Cancel", role: .cancel) { typedCode = "" }
            } message: {
                Text("An athlete ID or finish token that won't scan.")
            }
            .alert(unknownTitle, isPresented: $isAddingUnknown) {
                TextField("Name (optional)", text: $unknownName)
                    .textInputAutocapitalization(.words)
                Button("Add") {
                    scanner.addUnknown(name: unknownName)
                    unknownName = ""
                }
                Button("Cancel", role: .cancel) { unknownName = "" }
            } message: {
                Text("They get a position; the organiser can add their details later.")
            }
        }
        // Stays on while scanning; the screen locking would stop the camera mid-run
        .onAppear { UIApplication.shared.isIdleTimerDisabled = scanner.isRunning }
        .onChange(of: scanner.isRunning) { _, running in UIApplication.shared.isIdleTimerDisabled = running }
        .onDisappear { UIApplication.shared.isIdleTimerDisabled = false }
    }

    private var notStarted: some View {
        VStack(spacing: 16) {
            JoinedRunCard()
            if runs.joinedRun == nil {
                Picker("Timing", selection: $chosenMode) {
                    Text(TimingMode.tokens.label()).tag(TimingMode.tokens)
                    Text(TimingMode.scanOrder.label()).tag(TimingMode.scanOrder)
                }
                .pickerStyle(.segmented)
            }
            Spacer()
            Text(mode == .scanOrder
                 ? "Scan runners in the order they finish; each takes the next position. A missed or swapped scan moves everyone behind it."
                 : "Scan each runner's barcode and their finish token, in either order. Tap Start when the first runners come in.")
            Button {
                scanner.start(mode: mode)
            } label: {
                Text("Start")
                    .font(.system(size: 40, weight: .semibold))
                    .frame(maxWidth: .infinity, minHeight: 120)
            }
            .buttonStyle(.borderedProminent)
            .buttonBorderShape(.roundedRectangle(radius: 24))
            Spacer()
            Button("Recorded Sessions") { isShowingSessions = true }
        }
        .sheet(isPresented: $isShowingSessions) { SessionsView() }
    }

    private func scanning(_ session: ScannerSession) -> some View {
        VStack(spacing: 12) {
            SessionRunLine(run: session.run)
            CameraScannerView(onCode: scanner.scan)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(.black)
                .clipShape(RoundedRectangle(cornerRadius: 16))
            if scanner.isScanOrder {
                Text("Next: P\(session.nextPosition)")
                    .font(.system(size: 44, weight: .semibold))
                    .accessibilityIdentifier("nextPosition")
                Text("Scan runners in the order they finish. A missed or swapped scan moves everyone behind it.")
                    .font(.footnote)
            } else {
                Text(session.nextStep())
                    .font(.headline)
                    .accessibilityIdentifier("nextStep")
            }
            if let message = scanner.message {
                Text(message.text)
                    .foregroundStyle(message.kind == .error ? .red : .primary)
                    .accessibilityIdentifier("scanMessage")
            }
            Text(finishers(scanner.records.count))
                .font(.subheadline)
            VStack(spacing: 2) {
                ForEach(scanner.records.suffix(recentRecords).reversed(), id: \.eventId) { record in
                    HStack {
                        Text("P\(record.position?.intValue ?? 0)")
                        Spacer()
                        Text(record.label())
                    }
                    .font(.headline)
                }
            }
            HStack {
                Button(action: scanner.undo) {
                    Group {
                        if scanner.hasPending {
                            Text("Clear Scan")
                        } else if let last = scanner.records.last {
                            Text("Undo P\(last.position?.intValue ?? 0)")
                        } else {
                            Text("Undo")
                        }
                    }
                    .frame(maxWidth: .infinity)
                }
                .disabled(!scanner.hasPending && scanner.records.isEmpty)
                // With tokens, an unknown finisher takes the token scanned last
                Button {
                    isAddingUnknown = true
                } label: {
                    Text("No Barcode").frame(maxWidth: .infinity)
                }
                .disabled(!scanner.isScanOrder && session.pendingPosition == nil)
                Button {
                    isTyping = true
                } label: {
                    Text("Type a Code").frame(maxWidth: .infinity)
                }
            }
            .buttonStyle(.bordered)
        }
    }
}

/// The live camera, reporting every Code 128 and QR code it recognises.
struct CameraScannerView: View {
    let onCode: (String) -> Void

    var body: some View {
        if DataScannerViewController.isSupported && DataScannerViewController.isAvailable {
            DataScanner(onCode: onCode)
        } else {
            Text(DataScannerViewController.isSupported
                 ? "Allow camera access in Settings to scan, or type the codes in."
                 : "This device can't scan codes. Type them in instead.")
                .multilineTextAlignment(.center)
                .foregroundStyle(.white)
                .padding()
        }
    }
}

private struct DataScanner: UIViewControllerRepresentable {
    let onCode: (String) -> Void

    func makeUIViewController(context: Context) -> DataScannerViewController {
        let controller = DataScannerViewController(
            recognizedDataTypes: [.barcode(symbologies: [.code128, .qr])],
            qualityLevel: .fast,
            recognizesMultipleItems: true,
            isHighFrameRateTrackingEnabled: false,
            isHighlightingEnabled: true
        )
        controller.delegate = context.coordinator
        try? controller.startScanning()
        return controller
    }

    func updateUIViewController(_ controller: DataScannerViewController, context: Context) {
        context.coordinator.onCode = onCode
    }

    static func dismantleUIViewController(_ controller: DataScannerViewController, coordinator: Coordinator) {
        controller.stopScanning()
    }

    func makeCoordinator() -> Coordinator { Coordinator(onCode: onCode) }

    final class Coordinator: NSObject, DataScannerViewControllerDelegate {
        var onCode: (String) -> Void

        init(onCode: @escaping (String) -> Void) { self.onCode = onCode }

        func dataScanner(_ dataScanner: DataScannerViewController, didAdd addedItems: [RecognizedItem], allItems: [RecognizedItem]) {
            for item in addedItems {
                if case .barcode(let barcode) = item, let text = barcode.payloadStringValue {
                    onCode(text)
                }
            }
        }
    }
}

extension ScannerView {
    private var unknownTitle: String {
        let position = scanner.isScanOrder ? scanner.session?.nextPosition : scanner.session?.pendingPosition?.int32Value
        return position.map { "Finisher P\($0) Without a Barcode" } ?? "Finisher Without a Barcode"
    }
}

private func finishers(_ count: Int) -> String {
    count == 1 ? "1 finisher" : "\(count) finishers"
}
