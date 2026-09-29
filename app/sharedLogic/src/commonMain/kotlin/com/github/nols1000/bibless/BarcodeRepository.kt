package com.github.nols1000.bibless

import com.github.nols1000.bibless.barcode.BarcodeFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.decodeFromJsonElement
import kotlin.time.Clock
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class BarcodeState(
    val barcodes: List<Barcode>,
    val settings: Settings,
    /** The format this device shows codes in, from [settings]. */
    val format: BarcodeFormat,
)

/** What one device sends the other: every barcode (including tombstones) and the settings. */
@Serializable
private data class SyncPayload(
    val barcodes: List<Barcode>,
    val settings: Settings? = null,
)

/**
 * Source of truth for saved barcodes and settings on this device. Every local change is persisted
 * and pushed through [sync]; snapshots from the paired device come in through [applyRemote].
 */
class BarcodeRepository(
    private val store: KeyValueStore,
    private val device: Device,
    private val now: () -> Long,
) {
    constructor(store: KeyValueStore, device: Device) :
        this(store, device, { Clock.System.now().toEpochMilliseconds() })

    var sync: BarcodeSync? = null

    private val all = MutableStateFlow(runCatching { decode(store.get(KEY_BARCODES)) }.getOrDefault(emptyList()))
    private var settings = runCatching { store.get(KEY_SETTINGS)?.let { json.decodeFromString<Settings>(it) } }
        .getOrNull() ?: Settings()
    private val _state = MutableStateFlow(currentState())
    val state: StateFlow<BarcodeState> = _state.asStateFlow()

    private val listeners = MutableStateFlow(emptyList<(BarcodeState) -> Unit>())

    fun find(id: String): Barcode? = all.value.firstOrNull { it.id == id && !it.deleted }

    /**
     * The barcode whose detail screen is open on this device, so the app can reopen on it after
     * being closed; null once it was deleted. Kept per device and never synced.
     */
    var shownBarcodeId: String?
        get() = store.get(KEY_SHOWN_BARCODE)?.takeIf { find(it) != null }
        set(value) = store.put(KEY_SHOWN_BARCODE, value.orEmpty())

    /**
     * The barcode to open on launch, on top of the list: the one still shown from last time, else
     * the default, else the only barcode there is. Null starts on the list.
     */
    fun startBarcodeId(): String? =
        shownBarcodeId ?: defaultBarcodeId() ?: _state.value.barcodes.singleOrNull()?.id

    /** The barcode marked as default in the settings, if it still exists. */
    fun defaultBarcodeId(): String? = settings.defaultBarcodeId?.takeIf { find(it) != null }

    /** Adds a barcode, or throws [IllegalArgumentException] if [athleteId] is not a valid parkrun ID. */
    @OptIn(ExperimentalUuidApi::class)
    @Throws(IllegalArgumentException::class)
    fun add(name: String, athleteId: String): Barcode {
        val id = requireNotNull(normalizeAthleteId(athleteId)) { "Invalid parkrun ID: $athleteId" }
        val barcode = Barcode(
            id = Uuid.random().toString(),
            name = name.trim().ifEmpty { id },
            athleteId = id,
            updatedAt = now(),
        )
        all.update { it + barcode }
        commit(push = true)
        return barcode
    }

    fun delete(id: String) {
        all.update { list ->
            list.map { if (it.id == id) it.copy(deleted = true, updatedAt = now()) else it }
        }
        commit(push = true)
    }

    /** Sets the default format for phones or watches; syncs to the paired device. */
    fun setDefaultFormat(device: Device, format: BarcodeFormat) {
        settings = when (device) {
            Device.PHONE -> settings.copy(phoneFormat = format)
            Device.WATCH -> settings.copy(watchFormat = format)
        }.copy(updatedAt = now())
        commit(push = true)
    }

    /** Marks the barcode to open on launch, or null for the list; syncs to the paired device. */
    fun setDefaultBarcode(id: String?) {
        settings = settings.copy(defaultBarcodeId = id, updatedAt = now())
        commit(push = true)
    }

    /** JSON snapshot of every entry, including tombstones, plus the settings, for [BarcodeSync]. */
    fun payload(): String = json.encodeToString(SyncPayload(all.value.sortedBy { it.id }, settings))

    /**
     * Merges a snapshot received from the paired device. Pushes back only if this device has
     * entries or settings the sender lacks, so the two sides converge without echoing each other.
     */
    fun applyRemote(payloadJson: String) {
        val remote = runCatching { decodePayload(payloadJson) }.getOrNull() ?: return
        var changedLocally = false
        val merged = all.updateAndGet { local ->
            mergeBarcodes(local, remote.barcodes).also { changedLocally = it != mergeBarcodes(local, emptyList()) }
        }
        val remoteSettings = remote.settings
        if (remoteSettings != null && remoteSettings.updatedAt > settings.updatedAt) {
            settings = remoteSettings
            changedLocally = true
        }
        if (changedLocally) commit(push = false)

        val remoteLacksEntries = merged != mergeBarcodes(remote.barcodes, emptyList())
        val remoteLacksSettings = remoteSettings != null && settings.updatedAt > remoteSettings.updatedAt
        if (remoteLacksEntries || remoteLacksSettings) sync?.push(payload())
    }

    /** Callback-style observation for Swift. The listener is called immediately with the current state. */
    fun observe(listener: (BarcodeState) -> Unit): AutoCloseable {
        listeners.update { it + listener }
        listener(_state.value)
        return AutoCloseable { listeners.update { it - listener } }
    }

    private fun commit(push: Boolean) {
        store.put(KEY_BARCODES, json.encodeToString(all.value.sortedBy { it.id }))
        store.put(KEY_SETTINGS, json.encodeToString(settings))
        publish()
        if (push) sync?.push(payload())
    }

    private fun publish() {
        val state = currentState()
        _state.value = state
        listeners.value.forEach { it(state) }
    }

    private fun currentState() = BarcodeState(visible(all.value), settings, settings.formatFor(device))

    private companion object {
        const val KEY_BARCODES = "barcodes"
        const val KEY_SETTINGS = "settings"
        const val KEY_SHOWN_BARCODE = "shownBarcode"

        val json = Json { ignoreUnknownKeys = true }

        fun decode(payload: String?): List<Barcode> =
            payload?.let { json.decodeFromString<List<Barcode>>(it) }.orEmpty()

        /** Accepts the current object payload and the older bare list sent by previous versions. */
        fun decodePayload(payload: String): SyncPayload {
            val element = json.parseToJsonElement(payload)
            return if (element is JsonArray) {
                SyncPayload(json.decodeFromJsonElement<List<Barcode>>(element))
            } else {
                json.decodeFromJsonElement<SyncPayload>(element)
            }
        }

        fun visible(all: List<Barcode>) =
            all.filterNot { it.deleted }.sortedWith(compareBy({ it.name.lowercase() }, { it.athleteId }))
    }
}
