package com.github.nols1000.bibless

/** Merges two snapshots per [Barcode.id]; the most recently updated version of each entry wins. */
fun mergeBarcodes(local: List<Barcode>, remote: List<Barcode>): List<Barcode> =
    (local + remote)
        .groupBy { it.id }
        .map { (_, versions) -> versions.maxWith(compareBy<Barcode> { it.updatedAt }.thenBy { it.deleted }) }
        .sortedBy { it.id }
