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

    @Test
    fun reviewShowsRowsAndIssues() = runTest {
        val organiser = RunOrganiser.create()
        val run = organiser.createRun("Riverside 5k", null, "TOKENS").await()
        organiser.addFinisher(run.id, 1, "123", "  ").await()
        organiser.addFinisher(run.id, null, null, "Sam").await()
        val review = organiser.getReview(run.id).await()!!

        assertEquals("Riverside 5k", review.run.name)
        assertEquals(listOf("A123"), review.rows.map { it.athleteId })
        assertEquals(listOf("MISSING_POSITION", "MISSING_TIME"), review.issues.map { it.kind })
        assertEquals("Sam", review.issues[0].finishers.single().name)
        assertEquals(listOf(1), review.issues[1].positions.toList())
        assertEquals("position,time,elapsed_ms,athlete_id,name\n1,,,A123,\n", organiser.resultsCsv(run.id).await())
    }

    @Test
    fun importRejectsOtherFiles() = runTest {
        val organiser = RunOrganiser.create()
        val run = organiser.createRun("Riverside 5k", null, "TOKENS").await()
        assertTrue(runCatching { organiser.importLog(run.id, "position,time").await() }.isFailure)
        assertEquals(0, organiser.importLog(run.id, "{}").await())
    }

    @Test
    fun tokensCarryTheirTextAndBars() {
        val tokens = RunOrganiser.create().tokens(9, 11)

        assertEquals(listOf("P0009", "P0010", "P0011"), tokens.map { it.text })
        // Code 128 always starts with a 2-module bar and ends with the stop pattern's 2-module bar
        assertTrue(tokens[0].path.startsWith("M0 0h2v1h-2z"), tokens[0].path)
        assertTrue(tokens[0].path.endsWith("M${tokens[0].width - 2} 0h2v1h-2z"), tokens[0].path)
        assertTrue(runCatching { RunOrganiser.create().tokens(5, 4) }.isFailure)
    }
}
