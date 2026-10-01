import XCTest

/// Opening a barcode from outside the app, as the widget does.
@MainActor
final class BarcodeLinkTests: XCTestCase {
    private let app = XCUIApplication()
    /// Links to the demo barcodes by name; the demo data gets new IDs on every launch.
    private var links: [String: URL] = [:]

    override func setUp() {
        continueAfterFailure = false
        app.launchArguments += ["-demoData"]
        app.launch()
        XCTAssertTrue(app.staticTexts["Me"].waitForExistence(timeout: 10))
        // Each list row carries its barcode's ID
        for name in ["Me", "Sam"] {
            let id = app.buttons.containing(.staticText, identifier: name).firstMatch.identifier
            links[name] = URL(string: "bibless://barcode/\(id)")
        }
    }

    func testOpensTheLinkedBarcode() {
        app.open(links["Sam"]!)

        XCTAssertTrue(app.staticTexts["A0246802"].waitForExistence(timeout: 5))
    }

    func testReplacesTheOpenBarcode() {
        app.staticTexts["Sam"].tap()
        XCTAssertTrue(app.staticTexts["A0246802"].waitForExistence(timeout: 5))

        app.open(links["Me"]!)

        XCTAssertTrue(app.staticTexts["A0123456"].waitForExistence(timeout: 5))
        // On top of the list, not on top of Sam
        app.navigationBars.buttons.firstMatch.tap()
        XCTAssertTrue(app.staticTexts["Jamie (junior)"].waitForExistence(timeout: 5))
    }

    func testClosesSettingsToShowTheBarcode() {
        app.buttons["Settings"].tap()
        XCTAssertTrue(app.buttons["Done"].waitForExistence(timeout: 5))

        app.open(links["Sam"]!)

        XCTAssertTrue(app.staticTexts["A0246802"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["Done"].exists)
    }
}
