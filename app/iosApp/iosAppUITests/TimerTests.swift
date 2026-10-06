import XCTest

/// The phone stopwatch. Demo data keeps the timer in memory, so every launch starts stopped.
@MainActor
final class TimerTests: XCTestCase {
    private let app = XCUIApplication()

    override func setUp() {
        continueAfterFailure = false
        app.launchArguments += ["-demoData"]
        app.launch()
        app.buttons["Timer"].tap()
        XCTAssertTrue(app.buttons["Start"].waitForExistence(timeout: 5))
    }

    func testRecordsAndUndoesFinishes() {
        app.buttons["Start"].tap()
        XCTAssertTrue(app.staticTexts["0 finishers"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["Undo"].isEnabled)

        app.buttons["Finish"].tap()
        app.buttons["Finish"].tap()

        XCTAssertTrue(app.staticTexts["2 finishers"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["P1"].exists)
        XCTAssertTrue(app.staticTexts["P2"].exists)

        app.buttons.matching(NSPredicate(format: "label BEGINSWITH 'Undo P2'")).firstMatch.tap()

        XCTAssertTrue(app.staticTexts["1 finisher"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.staticTexts["P2"].exists)
    }

    func testTheClockRuns() {
        app.buttons["Start"].tap()
        let elapsed = app.staticTexts["elapsed"]
        XCTAssertTrue(elapsed.waitForExistence(timeout: 5))

        XCTAssertTrue(elapsed.wait(for: \.label, toEqual: "0:02", timeout: 5))
    }

    func testStoppingAsksFirst() {
        app.buttons["Start"].tap()
        app.buttons["Finish"].tap()

        app.navigationBars.buttons["Stop"].tap()
        let alert = app.alerts["Stop the timer?"]
        XCTAssertTrue(alert.waitForExistence(timeout: 5))
        alert.buttons["Keep Timing"].tap()
        XCTAssertTrue(app.buttons["Finish"].exists)

        app.navigationBars.buttons["Stop"].tap()
        alert.buttons["Stop"].tap()

        XCTAssertTrue(app.buttons["Start"].waitForExistence(timeout: 5))
    }

    func testClosingKeepsTheTimerRunning() {
        app.buttons["Start"].tap()
        app.buttons["Close"].tap()
        app.buttons["Timer"].tap()

        XCTAssertTrue(app.buttons["Finish"].waitForExistence(timeout: 5))
    }
}
