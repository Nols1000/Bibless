import SharedLogic
import SwiftUI

/// Draws a code as black modules on white with a quiet zone, regardless of color scheme.
struct BarcodeImageView: View {
    let text: String
    let format: BarcodeFormat

    var body: some View {
        ModuleGridView(grid: ModuleGrid(format.encode(text: text)))
            .accessibilityLabel("\(format.label) for \(text)")
    }
}

extension ModuleGrid {
    /// The modules of a code from the shared encoder.
    init(_ matrix: BitMatrix) {
        let width = Int(matrix.width)
        let height = Int(matrix.height)
        var bits = ""
        bits.reserveCapacity(width * height)
        for y in 0..<height {
            for x in 0..<width {
                bits.append(matrix.get(x: Int32(x), y: Int32(y)) ? "1" : "0")
            }
        }
        self.init(width: width, height: height, bits: bits)
    }
}
