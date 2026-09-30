import XCTest

/// Captures the Apple Watch screenshots. Run through fastlane: `bundle exec fastlane ios screenshots`.
@MainActor
final class ScreenshotTests: XCTestCase {
    override func setUp() {
        continueAfterFailure = false
    }

    /// The file names set the store order, as on the phones: the code in both formats, the list,
    /// adding one, then the launch setting.
    func testScreenshots() {
        let app = XCUIApplication()
        setupSnapshot(app)
        app.launchArguments += ["-demoData"]
        app.launch()

        XCTAssertTrue(app.staticTexts["Me"].waitForExistence(timeout: 10))
        snapshot("03-list")

        app.staticTexts["Me"].tap()
        XCTAssertTrue(app.staticTexts["A0123456"].waitForExistence(timeout: 5))
        snapshot("01-qr")

        // The code closes on tap
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        let add = app.buttons["Add"]
        scroll(app, to: add)
        add.tap()
        // DemoData.newBarcode; typed but never saved
        enter("Alex", into: app.textFields["Name"], of: app)
        // The ID field is labelled by its placeholder
        enter("A0975310", into: app.textFields["A1234567"], of: app)
        XCTAssertTrue(app.buttons["Save"].isEnabled)
        snapshot("04-add")
        app.buttons["Close"].firstMatch.tap()
        let settings = app.buttons["Settings"]
        scroll(app, to: settings)
        settings.tap()
        // Open the first barcode on launch; the setting comes first, so it shows with its header
        let option = app.buttons["First Barcode"]
        XCTAssertTrue(option.waitForExistence(timeout: 5))
        option.tap()
        XCTAssertTrue(app.staticTexts["Open on Launch"].isHittable)
        snapshot("05-settings")

        // The other watch format, further down the settings
        let barcode = app.buttons["Barcode"]
        scroll(app, to: barcode)
        barcode.tap()
        app.navigationBars.buttons.firstMatch.tap()
        let me = app.staticTexts["Me"]
        scroll(app, to: me, direction: .down)
        me.tap()
        XCTAssertTrue(app.staticTexts["A0123456"].waitForExistence(timeout: 5))
        snapshot("02-barcode")
    }

    /// Opens the system text input for `field` and types `text` into it.
    private func enter(_ text: String, into field: XCUIElement, of app: XCUIApplication) {
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        field.tap()
        let done = app.buttons["Done"]
        XCTAssertTrue(done.waitForExistence(timeout: 5))
        app.typeText(text)
        done.tap()
        XCTAssertTrue(app.textFields[text].waitForExistence(timeout: 5))
    }

    /// Scrolls the list towards its end (or with `.down`, its start) until `element` can be tapped;
    /// watch lists load their rows lazily while scrolling.
    private func scroll(_ app: XCUIApplication, to element: XCUIElement, direction: Direction = .up) {
        for _ in 0..<10 where !element.isHittable {
            direction == .up ? app.swipeUp() : app.swipeDown()
        }
        XCTAssertTrue(element.isHittable)
    }

    private enum Direction { case up, down }
}
