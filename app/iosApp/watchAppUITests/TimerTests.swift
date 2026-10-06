import XCTest

/// The watch stopwatch. Demo data keeps the timer in memory, so every launch starts stopped.
@MainActor
final class TimerTests: XCTestCase {
    private let app = XCUIApplication()

    override func setUp() {
        continueAfterFailure = false
        app.launchArguments += ["-demoData"]
        app.launch()
        let timer = app.buttons["Timer"]
        XCTAssertTrue(app.staticTexts["Me"].waitForExistence(timeout: 10))
        // Watch lists load their rows lazily while scrolling
        for _ in 0..<10 where !timer.isHittable {
            app.swipeUp()
        }
        timer.tap()
        XCTAssertTrue(app.buttons["Start"].waitForExistence(timeout: 5))
    }

    func testRecordsAndUndoesFinishes() {
        app.buttons["Start"].tap()
        let finish = app.buttons["finish"]
        XCTAssertTrue(finish.waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["No finishers yet"].exists)
        XCTAssertFalse(app.buttons["Undo"].isEnabled)

        finish.tap()
        finish.tap()
        XCTAssertTrue(app.staticTexts["P3"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH 'P2 '")).firstMatch.exists)

        app.buttons["Undo"].tap()
        XCTAssertTrue(app.staticTexts["P2"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts.matching(NSPredicate(format: "label BEGINSWITH 'P1 '")).firstMatch.exists)
    }

    func testTheClockRuns() {
        app.buttons["Start"].tap()
        let elapsed = app.staticTexts["elapsed"]
        XCTAssertTrue(elapsed.waitForExistence(timeout: 5))
        XCTAssertTrue(elapsed.wait(for: \.label, toEqual: "0:02", timeout: 5))
    }

    func testStoppingAsksFirst() {
        app.buttons["Start"].tap()
        app.buttons["finish"].tap()

        app.buttons["Stop"].tap()
        XCTAssertTrue(app.staticTexts["Stop the timer?"].waitForExistence(timeout: 5))
        XCTAssertTrue(app.staticTexts["1 finisher. The times go to your iPhone."].exists)
        app.buttons["Stop Timer"].tap()

        XCTAssertTrue(app.buttons["Start"].waitForExistence(timeout: 5))
    }
}
