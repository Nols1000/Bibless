import SwiftUI

/// A code's dark and light modules, in plain Swift so widgets can draw it without the Kotlin
/// framework. The apps make it from the shared encoder (see `BarcodeImageView`).
struct ModuleGrid: Codable, Equatable {
    let width: Int
    let height: Int
    /// Row by row, "1" for a dark module and "0" for a light one.
    let bits: String

    var isLinear: Bool { height == 1 }

    /// Whether each module is dark, row by row.
    var modules: [Bool] { bits.utf8.map { $0 == UInt8(ascii: "1") } }
}

/// Draws a code as black modules on white with a quiet zone, regardless of color scheme.
struct ModuleGridView: View {
    let grid: ModuleGrid

    var body: some View {
        let quiet = grid.isLinear ? 10 : 4
        let columns = grid.width + 2 * quiet
        let rows = grid.isLinear ? CGFloat(columns) * 0.4 : CGFloat(grid.height + 2 * quiet)
        let dark = grid.modules

        Canvas { context, size in
            // Whole-pixel modules keep bar edges crisp, which matters on small watch screens.
            let scale = context.environment.displayScale
            let module = max(1, (size.width * scale / CGFloat(columns)).rounded(.down)) / scale
            let left = (size.width - module * CGFloat(columns)) / 2 + CGFloat(quiet) * module
            let top = (size.height - module * rows) / 2 + CGFloat(quiet) * module
            var path = Path()
            for x in 0..<grid.width {
                if grid.isLinear {
                    if dark[x] {
                        path.addRect(CGRect(
                            x: left + CGFloat(x) * module,
                            y: CGFloat(quiet) * module,
                            width: module,
                            height: size.height - 2 * CGFloat(quiet) * module
                        ))
                    }
                } else {
                    for y in 0..<grid.height where dark[y * grid.width + x] {
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
    }
}
