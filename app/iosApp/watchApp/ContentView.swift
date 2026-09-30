import SharedLogic
import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var store: BarcodeStore
    @State private var isAdding = false

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
                                .font(.footnote)
                                .foregroundStyle(.secondary)
                        }
                    }
                    // Dragging is fiddly on the wrist, so a swipe moves a barcode to the top instead.
                    .swipeActions(edge: .leading) {
                        if barcode.id != store.barcodes.first?.id {
                            Button("Move to Top", systemImage: "arrow.up.to.line") {
                                store.moveToTop(barcode)
                            }
                            .tint(.accentColor)
                        }
                    }
                    // Declared here too, since custom swipe actions replace the ones onDelete adds.
                    .swipeActions(edge: .trailing) {
                        Button("Delete", systemImage: "trash", role: .destructive) {
                            store.delete(barcode)
                        }
                    }
                }
                Button {
                    isAdding = true
                } label: {
                    Label("Add", systemImage: "plus")
                }
                NavigationLink {
                    SettingsView()
                } label: {
                    Label("Settings", systemImage: "gearshape")
                }
            }
            .navigationTitle("Barcodes")
            .navigationDestination(for: String.self) { id in
                BarcodeDetailView(id: id)
            }
            .sheet(isPresented: $isAdding) {
                NavigationStack {
                    AddBarcodeView()
                }
            }
        }
        // Open the start barcode (last shown, first or only one) on top of the list, once per launch.
        .onAppear {
            guard !didStart else { return }
            didStart = true
            if let id = store.startBarcodeId { path = [id] }
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
        Form {
            TextField("Name", text: $name)
            TextField("parkrun ID", text: $athleteId, prompt: Text("A1234567"))
                .textInputAutocapitalization(.characters)
            if !athleteId.isEmpty && !isValid {
                Text("Use the format A1234567")
                    .font(.footnote)
                    .foregroundStyle(.red)
            }
            Button("Save") {
                try? store.add(name: name, athleteId: athleteId)
                dismiss()
            }
            .disabled(!isValid)
        }
        .navigationTitle("Add")
    }
}

private struct SettingsView: View {
    @EnvironmentObject private var store: BarcodeStore

    var body: some View {
        Form {
            // First, since skipping the list is what matters most on the wrist
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
                Text("Swipe right on a barcode in the list to move it to the top.")
            }
            Picker("Default Format", selection: store.defaultFormat(for: .watch)) {
                ForEach(BarcodeFormat.entries, id: \.self) { format in
                    Text(format.label).tag(format)
                }
            }
            .pickerStyle(.inline)
        }
        .navigationTitle("Settings")
    }
}

/// Shows the code alone on a white screen, as large as fits, so scanners pick it up easily.
private struct BarcodeDetailView: View {
    @EnvironmentObject private var store: BarcodeStore
    @Environment(\.dismiss) private var dismiss
    let id: String

    var body: some View {
        if let barcode = store.barcode(id: id) {
            // Centered on the whole display, not the safe area, so the clock doesn't push it down.
            ZStack {
                Color.white
                // Small inset keeps the code clear of the display's rounded corners.
                BarcodeImageView(text: barcode.athleteId, format: store.format)
                    .padding(6)
            }
            // Labels sit in the white space above and below the code without shifting it off center.
            .overlay(alignment: .top) {
                caption(barcode.name)
                    .padding(.top, 6)
            }
            .overlay(alignment: .bottom) {
                caption(barcode.athleteId)
                    .monospacedDigit()
                    .padding(.bottom, 6)
            }
            .ignoresSafeArea()
            .toolbar(.hidden, for: .navigationBar)
            .contentShape(Rectangle())
            .onTapGesture { dismiss() }
            .accessibilityHint("Tap to close")
        } else {
            Text("Barcode deleted")
        }
    }

    private func caption(_ text: String) -> some View {
        Text(text)
            .font(.footnote)
            .foregroundStyle(.black)
            .lineLimit(1)
            .padding(.horizontal, 16)
    }
}

#Preview {
    ContentView()
        .environmentObject(BarcodeStore())
}
