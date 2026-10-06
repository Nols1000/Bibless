package com.github.nols1000.bibless

import kotlinx.coroutines.await
import kotlinx.coroutines.test.runTest
import web.storage.localStorage
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RunOrganiserTest {

    @AfterTest
    fun clearStorage() = localStorage.clear()

    @Test
    fun runsAreKeptInLocalStorage() = runTest {
        val created = RunOrganiser.create().createRun("Riverside 5k", "2026-10-10", "SCAN_ORDER").await()
        val runs = RunOrganiser.create().listRuns().await()

        assertEquals(listOf(created.id), runs.map { it.id })
        assertEquals("SCAN_ORDER", runs.single().format)
        assertEquals("2026-10-10", runs.single().date)
        assertTrue(localStorage.getItem("bibless.organiser.key") != null)
    }

    @Test
    fun runQrCodeCoversItsGrid() = runTest {
        val organiser = RunOrganiser.create()
        val run = organiser.createRun("Riverside 5k", null, "TOKENS").await()
        val qr = organiser.runQr(run.id).await()!!

        assertTrue(qr.size in 21..61, "QR code is ${qr.size} modules wide")
        assertTrue(qr.path.startsWith("M0 0h1v1h-1z"), "finder pattern starts at the top left")
        assertNull(organiser.runQr("unknown").await())
    }

    @Test
    fun deletingRemovesTheRunFromStorage() = runTest {
        val organiser = RunOrganiser.create()
        val run = organiser.createRun("Riverside 5k", null, "TOKENS").await()
        organiser.deleteRun(run.id)

        assertEquals(0, organiser.listRuns().await().size)
        assertNull(localStorage.getItem("bibless.run.${run.id}"))
    }
}
