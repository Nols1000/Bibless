import RelevanceKit
import SwiftUI
import WidgetKit

/// Puts the first barcode in the Smart Stack on parkrun morning and during workouts, so it's one tap
/// away at the finish. Shows the name and a barcode symbol; the code itself needs the full screen.
@main
struct FirstBarcodeWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: WidgetBarcode.kind, provider: FirstBarcodeProvider()) { entry in
            FirstBarcodeView(barcode: entry.barcode)
                .containerBackground(.fill.tertiary, for: .widget)
        }
        .configurationDisplayName("First Barcode")
        .description("Opens your first barcode.")
        // Rectangular only: round and corner complications have no room for a name.
        .supportedFamilies([.accessoryRectangular])
    }
}

struct FirstBarcodeEntry: TimelineEntry {
    let date: Date
    let barcode: WidgetBarcode?
}

struct FirstBarcodeProvider: TimelineProvider {
    private static let sample = WidgetBarcode(id: "", name: "Me")

    func placeholder(in context: Context) -> FirstBarcodeEntry {
        FirstBarcodeEntry(date: .now, barcode: Self.sample)
    }

    func getSnapshot(in context: Context, completion: @escaping (FirstBarcodeEntry) -> Void) {
        let barcode = WidgetBarcode.load() ?? (context.isPreview ? Self.sample : nil)
        completion(FirstBarcodeEntry(date: .now, barcode: barcode))
    }

    /// A single entry; the watch app reloads the widget whenever the first barcode changes. It also
    /// reloads after each parkrun morning, which moves the mornings in [relevance] on by a week.
    func getTimeline(in context: Context, completion: @escaping (Timeline<FirstBarcodeEntry>) -> Void) {
        let entry = FirstBarcodeEntry(date: .now, barcode: WidgetBarcode.load())
        let policy = ParkrunMorning.upcoming(count: 1).first.map { TimelineReloadPolicy.after($0.end) } ?? .never
        completion(Timeline(entries: [entry], policy: policy))
    }

    /// Hints the system to put the widget on top on parkrun mornings and while a workout runs;
    /// the system still decides what shows first.
    func relevance() async -> WidgetRelevance<Void> {
        guard WidgetBarcode.load() != nil else { return WidgetRelevance([]) }
        var contexts = ParkrunMorning.upcoming().map { morning in
            if #available(watchOS 26.0, *) {
                RelevantContext.date(range: morning.start...morning.end, kind: .scheduled)
            } else {
                RelevantContext.date(from: morning.start, to: morning.end)
            }
        }
        contexts.append(.fitness(.workoutActive))
        return WidgetRelevance(contexts.map { WidgetRelevanceAttribute(context: $0) })
    }
}

/// Saturday mornings, when most parkruns start at 9:00 and the last finishers are in by 10:30.
enum ParkrunMorning {
    /// The next [count] Saturdays from 9:00 to 10:30 local time, starting with today's if it isn't over.
    static func upcoming(from now: Date = .now, count: Int = 8, calendar: Calendar = .current) -> [DateInterval] {
        var mornings: [DateInterval] = []
        var day = calendar.startOfDay(for: now)
        while mornings.count < count {
            if calendar.component(.weekday, from: day) == 7,
               let start = calendar.date(bySettingHour: 9, minute: 0, second: 0, of: day),
               let end = calendar.date(bySettingHour: 10, minute: 30, second: 0, of: day),
               end > now {
                mornings.append(DateInterval(start: start, end: end))
            }
            guard let next = calendar.date(byAdding: .day, value: 1, to: day) else { break }
            day = next
        }
        return mornings
    }
}

struct FirstBarcodeView: View {
    let barcode: WidgetBarcode?

    var body: some View {
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

#Preview(as: .accessoryRectangular) {
    FirstBarcodeWidget()
} timeline: {
    FirstBarcodeEntry(date: .now, barcode: WidgetBarcode(id: "1", name: "Me"))
    FirstBarcodeEntry(date: .now, barcode: nil)
}
