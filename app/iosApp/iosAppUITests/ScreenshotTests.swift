import XCTest

/// Captures the App Store screenshots. Run through fastlane: `bundle exec fastlane ios screenshots`.
@MainActor
final class ScreenshotTests: XCTestCase {
    override func setUp() {
        continueAfterFailure = false
    }

    override func tearDown() {
        XCUIDevice.shared.appearance = .light
    }

    /// Captures the screens that tools/screenshots/frame.py builds the store screenshots from, as
    /// listed in fastlane/screenshots/captions.json.
    func testScreenshots() {
        let app = XCUIApplication()
        setupSnapshot(app)
        app.launchArguments += ["-demoData"]
        app.launch()

        XCTAssertTrue(app.staticTexts["Me"].waitForExistence(timeout: 10))
        snapshot("list")

        openMe(app)
        snapshot("barcode")

        app.navigationBars.buttons.firstMatch.tap()
        app.buttons["Settings"].tap()
        // Open the first barcode on launch
        let option = app.collectionViews.buttons["First Barcode"]
        XCTAssertTrue(option.waitForExistence(timeout: 5))
        option.tap()
        XCTAssertTrue(option.isSelected)
        snapshot("settings")

        // The other iPhone format, in light and dark mode; the code stays black on white
        let barcode = app.collectionViews.buttons["Barcode"].firstMatch
        barcode.tap()
        XCTAssertTrue(barcode.isSelected)
        app.buttons["Done"].tap()
        openMe(app)
        snapshot("barcode-code128")

        app.navigationBars.buttons.firstMatch.tap()
        XCUIDevice.shared.appearance = .dark
        sleep(2) // The switch reaches the app asynchronously, with nothing to wait for
        openMe(app)
        snapshot("barcode-dark")
    }

    private func openMe(_ app: XCUIApplication) {
        app.staticTexts["Me"].tap()
        XCTAssertTrue(app.staticTexts["A0123456"].waitForExistence(timeout: 5))
    }
}
