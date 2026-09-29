package com.github.nols1000.bibless

import com.github.nols1000.bibless.barcode.BarcodeFormat
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

private class MemoryStore : KeyValueStore {
    val values = mutableMapOf<String, String>()
    override fun get(key: String) = values[key]
    override fun put(key: String, value: String) {
        values[key] = value
    }
}

/** Connects two repositories the way the Data Layer / WatchConnectivity would. */
private class Link : BarcodeSync {
    lateinit var peer: BarcodeRepository
    var pushes = 0
    override fun push(payloadJson: String) {
        pushes++
        peer.applyRemote(payloadJson)
    }
}

class BarcodeRepositoryTest {
    private var time = 1_000L
    private fun repo(store: KeyValueStore = MemoryStore(), device: Device = Device.PHONE) =
        BarcodeRepository(store, device) { time++ }

    private fun pair(): Pair<BarcodeRepository, BarcodeRepository> {
        val phone = repo()
        val watch = repo(device = Device.WATCH)
        phone.sync = Link().also { it.peer = watch }
        watch.sync = Link().also { it.peer = phone }
        return phone to watch
    }

    @Test
    fun addPersistsAndSorts() {
        val store = MemoryStore()
        val repo = repo(store)
        repo.add("Zoe", "a2")
        repo.add("Alex", "1")
        assertEquals(listOf("Alex", "Zoe"), repo.state.value.barcodes.map { it.name })
        assertEquals(listOf("A1", "A2"), repo(store).state.value.barcodes.map { it.athleteId })
    }

    @Test
    fun addRejectsInvalidId() {
        assertFailsWith<IllegalArgumentException> { repo().add("Me", "nope") }
    }

    @Test
    fun blankNameFallsBackToId() {
        assertEquals("A42", repo().add(" ", "A42").name)
    }

    @Test
    fun defaultFormatIsQr() {
        assertEquals(BarcodeFormat.QR, repo().state.value.format)
        assertEquals(BarcodeFormat.QR, repo(device = Device.WATCH).state.value.format)
    }

    @Test
    fun phoneAndWatchDefaultsAreIndependent() {
        val store = MemoryStore()
        repo(store).setDefaultFormat(Device.PHONE, BarcodeFormat.CODE128)
        assertEquals(BarcodeFormat.CODE128, repo(store, Device.PHONE).state.value.format)
        assertEquals(BarcodeFormat.QR, repo(store, Device.WATCH).state.value.format)
    }

    @Test
    fun settingsSyncBothWays() {
        val (phone, watch) = pair()
        phone.setDefaultFormat(Device.WATCH, BarcodeFormat.CODE128)
        assertEquals(BarcodeFormat.CODE128, watch.state.value.format)
        assertEquals(BarcodeFormat.QR, phone.state.value.format)

        watch.setDefaultFormat(Device.WATCH, BarcodeFormat.QR)
        assertEquals(BarcodeFormat.QR, phone.state.value.settings.watchFormat)
        assertEquals(1, (watch.sync as Link).pushes)
    }

    @Test
    fun olderRemoteSettingsLoseAndArePushedBack() {
        val phone = repo()
        val watch = repo(device = Device.WATCH)
        watch.setDefaultFormat(Device.WATCH, BarcodeFormat.CODE128)
        val stale = watch.payload()
        phone.setDefaultFormat(Device.WATCH, BarcodeFormat.QR)
        phone.sync = Link().also { it.peer = watch }

        phone.applyRemote(stale)
        assertEquals(BarcodeFormat.QR, phone.state.value.settings.watchFormat)
        assertEquals(1, (phone.sync as Link).pushes)
        assertEquals(BarcodeFormat.QR, watch.state.value.format)
    }

    @Test
    fun acceptsLegacyListPayload() {
        val repo = repo()
        repo.applyRemote("""[{"id":"x","name":"Me","athleteId":"A1","updatedAt":1}]""")
        assertEquals(listOf("A1"), repo.state.value.barcodes.map { it.athleteId })
        assertEquals(BarcodeFormat.QR, repo.state.value.format)
    }

    @Test
    fun addAndDeleteSyncBothWays() {
        val (phone, watch) = pair()
        val mine = phone.add("Me", "A1")
        assertEquals(listOf("A1"), watch.state.value.barcodes.map { it.athleteId })

        watch.add("Partner", "A2")
        assertEquals(setOf("A1", "A2"), phone.state.value.barcodes.map { it.athleteId }.toSet())

        watch.delete(mine.id)
        assertNull(phone.find(mine.id))
        assertEquals(phone.payload(), watch.payload())
    }

    @Test
    fun remoteSnapshotDoesNotEchoBack() {
        val (phone, watch) = pair()
        phone.add("Me", "A1")
        assertEquals(0, (watch.sync as Link).pushes)
    }

    @Test
    fun devicesThatDivergedWhileDisconnectedConverge() {
        val phone = repo()
        val watch = repo()
        phone.add("Me", "A1")
        watch.add("Partner", "A2")

        phone.sync = Link().also { it.peer = watch }
        watch.sync = Link().also { it.peer = phone }
        watch.applyRemote(phone.payload())

        assertEquals(phone.payload(), watch.payload())
        assertEquals(2, phone.state.value.barcodes.size)
    }

    @Test
    fun newerVersionWinsAndTombstoneBeatsOlderEntry() {
        val live = Barcode("x", "Me", "A1", updatedAt = 1)
        val deleted = live.copy(updatedAt = 2, deleted = true)
        assertEquals(listOf(deleted), mergeBarcodes(listOf(live), listOf(deleted)))
        assertEquals(listOf(deleted), mergeBarcodes(listOf(deleted), listOf(live)))
    }

    @Test
    fun observeReceivesCurrentAndLaterStates() {
        val repo = repo()
        val seen = mutableListOf<Int>()
        val handle = repo.observe { seen += it.barcodes.size }
        repo.add("Me", "A1")
        handle.close()
        repo.add("You", "A2")
        assertEquals(listOf(0, 1), seen)
        assertTrue(repo.state.value.barcodes.size == 2)
    }

    @Test
    fun ignoresMalformedPayload() {
        val repo = repo()
        repo.applyRemote("not json")
        assertEquals(emptyList(), repo.state.value.barcodes)
    }

    @Test
    fun shownBarcodePersists() {
        val store = MemoryStore()
        val repo = repo(store)
        val barcode = repo.add("Me", "A1")
        repo.shownBarcodeId = barcode.id
        assertEquals(barcode.id, repo(store).shownBarcodeId)
        repo.shownBarcodeId = null
        assertNull(repo(store).shownBarcodeId)
    }

    @Test
    fun shownBarcodeIsForgottenOnceDeleted() {
        val (phone, watch) = pair()
        val barcode = phone.add("Me", "A1")
        phone.shownBarcodeId = barcode.id
        watch.delete(barcode.id)
        assertNull(phone.shownBarcodeId)
    }
}
