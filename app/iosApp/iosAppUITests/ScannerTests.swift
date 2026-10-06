import XCTest

/// The finish scanner. The simulator has no camera, so codes are typed in, as volunteers do when
/// a barcode won't scan.
@MainActor
final class ScannerTests: XCTestCase {
    private let app = XCUIApplication()

    override func setUp() {
        continueAfterFailure = false
        app.launchArguments += ["-demoData"]
        app.launch()
        app.buttons["Scanner"].tap()
        XCTAssertTrue(app.buttons["Start"].waitForExistence(timeout: 5))
        app.buttons["Start"].tap()
        XCTAssertTrue(app.staticTexts["nextStep"].waitForExistence(timeout: 5))
    }

    private func type(_ code: String) {
        app.buttons["Type a Code"].tap()
        let field = app.alerts.textFields.firstMatch
        XCTAssertTrue(field.waitForExistence(timeout: 5))
        field.typeText(code)
        app.alerts.buttons["Add"].tap()
    }

    func testPairsARunnerWithTheirToken() {
        XCTAssertEqual(app.staticTexts["nextStep"].label, "Scan a runner's barcode and their finish token")

        type("A123456")
        XCTAssertTrue(app.staticTexts["Scan the finish token for A123456"].waitForExistence(timeout: 5))

        type("P0042")
        XCTAssertTrue(app.staticTexts["P42: A123456"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["1 finisher"].exists)
    }

    func testWarnsAboutDuplicates() {
        type("A123456")
        type("P1")
        type("A123456")

        XCTAssertTrue(app.staticTexts["A123456 was already recorded."].waitForExistence(timeout: 5))
    }

    func testUndoClearsTheHalfPairThenTheLastPair() {
        type("A111111")
        type("P1")
        type("A222222")

        app.buttons["Clear Scan"].tap()
        app.buttons["Undo P1"].tap()

        XCTAssertTrue(app.staticTexts["0 finishers"].waitForExistence(timeout: 5))
    }

    func testNoBarcodeTakesTheScannedToken() {
        XCTAssertFalse(app.buttons["No Barcode"].isEnabled)
        type("P0009")

        app.buttons["No Barcode"].tap()
        app.alerts.buttons["Add"].tap()

        XCTAssertTrue(app.staticTexts["P9: Unknown"].waitForExistence(timeout: 5))
    }

    func testStoppingAsksFirst() {
        app.navigationBars.buttons["Stop"].tap()
        let alert = app.alerts["Stop scanning?"]
        XCTAssertTrue(alert.waitForExistence(timeout: 5))
        alert.buttons["Stop"].tap()

        XCTAssertTrue(app.buttons["Start"].waitForExistence(timeout: 5))
    }
}
