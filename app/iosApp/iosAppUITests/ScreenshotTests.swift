import XCTest

/// Captures the App Store screenshots. Run through fastlane: `bundle exec fastlane ios screenshots`,
/// which uploads them as they are. The names set the store order: the code in both formats, in dark
/// mode, the list, then the launch setting.
@MainActor
final class ScreenshotTests: XCTestCase {
    override func setUp() {
        continueAfterFailure = false
    }

    override func tearDown() {
        XCUIDevice.shared.appearance = .light
    }

    func testScreenshots() {
        let app = XCUIApplication()
        setupSnapshot(app)
        app.launchArguments += ["-demoData"]
        app.launch()

        XCTAssertTrue(app.staticTexts["Me"].waitForExistence(timeout: 10))
        snapshot("04-list")

        openMe(app)
        snapshot("01-barcode")

        app.navigationBars.buttons.firstMatch.tap()
        app.buttons["Settings"].tap()
        // Open the first barcode on launch
        let option = app.collectionViews.buttons["First Barcode"]
        XCTAssertTrue(option.waitForExistence(timeout: 5))
        option.tap()
        XCTAssertTrue(option.isSelected)
        snapshot("05-settings")

        // The other iPhone format, in light and dark mode; the code stays black on white
        let barcode = app.collectionViews.buttons["Barcode"].firstMatch
        barcode.tap()
        XCTAssertTrue(barcode.isSelected)
        app.buttons["Done"].tap()
        openMe(app)
        snapshot("02-barcode-code128")

        app.navigationBars.buttons.firstMatch.tap()
        XCUIDevice.shared.appearance = .dark
        sleep(2) // The switch reaches the app asynchronously, with nothing to wait for
        openMe(app)
        snapshot("03-barcode-dark")
    }

    private func openMe(_ app: XCUIApplication) {
        app.staticTexts["Me"].tap()
        XCTAssertTrue(app.staticTexts["A0123456"].waitForExistence(timeout: 5))
    }
}
