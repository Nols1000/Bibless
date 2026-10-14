import XCTest

/// The scanner in scan order, without finish tokens. Codes are typed, as the simulator can't scan.
@MainActor
final class ScanOrderTests: XCTestCase {
    private let app = XCUIApplication()

    override func setUp() {
        continueAfterFailure = false
        app.launchArguments += ["-demoData"]
        app.launch()
        app.buttons["Scanner"].tap()
        XCTAssertTrue(app.buttons["Start"].waitForExistence(timeout: 5))
        app.buttons["Scan order"].tap()
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label CONTAINS 'moves everyone behind it'")).firstMatch.exists)
        app.buttons["Start"].tap()
        XCTAssertTrue(app.staticTexts["nextPosition"].waitForExistence(timeout: 5))
    }

    private func type(_ code: String) {
        app.buttons["Type a Code"].tap()
        let field = app.alerts.textFields.firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        field.typeText(code)
        app.alerts.buttons["Add"].tap()
    }

    func testEachRunnerTakesTheNextPosition() {
        XCTAssertEqual(app.staticTexts["nextPosition"].label, "Next: P1")

        type("A111111")
        type("A222222")

        XCTAssertTrue(app.staticTexts["P2: A222222"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.staticTexts["nextPosition"].label, "Next: P3")

        app.buttons["Undo P2"].tap()
        XCTAssertTrue(app.staticTexts["Next: P2"].waitForExistence(timeout: 5))
    }

    func testUnknownFinishersTakeAPosition() {
        type("A111111")

        app.buttons["No Barcode"].tap()
        let alert = app.alerts["Finisher P2 Without a Barcode"]
        XCTAssertTrue(alert.waitForExistence(timeout: 5))
        alert.textFields.firstMatch.typeText("Sam")
        alert.buttons["Add"].tap()

        XCTAssertTrue(app.staticTexts["Sam (no barcode)"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.staticTexts["nextPosition"].label, "Next: P3")
    }

    func testTokensAreTurnedAway() {
        type("P0001")

        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label BEGINSWITH 'This run has no finish tokens'")).firstMatch.waitForExistence(timeout: 5))
        XCTAssertEqual(app.staticTexts["nextPosition"].label, "Next: P1")
    }
}
