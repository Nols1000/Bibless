import AppIntents

/// A barcode as Siri, Shortcuts and Spotlight see it: its name. The app keeps the list in the app
/// group (see `WidgetBarcode.saveAll`), so the intents don't need the shared Kotlin code.
struct BarcodeEntity: AppEntity {
    static let typeDisplayRepresentation: TypeDisplayRepresentation = "Barcode"
    static let defaultQuery = BarcodeQuery()

    let id: String
    let name: String

    var displayRepresentation: DisplayRepresentation { DisplayRepresentation(title: "\(name)") }

    init(_ barcode: WidgetBarcode) {
        id = barcode.id
        name = barcode.name
    }
}

struct BarcodeQuery: EntityStringQuery {
    func entities(for identifiers: [String]) async throws -> [BarcodeEntity] {
        all().filter { identifiers.contains($0.id) }
    }

    /// For "Show Sam in Bibless" and the like.
    func entities(matching string: String) async throws -> [BarcodeEntity] {
        all().filter { $0.name.localizedCaseInsensitiveContains(string) }
    }

    func suggestedEntities() async throws -> [BarcodeEntity] {
        all()
    }

    private func all() -> [BarcodeEntity] {
        WidgetBarcode.loadAll().map(BarcodeEntity.init)
    }
}

/// Opens one barcode at full brightness.
struct OpenBarcodeIntent: AppIntent {
    static let title: LocalizedStringResource = "Open Barcode"
    static let description = IntentDescription("Opens a barcode, ready to scan.")
    static let openAppWhenRun = true

    @Parameter(title: "Barcode")
    var barcode: BarcodeEntity

    static var parameterSummary: some ParameterSummary {
        Summary("Open \(\.$barcode)")
    }

    func perform() async throws -> some IntentResult & OpensIntent {
        // The same link the widgets open
        .result(opensIntent: OpenURLIntent(WidgetBarcode(id: barcode.id, name: barcode.name).url))
    }
}

/// What Siri, Spotlight and the Action button offer without setting anything up.
struct BiblessShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: OpenFirstBarcodeIntent(),
            phrases: [
                "Show my barcode in \(.applicationName)",
                "Show my \(.applicationName) barcode",
                "Open \(.applicationName) barcode",
            ],
            shortTitle: "First Barcode",
            systemImageName: "barcode"
        )
        AppShortcut(
            intent: OpenBarcodeIntent(),
            phrases: [
                "Show \(\.$barcode) in \(.applicationName)",
                "Show \(\.$barcode)'s barcode in \(.applicationName)",
            ],
            shortTitle: "Open Barcode",
            systemImageName: "barcode.viewfinder"
        )
    }
}
