import SwiftUI
import WidgetKit

/// Everything the phone offers outside the app: the home screen and lock screen widgets, and the
/// Control Center control.
@main
struct PhoneWidgets: WidgetBundle {
    var body: some Widget {
        FirstBarcodeWidget()
        LockScreenBarcodeWidget()
        FirstBarcodeControl()
    }
}
