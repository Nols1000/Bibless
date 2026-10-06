import SharedLogic
import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var store: BarcodeStore
    @State private var isAdding = false

    @State private var path: [String] = []
    @State private var didStart = false
    /// Counts widget taps, so each one starts a fresh pager on the widget's barcode.
    @State private var widgetOpens = 0

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
                    // The path keeps the barcode a pager was opened on, not the one paged to, so
                    // the widget's barcode can already be in it; start over on it all the same.
                    .id("\(id)#\(widgetOpens)")
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
        // Tapping the Smart Stack widget opens its barcode, from which the others are a swipe away.
        .onOpenURL { url in
            guard let id = WidgetBarcode.id(from: url), store.barcode(id: id) != nil else { return }
            didStart = true
            isAdding = false // the add sheet would cover the code
            widgetOpens += 1
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
        Form {
            TextField("Name", text: $name)
            TextField("Athlete ID", text: $athleteId, prompt: Text("A1234567"))
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

/// Shows one page per barcode, starting on the one opened; swiping up or turning the crown moves to
/// the next, so runners scanning for several people needn't go back to the list. Swiping sideways
/// switches between the formats, for when the scanner reads only the other one.
private struct BarcodeDetailView: View {
    @EnvironmentObject private var store: BarcodeStore
    @State private var shownId: String?
    /// The format swiped to, kept while paging through the barcodes but not saved as the default.
    @State private var swipedFormat: BarcodeFormat?

    init(id: String) {
        _shownId = State(initialValue: id)
    }

    private var format: Binding<BarcodeFormat?> {
        Binding(
            get: { swipedFormat ?? store.format },
            // Mid-swipe the scroll position can briefly have no page
            set: { if let format = $0 { swipedFormat = format } }
        )
    }

    var body: some View {
        if let shownId, store.barcode(id: shownId) != nil {
            // A paging scroll view rather than a paged TabView, whose clock bar covers the name.
            ScrollView {
                LazyVStack(spacing: 0) {
                    ForEach(store.barcodes) { barcode in
                        FormatPager(barcode: barcode, format: format)
                            .containerRelativeFrame([.horizontal, .vertical])
                    }
                }
                .scrollTargetLayout()
            }
            .scrollTargetBehavior(.paging)
            .scrollPosition(id: $shownId)
            .scrollIndicators(.hidden)
            // With a single barcode there's nothing to page to, so the screen stays as it was.
            .scrollDisabled(store.barcodes.count == 1)
            .overlay(alignment: .trailing) {
                if store.barcodes.count > 1 {
                    PageIndicator(count: store.barcodes.count, current: store.barcodes.firstIndex { $0.id == shownId })
                        .accessibilityValue(shownIndex.map { "Barcode \($0 + 1) of \(store.barcodes.count)" } ?? "")
                }
            }
            .ignoresSafeArea()
            .toolbar(.hidden, for: .navigationBar)
            .onChange(of: shownId) { store.shownBarcodeId = shownId }
            // Changing the default switches the code, as before.
            .onChange(of: store.format) { swipedFormat = nil }
        } else {
            Text("Barcode deleted")
        }
    }

    private var shownIndex: Int? { store.barcodes.firstIndex { $0.id == shownId } }
}

/// The code of one barcode, a page per format; every page follows `format`, so the next barcode
/// comes up in the format the scanner just read.
private struct FormatPager: View {
    @Environment(\.dismiss) private var dismiss
    let barcode: Barcode
    @Binding var format: BarcodeFormat?

    var body: some View {
        ScrollView(.horizontal) {
            LazyHStack(spacing: 0) {
                ForEach(BarcodeFormat.entries, id: \.self) { format in
                    BarcodePage(barcode: barcode, format: format)
                        .containerRelativeFrame([.horizontal, .vertical])
                }
            }
            .scrollTargetLayout()
        }
        .scrollTargetBehavior(.paging)
        .scrollPosition(id: $format)
        .scrollIndicators(.hidden)
        .background(.white)
        .overlay(alignment: .bottom) {
            PageIndicator(count: BarcodeFormat.entries.count, current: format.flatMap { BarcodeFormat.entries.firstIndex(of: $0) }, axis: .horizontal)
                .accessibilityValue(format?.label ?? "")
        }
        .contentShape(Rectangle())
        .onTapGesture { dismiss() }
        .accessibilityHint("Tap to close")
    }
}

/// A dot per page, like the system's page indicators: barcodes down the edge by the crown
/// (`.vertical`), formats along the bottom (`.horizontal`).
private struct PageIndicator: View {
    let count: Int
    let current: Int?
    var axis: Axis = .vertical

    var body: some View {
        let layout = axis == .vertical ? AnyLayout(VStackLayout(spacing: 4)) : AnyLayout(HStackLayout(spacing: 4))
        layout {
            ForEach(0..<count, id: \.self) { index in
                Circle()
                    .fill(index == current ? Color.black : Color.gray.opacity(0.5))
                    .frame(width: 6, height: 6)
            }
        }
        .padding(axis == .vertical ? .trailing : .bottom, 3)
        .accessibilityElement()
    }
}

/// Shows the code alone on a white screen, as large as fits, so scanners pick it up easily.
private struct BarcodePage: View {
    let barcode: Barcode
    let format: BarcodeFormat

    var body: some View {
        // Centered on the whole display, not the safe area, so the clock doesn't push it down.
        ZStack {
            Color.white
            // The labels sit right against the code, above and below it, whatever its shape.
            VStack(spacing: 2) {
                caption(barcode.name)
                // Small inset keeps the code clear of the display's rounded corners.
                BarcodeImageView(text: barcode.athleteId, format: format)
                    .padding(.horizontal, 6)
                    .layoutPriority(1)
                caption(barcode.athleteId)
                    .monospacedDigit()
            }
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
