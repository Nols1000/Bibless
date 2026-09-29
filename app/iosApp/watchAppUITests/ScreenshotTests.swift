import XCTest

/// Captures the Apple Watch screenshots. Run through fastlane: `bundle exec fastlane ios screenshots`.
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

        // The code closes on tap
        app.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.5)).tap()
        let settings = app.buttons["Settings"]
        scroll(app, to: settings)
        settings.tap()
        // Mark the barcode to open on launch
        let option = app.buttons["Me"]
        scroll(app, to: option)
        option.tap()
        snapshot("03-settings")
    }

    /// Swipes up until `element` can be tapped; watch lists load their rows lazily while scrolling.
    private func scroll(_ app: XCUIApplication, to element: XCUIElement) {
        for _ in 0..<10 where !element.isHittable {
            app.swipeUp()
        }
        XCTAssertTrue(element.isHittable)
    }
}
