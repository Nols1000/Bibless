import XCTest

/// Captures the App Store screenshots. Run through fastlane: `bundle exec fastlane ios screenshots`.
@MainActor
final class ScreenshotTests: XCTestCase {
    override func setUp() {
        continueAfterFailure = false
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
    }
}
