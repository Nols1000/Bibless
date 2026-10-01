import Foundation
import SharedLogic
import SwiftUI
import WidgetKit

/// SwiftUI-facing wrapper around the shared Kotlin `BarcodeRepository`.
@MainActor
final class BarcodeStore: ObservableObject {
    @Published private(set) var barcodes: [Barcode] = []
    @Published private(set) var format: BarcodeFormat = .qr
    @Published private(set) var settings = Settings(
        phoneFormat: .qr, watchFormat: .qr, openOnLaunch: .barcodeList, barcodeOrder: nil, defaultBarcodeId: nil, updatedAt: 0
    )

    #if os(watchOS)
    private static let device = Device.watch
    #else
    private static let device = Device.phone
    #endif
    private let repository: BarcodeRepository
    private let watchSync: WatchSync?
    private let isDemo: Bool
    private var observation: KotlinAutoCloseable?

    init() {
        if ProcessInfo.processInfo.arguments.contains(DemoData.shared.LAUNCH_ARGUMENT) {
            // Store screenshots: sample codes kept in memory, nothing saved or synced
            isDemo = true
            repository = BarcodeRepository(store: InMemoryStore(), device: Self.device)
            DemoData.shared.load(repository: repository)
            watchSync = nil
        } else {
            isDemo = false
            repository = BarcodeRepository(store: UserDefaultsStore(), device: Self.device)
            let sync = WatchSync(repository: repository)
            repository.sync = sync
            watchSync = sync
        }
        observation = repository.observe { [weak self] state in
            if Thread.isMainThread {
                MainActor.assumeIsolated { self?.update(state) }
            } else {
                DispatchQueue.main.async { self?.update(state) }
            }
        }
        watchSync?.activate()
    }

    deinit {
        observation?.close()
    }

    func add(name: String, athleteId: String) throws {
        _ = try repository.add(name: name, athleteId: athleteId)
    }

    func delete(_ barcode: Barcode) {
        repository.delete(id: barcode.id)
    }

    /// Reorders the list like `Array.move(fromOffsets:toOffset:)`, for `onMove`.
    func move(fromOffsets source: IndexSet, toOffset destination: Int) {
        var ids = barcodes.map(\.id)
        ids.move(fromOffsets: source, toOffset: destination)
        repository.setOrder(ids: ids)
    }

    /// Makes `barcode` the first one, the one the apps open first.
    func moveToTop(_ barcode: Barcode) {
        repository.moveToTop(id: barcode.id)
    }

    func setDefaultFormat(_ format: BarcodeFormat, for device: Device) {
        repository.setDefaultFormat(device: device, format: format)
    }

    /// Binding for a settings picker that edits the default format of `device`.
    func defaultFormat(for device: Device) -> Binding<BarcodeFormat> {
        Binding(
            get: { self.settings.formatFor(device: device) },
            set: { self.setDefaultFormat($0, for: device) }
        )
    }

    func barcode(id: String) -> Barcode? {
        repository.find(id: id)
    }

    /// The barcode whose detail view is open, so the app can reopen on it; nil once it was deleted.
    var shownBarcodeId: String? {
        get { repository.shownBarcodeId }
        set { repository.shownBarcodeId = newValue }
    }

    /// The barcode to open on launch, on top of the list; see `BarcodeRepository.startBarcodeId`.
    var startBarcodeId: String? {
        repository.startBarcodeId()
    }

    /// Binding for a picker that sets what both apps open on launch.
    var openOnLaunch: Binding<LaunchScreen> {
        Binding(
            get: { self.settings.openOnLaunch },
            set: { self.repository.setOpenOnLaunch(screen: $0) }
        )
    }

    private func update(_ state: BarcodeState) {
        barcodes = state.barcodes
        format = state.format
        settings = state.settings
        // The widgets (home and lock screen, Smart Stack) offer the first barcode; demo data stays out of them.
        let widgetBarcode = barcodes.first.map {
            WidgetBarcode(
                id: $0.id,
                name: $0.name,
                athleteId: $0.athleteId,
                grid: ModuleGrid(format.encode(text: $0.athleteId))
            )
        }
        if !isDemo, WidgetBarcode.save(widgetBarcode) {
            WidgetCenter.shared.reloadAllTimelines()
        }
    }
}

func normalizeAthleteId(_ input: String) -> String? {
    AthleteIdKt.normalizeAthleteId(input: input)
}

extension BarcodeFormat {
    var label: String { self == .qr ? "QR code" : "Barcode" }
}

extension LaunchScreen {
    var label: String { self == .firstBarcode ? "First Barcode" : "Barcode List" }
}

extension Device {
    var label: String { self == .phone ? "iPhone" : "Apple Watch" }
}

extension Barcode: @retroactive Identifiable {}
