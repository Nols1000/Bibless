import AppIntents
import SwiftUI
import WidgetKit

/// A button in Control Center, on the lock screen and on the Action button that opens the first
/// barcode in one press.
struct FirstBarcodeControl: ControlWidget {
    static let kind = "com.github.nols1000.bibless.FirstBarcodeControl"

    var body: some ControlWidgetConfiguration {
        StaticControlConfiguration(kind: Self.kind) {
            ControlWidgetButton(action: OpenFirstBarcodeIntent()) {
                Label("Barcode", systemImage: "barcode")
            }
        }
        .displayName("First Barcode")
        .description("Opens your first barcode.")
    }
}
