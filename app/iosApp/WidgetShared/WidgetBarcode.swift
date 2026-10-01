import Foundation

/// The barcode the widgets offer: the first one in the list. Each app writes it to the app group it
/// shares with its widget, which can't run the shared Kotlin code itself.
struct WidgetBarcode: Codable, Equatable {
    let id: String
    let name: String
    /// What the phone widget shows below the code; the watch widget shows only the name.
    var athleteId: String? = nil
    /// The code in this device's format, for the phone widget to draw.
    var grid: ModuleGrid? = nil

    static let kind = "FirstBarcode"
    private static let appGroup = "group.com.github.nols1000.bibless"
    private static let key = "widgetBarcode"
    private static let scheme = "bibless"

    /// Opens this barcode's detail screen in the app.
    var url: URL {
        var components = URLComponents()
        components.scheme = Self.scheme
        components.host = "barcode"
        components.path = "/\(id)"
        return components.url!
    }

    /// The barcode ID in a URL made by [url], or nil for any other URL.
    static func id(from url: URL) -> String? {
        guard url.scheme == scheme, url.host == "barcode" else { return nil }
        let id = String(url.path.dropFirst())
        return id.isEmpty ? nil : id
    }

    static func load() -> WidgetBarcode? {
        guard let data = UserDefaults(suiteName: appGroup)?.data(forKey: key) else { return nil }
        return try? JSONDecoder().decode(WidgetBarcode.self, from: data)
    }

    /// Stores [barcode] for the widget; true if it differs from what the widget showed so far.
    static func save(_ barcode: WidgetBarcode?) -> Bool {
        guard barcode != load(), let defaults = UserDefaults(suiteName: appGroup) else { return false }
        if let barcode, let data = try? JSONEncoder().encode(barcode) {
            defaults.set(data, forKey: key)
        } else {
            defaults.removeObject(forKey: key)
        }
        return true
    }
}
