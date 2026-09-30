package com.github.nols1000.bibless

/**
 * Sample barcodes for the store screenshots (fastlane snapshot / screengrab), so no real
 * athlete's code shows up in them. The IDs are made up and written with a leading zero, so they
 * don't match anyone's printed athlete ID.
 */
object DemoData {
    /** Launch argument that starts the iOS and watchOS apps with the demo data in memory. */
    const val LAUNCH_ARGUMENT = "-demoData"

    val barcodes = listOf(
        "Me" to "A0123456",
        "Sam" to "A0246802",
        "Jamie (junior)" to "A0135791",
    )

    /** Entry typed into the add form in the screenshots; never saved. */
    val newBarcode = "Alex" to "A0975310"

    /**
     * Replaces every saved barcode in [repository] with [barcodes], in that order, and resets the
     * settings, so the app starts on the list with its default formats whatever an earlier run
     * left behind.
     */
    fun load(repository: BarcodeRepository) {
        repository.state.value.barcodes.forEach { repository.delete(it.id) }
        // Added within the same millisecond, so their order is set explicitly
        repository.setOrder(barcodes.map { (name, id) -> repository.add(name, id).id })
        repository.shownBarcodeId = null
        repository.setOpenOnLaunch(Settings().openOnLaunch)
        val defaults = Settings()
        Device.entries.forEach { repository.setDefaultFormat(it, defaults.formatFor(it)) }
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
