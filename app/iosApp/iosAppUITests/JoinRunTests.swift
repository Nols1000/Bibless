import XCTest

/// Joining a run before timing or scanning. The simulator can't scan, so the joined run comes in
/// through the test variable that RunStore reads with demo data.
@MainActor
final class JoinRunTests: XCTestCase {
    private let app = XCUIApplication()
    private let run = #"{"run":"run-1","root":"\#(String(repeating: "B", count: 87))","name":"Riverside 5k","format":"SCAN_ORDER","date":"2026-10-10"}"#

    override func setUp() {
        continueAfterFailure = false
        app.launchArguments += ["-demoData"]
    }

    func testWithoutARunTheTimerOffersToJoin() {
        app.launch()
        app.buttons["Timer"].tap()

        XCTAssertTrue(app.staticTexts["No Run"].waitForExistence(timeout: 5))
        app.buttons["Join Run"].tap()
        XCTAssertTrue(app.navigationBars["Join a Run"].waitForExistence(timeout: 5))
        app.buttons["Cancel"].tap()
        XCTAssertTrue(app.staticTexts["No Run"].waitForExistence(timeout: 5))
    }

    func testTheJoinedRunShowsAndRecords() {
        app.launchEnvironment["BIBLESS_JOIN_RUN"] = run
        app.launch()
        app.buttons["Timer"].tap()

        XCTAssertTrue(app.staticTexts["Riverside 5k"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts.containing(NSPredicate(format: "label CONTAINS 'Scan order'")).firstMatch.exists)
        app.buttons["Start"].tap()

        XCTAssertTrue(app.buttons["Finish"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["Riverside 5k"].exists)
    }

    func testLeavingRecordsWithoutARun() {
        app.launchEnvironment["BIBLESS_JOIN_RUN"] = run
        app.launch()
        app.buttons["Scanner"].tap()
        XCTAssertTrue(app.staticTexts["Riverside 5k"].waitForExistence(timeout: 5))

        app.buttons["Leave"].tap()

        XCTAssertTrue(app.staticTexts["No Run"].waitForExistence(timeout: 5))
    }
}
