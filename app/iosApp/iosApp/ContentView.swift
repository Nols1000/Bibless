import SharedLogic
import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var store: BarcodeStore
    @State private var isAdding = false
    @State private var isShowingSettings = false

    @State private var path: [String] = []

    var body: some View {
        NavigationStack(path: $path) {
            List {
                ForEach(store.barcodes) { barcode in
                    NavigationLink(value: barcode.id) {
                        VStack(alignment: .leading) {
                            Text(barcode.name)
                            Text(barcode.athleteId)
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
                .onDelete { offsets in
                    offsets.map { store.barcodes[$0] }.forEach(store.delete)
                }
            }
            .overlay {
                if store.barcodes.isEmpty {
                    ContentUnavailableView(
                        "No Barcodes",
                        systemImage: "barcode",
                        description: Text("Tap + to add your parkrun ID.")
                    )
                }
            }
            .navigationTitle("Barcodes")
            .navigationDestination(for: String.self) { id in
                BarcodeDetailView(id: id)
            }
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("Settings", systemImage: "gearshape") { isShowingSettings = true }
                }
                ToolbarItem(placement: .primaryAction) {
                    Button("Add Barcode", systemImage: "plus") { isAdding = true }
                }
            }
            .sheet(isPresented: $isAdding) {
                AddBarcodeView()
            }
            .sheet(isPresented: $isShowingSettings) {
                SettingsView()
            }
        }
        // Reopen the barcode that was shown when the app was closed, on top of the list.
        .onAppear {
            if path.isEmpty, let id = store.shownBarcodeId { path = [id] }
        }
        .onChange(of: path) { store.shownBarcodeId = path.last }
    }
}

private struct AddBarcodeView: View {
    @EnvironmentObject private var store: BarcodeStore
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var athleteId = ""

    private var isValid: Bool { normalizeAthleteId(athleteId) != nil }

    var body: some View {
        NavigationStack {
            Form {
                TextField("Name", text: $name)
                    .textContentType(.name)
                Section {
                    TextField("parkrun ID", text: $athleteId, prompt: Text("A1234567"))
                        .textInputAutocapitalization(.characters)
                        .autocorrectionDisabled()
                } footer: {
                    if !athleteId.isEmpty && !isValid {
                        Text("Use the format A1234567").foregroundStyle(.red)
                    }
                }
            }
            .navigationTitle("Add Barcode")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        try? store.add(name: name, athleteId: athleteId)
                        dismiss()
                    }
                    .disabled(!isValid)
                }
            }
        }
        .presentationDetents([.medium])
    }
}

private struct SettingsView: View {
    @EnvironmentObject private var store: BarcodeStore
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                ForEach([Device.phone, Device.watch], id: \.self) { device in
                    Section {
                        Picker("Default Format", selection: store.defaultFormat(for: device)) {
                            ForEach(BarcodeFormat.entries, id: \.self) { format in
                                Text(format.label).tag(format)
                            }
                        }
                        .pickerStyle(.inline)
                        .labelsHidden()
                    } header: {
                        Text(device.label)
                    } footer: {
                        if device == .watch {
                            Text("Used when you open a code on your Apple Watch.")
                        }
                    }
                }
            }
            .navigationTitle("Settings")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}

private struct BarcodeDetailView: View {
    @EnvironmentObject private var store: BarcodeStore
    let id: String

    var body: some View {
        if let barcode = store.barcode(id: id) {
            VStack(spacing: 24) {
                // Black on white regardless of theme: scanners struggle with a code framed by a dark screen.
                VStack(spacing: 0) {
                    BarcodeImageView(text: barcode.athleteId, format: store.format)

                    Text(barcode.athleteId)
                        .font(.title.monospacedDigit())
                        .foregroundStyle(.black)
                        .padding(.bottom, 24)
                }
                .background(.white, in: RoundedRectangle(cornerRadius: 12))

                Spacer()
            }
            .padding()
            .navigationTitle(barcode.name)
            .navigationBarTitleDisplayMode(.inline)
            // Stays on until the volunteer has scanned it; the timeout returns with the list.
            .onAppear { UIApplication.shared.isIdleTimerDisabled = true }
            .onDisappear { UIApplication.shared.isIdleTimerDisabled = false }
            .modifier(FullBrightness())
        } else {
            ContentUnavailableView("Barcode Deleted", systemImage: "trash")
        }
    }
}

#Preview {
    ContentView()
        .environmentObject(BarcodeStore())
}

/// Turns the screen to full brightness while the view is visible and the app is active, so scanners
/// read the code in bright sunlight, and restores the previous brightness afterwards.
private struct FullBrightness: ViewModifier {
    @Environment(\.scenePhase) private var scenePhase
    @State private var previous: CGFloat?

    func body(content: Content) -> some View {
        content
            .onAppear(perform: raise)
            .onDisappear(perform: restore)
            .onChange(of: scenePhase) { _, phase in
                phase == .active ? raise() : restore()
            }
    }

    private func raise() {
        guard previous == nil else { return }
        previous = UIScreen.main.brightness
        UIScreen.main.brightness = 1
    }

    private func restore() {
        guard let previous else { return }
        UIScreen.main.brightness = previous
        self.previous = nil
    }
}
