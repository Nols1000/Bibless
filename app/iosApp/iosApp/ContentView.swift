import SharedLogic
import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var store: BarcodeStore
    @State private var isAdding = false
    @State private var isShowingSettings = false

    @State private var path: [String] = []
    @State private var didStart = false

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
                    // Lets UI tests open the barcode by its link
                    .accessibilityIdentifier(barcode.id)
                }
                .onDelete { offsets in
                    offsets.map { store.barcodes[$0] }.forEach(store.delete)
                }
                .onMove(perform: store.move)
            }
            .overlay {
                if store.barcodes.isEmpty {
                    ContentUnavailableView(
                        "No Barcodes",
                        systemImage: "barcode",
                        description: Text("Tap + to add your athlete ID.")
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
                // Shows the handles for dragging barcodes into a different order
                if store.barcodes.count > 1 {
                    ToolbarItem(placement: .topBarTrailing) {
                        EditButton()
                    }
                }
            }
            .sheet(isPresented: $isAdding) {
                AddBarcodeView()
            }
            .sheet(isPresented: $isShowingSettings) {
                SettingsView()
            }
        }
        // Open the start barcode (last shown, first or only one) on top of the list, once per launch.
        .onAppear {
            guard !didStart else { return }
            didStart = true
            if let id = store.startBarcodeId { path = [id] }
        }
        .onChange(of: path) { store.shownBarcodeId = path.last }
        // The widget opens its barcode on top of the list, over whatever was open.
        .onOpenURL { url in
            guard let id = WidgetBarcode.id(from: url), store.barcode(id: id) != nil else { return }
            didStart = true
            isAdding = false // the sheets would cover the code
            isShowingSettings = false
            path = [id]
            // Also when the path already was [id], where onChange(of: path) doesn't fire
            store.shownBarcodeId = id
        }
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
                    TextField("Athlete ID", text: $athleteId, prompt: Text("A1234567"))
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
                Section {
                    Picker("Open on Launch", selection: store.openOnLaunch) {
                        ForEach(LaunchScreen.entries, id: \.self) { screen in
                            Text(screen.label).tag(screen)
                        }
                    }
                    .pickerStyle(.inline)
                    .labelsHidden()
                } header: {
                    Text("Open on Launch")
                } footer: {
                    if store.settings.openOnLaunch == .barcodeList && store.barcodes.count == 1 {
                        Text("Your only barcode opens either way.")
                    } else {
                        Text("Tap Edit in the list, then drag a barcode to change which comes first.")
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
