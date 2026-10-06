import XCTest

/// Swiping up or down to the next barcode, and sideways to the other format.
@MainActor
final class BarcodePagingTests: XCTestCase {
    private let app = XCUIApplication()

    override func setUp() {
        continueAfterFailure = false
        app.launchArguments += ["-demoData"]
        app.launch()
        XCTAssertTrue(app.staticTexts["Me"].waitForExistence(timeout: 10))
    }

    func testKeepsTheSwipedFormatForTheNextBarcode() {
        app.staticTexts["Me"].tap()
        // The demo data's default
        let qrCode = code("QR code for A0123456")
        XCTAssertTrue(qrCode.waitForExistence(timeout: 5))

        qrCode.swipeRight()
        let barcode = code("Barcode for A0123456")
        XCTAssertTrue(barcode.waitForExistence(timeout: 5))

        barcode.swipeUp()
        XCTAssertTrue(code("Barcode for A0246802").waitForExistence(timeout: 5))
        XCTAssertTrue(app.navigationBars["Sam"].exists)
    }

    func testOpensOnTheDefaultFormatAgain() {
        app.staticTexts["Me"].tap()
        let qrCode = code("QR code for A0123456")
        XCTAssertTrue(qrCode.waitForExistence(timeout: 5))
        qrCode.swipeRight()
        XCTAssertTrue(code("Barcode for A0123456").waitForExistence(timeout: 5))

        app.navigationBars.buttons.firstMatch.tap()
        app.staticTexts["Me"].tap()

        XCTAssertTrue(code("QR code for A0123456").waitForExistence(timeout: 5))
    }

    private func code(_ label: String) -> XCUIElement {
        app.descendants(matching: .any)[label].firstMatch
    }
}
