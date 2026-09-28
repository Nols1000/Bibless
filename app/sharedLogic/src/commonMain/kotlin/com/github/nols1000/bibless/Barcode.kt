package com.github.nols1000.bibless

import kotlinx.serialization.Serializable

/**
 * A saved parkrun barcode. Deleted entries are kept as tombstones ([deleted]) so the deletion can
 * sync to the other device.
 */
@Serializable
data class Barcode(
    val id: String,
    val name: String,
    val athleteId: String,
    val updatedAt: Long,
    val deleted: Boolean = false,
)
