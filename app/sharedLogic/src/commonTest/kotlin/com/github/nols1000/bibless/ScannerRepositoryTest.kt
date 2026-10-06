package com.github.nols1000.bibless

import com.github.nols1000.bibless.race.RecordAthleteEvent
import com.github.nols1000.bibless.race.RemoveAthleteEvent
import com.github.nols1000.bibless.race.verify
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ScannerRepositoryTest {
    private val store = InMemoryStore()
    private val clock = FakeClock()

    private fun TestScope.device() = RaceDevice(store, clock, backgroundScope)

    private fun TestScope.scanner() = ScannerRepository(device()).also { it.start() }

    /** Scans [text] after the previous code left the camera's view. */
    private fun ScannerRepository.scanLater(text: String): ScanOutcome {
        clock.advance(2_000)
        return scan(text)
    }

    private fun ScannerRepository.records() = state.value.session!!.records.map { it.athleteId to it.position }

    @Test
    fun codesAreAthletesOrTokens() {
        assertEquals(ScannedCode.Athlete("A123456"), parseScannedCode(" a123456 "))
        assertEquals(ScannedCode.Token(42), parseScannedCode("P0042"))
        assertEquals(ScannedCode.Token(7), parseScannedCode("p7"))
        assertIs<ScannedCode.Unknown>(parseScannedCode("P0000"))
        assertIs<ScannedCode.Unknown>(parseScannedCode("https://example.com"))
    }

    @Test
    fun anAthleteThenATokenMakeAPair() = runTest {
        val scanner = scanner()
        val first = scanner.scanLater("A123456")
        assertEquals(ScanOutcome.Waiting("A123456", null, emptyList()), first)
        assertEquals("A123456", scanner.state.value.session!!.pendingAthleteId)

        val pair = assertIs<ScanOutcome.Recorded>(scanner.scanLater("P0001"))
        assertEquals(emptyList(), pair.warnings)
        assertEquals(listOf("A123456" to 1), scanner.records())
        assertNull(scanner.state.value.session!!.pendingAthleteId)
    }

    @Test
    fun theTokenMayComeFirst() = runTest {
        val scanner = scanner()
        assertEquals(ScanOutcome.Waiting(null, 2, emptyList()), scanner.scanLater("P0002"))
        assertIs<ScanOutcome.Recorded>(scanner.scanLater("A123456"))
        assertEquals(listOf("A123456" to 2), scanner.records())
    }

    @Test
    fun aCodeStillInViewIsTakenOnce() = runTest {
        val scanner = scanner()
        scanner.scanLater("A123456")
        scanner.scanLater("P0001")
        repeat(10) {
            clock.advance(100)
            assertEquals(ScanOutcome.Repeat, scanner.scan("P0001"))
        }
        assertEquals(1, scanner.records().size)
    }

    @Test
    fun duplicatesAreRecordedWithAWarning() = runTest {
        val scanner = scanner()
        scanner.scanLater("A123456")
        scanner.scanLater("P0001")
        // The warning comes with the scan that repeats, while the runner is still there
        val athlete = assertIs<ScanOutcome.Waiting>(scanner.scanLater("A123456"))
        assertEquals(listOf(ScanWarning.DUPLICATE_ATHLETE), athlete.warnings)
        assertIs<ScanOutcome.Recorded>(scanner.scanLater("P0002"))

        val token = assertIs<ScanOutcome.Waiting>(scanner.scanLater("P0002"))
        assertEquals(listOf(ScanWarning.DUPLICATE_TOKEN), token.warnings)
        assertIs<ScanOutcome.Recorded>(scanner.scanLater("A999999"))
        assertEquals(3, scanner.records().size)
    }

    @Test
    fun aSecondAthleteReplacesOneWithoutToken() = runTest {
        val scanner = scanner()
        scanner.scanLater("A111111")
        val replaced = assertIs<ScanOutcome.Waiting>(scanner.scanLater("A222222"))
        assertEquals(listOf(ScanWarning.REPLACED_ATHLETE), replaced.warnings)
        scanner.scanLater("P0001")
        assertEquals(listOf("A222222" to 1), scanner.records())
    }

    @Test
    fun unknownCodesAreReported() = runTest {
        val scanner = scanner()
        assertEquals(ScanOutcome.NotRecognised("hello"), scanner.scanLater("hello"))
        assertTrue(scanner.records().isEmpty())
    }

    @Test
    fun undoDropsTheHalfPairFirstThenTheLastPair() = runTest {
        val scanner = scanner()
        scanner.scanLater("A111111")
        scanner.scanLater("P0001")
        scanner.scanLater("A222222")

        scanner.undo()
        assertNull(scanner.state.value.session!!.pendingAthleteId)
        assertEquals(1, scanner.records().size)

        scanner.undo()
        assertTrue(scanner.records().isEmpty())
    }

    @Test
    fun typedCodesAreTakenEveryTime() = runTest {
        val scanner = scanner()
        scanner.enter("A123456")
        scanner.enter("P1")
        scanner.enter("A123456")
        assertIs<ScanOutcome.Recorded>(scanner.enter("P2"))
        assertEquals(2, scanner.records().size)
    }

    @Test
    fun scanningNeedsASession() = runTest {
        val scanner = ScannerRepository(device())
        assertEquals(ScanOutcome.NotRunning, scanner.scanLater("A123456"))
    }

    @Test
    fun pairsSurviveARestartAndAreSigned() = runTest {
        val scanner = scanner()
        scanner.scanLater("A123456")
        scanner.scanLater("P0001")
        scanner.undo()
        scanner.scanLater("A654321")
        scanner.scanLater("P0001")

        val reopened = ScannerRepository(device())
        assertEquals(listOf("A654321" to 1), reopened.records())
        advanceUntilIdle()
        val id = reopened.state.value.session!!.id
        val verification = reopened.log(id)!!.verify()
        assertTrue(verification.problems.isEmpty(), "${verification.problems}")
        val events = verification.entries.sortedBy { it.entry.seq }.map { it.entry.event::class }
        assertEquals(listOf(RecordAthleteEvent::class, RemoveAthleteEvent::class, RecordAthleteEvent::class), events.drop(1))
    }

    @Test
    fun timerAndScannerSignWithTheSameKey() = runTest {
        val device = device()
        val timer = TimerRepository(device).also { it.start() }
        val scanner = ScannerRepository(device).also { it.start() }
        advanceUntilIdle()

        val authors = listOf(
            timer.log(timer.state.value.session!!.id)!!,
            scanner.log(scanner.state.value.session!!.id)!!,
        ).map { it.verify().entries.single().entry.author }
        assertEquals(1, authors.toSet().size)
    }
}
