import Foundation

/// The barcode the watch widget offers: the first one in the list. The watch app writes it to the
/// app group it shares with the widget, which can't run the shared Kotlin code itself.
struct WidgetBarcode: Codable, Equatable {
    let id: String
    let name: String

    static let kind = "FirstBarcode"
    private static let appGroup = "group.com.github.nols1000.bibless"
    private static let key = "widgetBarcode"
    private static let scheme = "bibless"

    /// Opens this barcode's detail screen in the watch app.
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
