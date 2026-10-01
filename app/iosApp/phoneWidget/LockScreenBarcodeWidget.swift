import SwiftUI
import WidgetKit

/// The first barcode on the lock screen. The lock screen recolors widgets, which scanners can't
/// read, so it shows the name and a barcode symbol; tapping it opens the code after Face ID.
struct LockScreenBarcodeWidget: Widget {
    static let kind = "FirstBarcodeLockScreen"

    var body: some WidgetConfiguration {
        StaticConfiguration(kind: Self.kind, provider: FirstBarcodeProvider()) { entry in
            LockScreenBarcodeView(barcode: entry.barcode)
                .containerBackground(.clear, for: .widget)
        }
        .configurationDisplayName("First Barcode")
        .description("Opens your first barcode.")
        .supportedFamilies([.accessoryRectangular, .accessoryCircular])
    }
}

struct LockScreenBarcodeView: View {
    let barcode: WidgetBarcode?
    @Environment(\.widgetFamily) private var family

    var body: some View {
        switch family {
        case .accessoryCircular:
            ZStack {
                AccessoryWidgetBackground()
                Image(systemName: "barcode")
                    .font(.title2)
                    .widgetAccentable()
            }
            .accessibilityLabel(barcode.map { "Barcode \($0.name)" } ?? "Add a barcode in Bibless")
            .widgetURL(barcode?.url)
        default:
            if let barcode {
                HStack(spacing: 8) {
                    Image(systemName: "barcode")
                        .font(.title2)
                        .widgetAccentable()
                    VStack(alignment: .leading) {
                        Text("Bibless")
                            .font(.caption2)
                            .foregroundStyle(.secondary)
                        Text(barcode.name)
                            .font(.headline)
                            .lineLimit(2)
                    }
                    Spacer(minLength: 0)
                }
                .widgetURL(barcode.url)
            } else {
                Text("Add a barcode in Bibless")
                    .font(.footnote)
            }
        }
    }
}

#Preview(as: .accessoryRectangular) {
    LockScreenBarcodeWidget()
} timeline: {
    FirstBarcodeEntry(date: .now, barcode: FirstBarcodeProvider.sample)
    FirstBarcodeEntry(date: .now, barcode: nil)
}

#Preview(as: .accessoryCircular) {
    LockScreenBarcodeWidget()
} timeline: {
    FirstBarcodeEntry(date: .now, barcode: FirstBarcodeProvider.sample)
}
