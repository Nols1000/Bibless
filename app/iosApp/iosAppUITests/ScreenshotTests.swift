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

    func testScreenshots() {
        let app = XCUIApplication()
        setupSnapshot(app)
        app.launchArguments += ["-demoData"]
        app.launch()

        XCTAssertTrue(app.staticTexts["Me"].waitForExistence(timeout: 10))
        snapshot("01-list")

        app.staticTexts["Me"].tap()
        XCTAssertTrue(app.staticTexts["A0123456"].waitForExistence(timeout: 5))
        snapshot("02-barcode")

        app.navigationBars.buttons.firstMatch.tap()
        app.buttons["Settings"].tap()
        // Mark the barcode to open on launch
        let option = app.collectionViews.buttons["Me"]
        XCTAssertTrue(option.waitForExistence(timeout: 5))
        option.tap()
        XCTAssertTrue(option.isSelected)
        snapshot("04-settings")

        // The code stays black on white in dark mode
        app.buttons["Done"].tap()
        XCUIDevice.shared.appearance = .dark
        sleep(2) // The switch reaches the app asynchronously, with nothing to wait for
        app.staticTexts["Me"].tap()
        XCTAssertTrue(app.staticTexts["A0123456"].waitForExistence(timeout: 5))
        snapshot("03-barcode-dark")
    }
}
