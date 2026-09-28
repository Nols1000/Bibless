import SharedLogic
import SwiftUI

/// Draws a code as black modules on white with a quiet zone, regardless of color scheme.
struct BarcodeImageView: View {
    let text: String
    let format: BarcodeFormat

    var body: some View {
        let matrix = format.encode(text: text)
        let isLinear = matrix.height == 1
        let quiet = isLinear ? 10 : 4
        let columns = Int(matrix.width) + 2 * quiet
        let rows = isLinear ? CGFloat(columns) * 0.4 : CGFloat(Int(matrix.height) + 2 * quiet)

        Canvas { context, size in
            // Whole-pixel modules keep bar edges crisp, which matters on small watch screens.
            let scale = context.environment.displayScale
            let module = max(1, (size.width * scale / CGFloat(columns)).rounded(.down)) / scale
            let left = (size.width - module * CGFloat(columns)) / 2 + CGFloat(quiet) * module
            let top = (size.height - module * rows) / 2 + CGFloat(quiet) * module
            var path = Path()
            for x in 0..<Int(matrix.width) {
                if isLinear {
                    if matrix.get(x: Int32(x), y: 0) {
                        path.addRect(CGRect(
                            x: left + CGFloat(x) * module,
                            y: CGFloat(quiet) * module,
                            width: module,
                            height: size.height - 2 * CGFloat(quiet) * module
                        ))
                    }
                } else {
                    for y in 0..<Int(matrix.height) where matrix.get(x: Int32(x), y: Int32(y)) {
                        path.addRect(CGRect(
                            x: left + CGFloat(x) * module,
                            y: top + CGFloat(y) * module,
                            width: module,
                            height: module
                        ))
                    }
                }
            }
            context.fill(path, with: .color(.black))
        }
        .aspectRatio(CGFloat(columns) / rows, contentMode: .fit)
        .background(Color.white)
        .accessibilityLabel("\(format.label) for \(text)")
    }
}
