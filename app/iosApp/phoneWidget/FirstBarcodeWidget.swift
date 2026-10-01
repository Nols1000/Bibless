import SwiftUI
import WidgetKit

/// Puts the first barcode on the home screen, ready to scan without opening the app. Tapping it
/// opens the barcode at full brightness.
@main
struct FirstBarcodeWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: WidgetBarcode.kind, provider: FirstBarcodeProvider()) { entry in
            FirstBarcodeView(barcode: entry.barcode)
                // White in every appearance, like the code in the app, so scanners find it easily
                .containerBackground(.white, for: .widget)
        }
        .configurationDisplayName("First Barcode")
        .description("Your first barcode, ready to scan.")
        .supportedFamilies([.systemSmall, .systemMedium])
        // Tinted and clear home screens would recolor the code; scanners need black on white.
        .containerBackgroundRemovable(false)
    }
}

struct FirstBarcodeEntry: TimelineEntry {
    let date: Date
    let barcode: WidgetBarcode?
}

struct FirstBarcodeProvider: TimelineProvider {
    static let sample = WidgetBarcode(
        id: "",
        name: "Me",
        athleteId: "A1234567",
        // A made-up code for the widget gallery, not anyone's athlete ID
        grid: ModuleGrid(width: 21, height: 21, bits: String(repeating: "1101001", count: 63))
    )

    func placeholder(in context: Context) -> FirstBarcodeEntry {
        FirstBarcodeEntry(date: .now, barcode: Self.sample)
    }

    func getSnapshot(in context: Context, completion: @escaping (FirstBarcodeEntry) -> Void) {
        let barcode = WidgetBarcode.load() ?? (context.isPreview ? Self.sample : nil)
        completion(FirstBarcodeEntry(date: .now, barcode: barcode))
    }

    /// A single entry; the app reloads the widget whenever the first barcode changes.
    func getTimeline(in context: Context, completion: @escaping (Timeline<FirstBarcodeEntry>) -> Void) {
        completion(Timeline(entries: [FirstBarcodeEntry(date: .now, barcode: WidgetBarcode.load())], policy: .never))
    }
}

struct FirstBarcodeView: View {
    let barcode: WidgetBarcode?

    var body: some View {
        if let barcode, let grid = barcode.grid {
            VStack(spacing: 4) {
                Line(text: barcode.name).fontWeight(.medium)
                ModuleGridView(grid: grid)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .accessibilityLabel("Barcode \(barcode.athleteId ?? "")")
                if let athleteId = barcode.athleteId {
                    Line(text: athleteId).monospacedDigit()
                }
            }
            .widgetURL(barcode.url)
        } else {
            // No barcode yet, or one saved by an app version that didn't store its code
            Line(text: "Add a barcode in Bibless")
                .multilineTextAlignment(.center)
        }
    }

    private struct Line: View {
        let text: String

        var body: some View {
            Text(text)
                .font(.subheadline)
                .foregroundStyle(.black)
                .lineLimit(1)
        }
    }
}

#Preview(as: .systemSmall) {
    FirstBarcodeWidget()
} timeline: {
    FirstBarcodeEntry(date: .now, barcode: FirstBarcodeProvider.sample)
    FirstBarcodeEntry(date: .now, barcode: nil)
}
