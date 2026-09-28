import SharedLogic
import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var store: BarcodeStore
    @State private var isAdding = false
    @State private var isShowingSettings = false

    var body: some View {
        NavigationStack {
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
                BarcodeImageView(text: barcode.athleteId, format: store.format)
                    .clipShape(RoundedRectangle(cornerRadius: 12))

                Text(barcode.athleteId)
                    .font(.title.monospacedDigit())

                Spacer()
            }
            .padding()
            .navigationTitle(barcode.name)
            .navigationBarTitleDisplayMode(.inline)
        } else {
            ContentUnavailableView("Barcode Deleted", systemImage: "trash")
        }
    }
}

#Preview {
    ContentView()
        .environmentObject(BarcodeStore())
}
