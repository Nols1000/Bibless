import XCTest

/// Sharing recorded sessions with the organiser.
@MainActor
final class SessionsTests: XCTestCase {
    private let app = XCUIApplication()

    override func setUp() {
        continueAfterFailure = false
        app.launchArguments += ["-demoData"]
        app.launch()
        app.buttons["Timer"].tap()
    }

    func testWithoutSessionsTheListExplainsItself() {
        app.buttons["Recorded Sessions"].tap()
        XCTAssertTrue(app.staticTexts["No Sessions"].waitForExistence(timeout: 5))
    }

    func testSharesARecordedSession() {
        app.buttons["Start"].tap()
        app.buttons["Finish"].tap()
        app.buttons["Finish"].tap()
        app.navigationBars.buttons["Stop"].tap()
        app.alerts.buttons["Stop"].tap()

        app.buttons["Recorded Sessions"].tap()
        XCTAssertTrue(app.staticTexts["2 finish times · no run"].waitForExistence(timeout: 5))
        app.buttons["Share"].tap()

        XCTAssertTrue(app.otherElements["ActivityListView"].waitForExistence(timeout: 10))
    }
}
