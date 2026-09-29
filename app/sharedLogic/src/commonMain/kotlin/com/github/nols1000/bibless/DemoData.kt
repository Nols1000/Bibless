package com.github.nols1000.bibless

/**
 * Sample barcodes for the store screenshots (fastlane snapshot / screengrab), so no real
 * athlete's code shows up in them. The IDs are made up and written with a leading zero, so they
 * don't match anyone's printed parkrun ID.
 */
object DemoData {
    /** Launch argument that starts the iOS and watchOS apps with the demo data in memory. */
    const val LAUNCH_ARGUMENT = "-demoData"

    val barcodes = listOf(
        "Me" to "A0123456",
        "Sam" to "A0246802",
        "Jamie (junior)" to "A0135791",
    )

    /** Replaces every saved barcode in [repository] with [barcodes]. */
    fun load(repository: BarcodeRepository) {
        repository.state.value.barcodes.forEach { repository.delete(it.id) }
        barcodes.forEach { (name, id) -> repository.add(name, id) }
    }
}

/** Non-persistent [KeyValueStore], e.g. for demo mode. */
class InMemoryStore : KeyValueStore {
    private val values = mutableMapOf<String, String>()

    override fun get(key: String): String? = values[key]

    override fun put(key: String, value: String) {
        values[key] = value
    }
}
