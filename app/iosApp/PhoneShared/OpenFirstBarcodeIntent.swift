import AppIntents

/// Opens the first barcode at full brightness. The Control Center and lock screen control runs it;
/// it is part of the app too, because it runs there once the app is open.
struct OpenFirstBarcodeIntent: AppIntent {
    static let title: LocalizedStringResource = "Open First Barcode"
    static let description = IntentDescription("Opens your first barcode, ready to scan.")
    static let openAppWhenRun = true

    func perform() async throws -> some IntentResult & OpensIntent {
        // The same link the widgets open; with no barcode yet, the app opens on its list.
        let url = WidgetBarcode.load()?.url ?? URL(string: "bibless://")!
        return .result(opensIntent: OpenURLIntent(url))
    }
}
