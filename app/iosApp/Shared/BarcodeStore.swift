import Foundation
import SharedLogic
import SwiftUI

/// SwiftUI-facing wrapper around the shared Kotlin `BarcodeRepository`.
@MainActor
final class BarcodeStore: ObservableObject {
    @Published private(set) var barcodes: [Barcode] = []
    @Published private(set) var format: BarcodeFormat = .qr
    @Published private(set) var settings = Settings(phoneFormat: .qr, watchFormat: .qr, updatedAt: 0)

    #if os(watchOS)
    private static let device = Device.watch
    #else
    private static let device = Device.phone
    #endif
    private let repository: BarcodeRepository
    private let watchSync: WatchSync?
    private var observation: KotlinAutoCloseable?

    init() {
        if ProcessInfo.processInfo.arguments.contains(DemoData.shared.LAUNCH_ARGUMENT) {
            // Store screenshots: sample codes kept in memory, nothing saved or synced
            repository = BarcodeRepository(store: InMemoryStore(), device: Self.device)
            DemoData.shared.load(repository: repository)
            watchSync = nil
        } else {
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

    private func update(_ state: BarcodeState) {
        barcodes = state.barcodes
        format = state.format
        settings = state.settings
    }
}

func normalizeAthleteId(_ input: String) -> String? {
    AthleteIdKt.normalizeAthleteId(input: input)
}

extension BarcodeFormat {
    var label: String { self == .qr ? "QR code" : "Barcode" }
}

extension Device {
    var label: String { self == .phone ? "iPhone" : "Apple Watch" }
}

extension Barcode: @retroactive Identifiable {}
