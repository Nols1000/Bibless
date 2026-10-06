package com.github.nols1000.bibless

import com.github.nols1000.bibless.race.RecordAthleteEvent
import com.github.nols1000.bibless.race.RemoveAthleteEvent
import com.github.nols1000.bibless.race.TimingMode
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

    private fun TestScope.scanner(mode: TimingMode = TimingMode.TOKENS) = ScannerRepository(device()).also { it.start(mode) }

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
        val scanner = ScannerRepository(device).also { it.start(TimingMode.TOKENS) }
        advanceUntilIdle()

        val authors = listOf(
            timer.log(timer.state.value.session!!.id)!!,
            scanner.log(scanner.state.value.session!!.id)!!,
        ).map { it.verify().entries.single().entry.author }
        assertEquals(1, authors.toSet().size)
    }

    @Test
    fun inScanOrderEachRunnerTakesTheNextPosition() = runTest {
        val scanner = scanner(TimingMode.SCAN_ORDER)
        assertEquals(1, scanner.state.value.session!!.nextPosition)

        val first = assertIs<ScanOutcome.Recorded>(scanner.scanLater("A111111"))
        assertEquals(1, first.record.position)
        scanner.scanLater("A222222")

        assertEquals(listOf("A111111" to 1, "A222222" to 2), scanner.records())
        assertEquals(3, scanner.state.value.session!!.nextPosition)
        assertEquals("Scan the next finisher", scanner.state.value.session!!.nextStep())
    }

    @Test
    fun undoInScanOrderGivesThePositionBack() = runTest {
        val scanner = scanner(TimingMode.SCAN_ORDER)
        scanner.scanLater("A111111")
        scanner.scanLater("A222222")
        scanner.undo()

        assertEquals(2, scanner.state.value.session!!.nextPosition)
        assertIs<ScanOutcome.Recorded>(scanner.scanLater("A333333"))
        assertEquals(listOf("A111111" to 1, "A333333" to 2), scanner.records())
    }

    @Test
    fun scanOrderWarnsAboutDuplicatesButKeepsThem() = runTest {
        val scanner = scanner(TimingMode.SCAN_ORDER)
        scanner.scanLater("A111111")
        val again = assertIs<ScanOutcome.Recorded>(scanner.scanLater("A111111"))

        assertEquals(listOf(ScanWarning.DUPLICATE_ATHLETE), again.warnings)
        assertEquals(2, scanner.records().size)
    }

    @Test
    fun scanOrderTurnsTokensAway() = runTest {
        val scanner = scanner(TimingMode.SCAN_ORDER)
        assertEquals(ScanOutcome.TokenInScanOrder, scanner.scanLater("P0001"))
        assertTrue(scanner.records().isEmpty())
    }

    @Test
    fun unknownFinishersTakeTheNextPositionInScanOrder() = runTest {
        val scanner = scanner(TimingMode.SCAN_ORDER)
        scanner.scanLater("A111111")
        val unknown = assertIs<ScanOutcome.Recorded>(scanner.addUnknown(" Sam "))
        scanner.addUnknown("")

        assertEquals(ScanRecord(unknown.record.eventId, null, 2, "Sam"), unknown.record)
        assertEquals(listOf("A111111" to 1, null to 2, null to 3), scanner.records())
        assertEquals("Sam (no barcode)", scanner.state.value.session!!.records[1].label())
        assertEquals("Unknown", scanner.state.value.session!!.records[2].label())
    }

    @Test
    fun unknownFinishersWithTokensNeedTheTokenFirst() = runTest {
        val scanner = scanner()
        assertEquals(ScanOutcome.MissingToken, scanner.addUnknown(null))

        scanner.scanLater("P0005")
        assertIs<ScanOutcome.Recorded>(scanner.addUnknown(null))
        assertEquals(listOf(null to 5), scanner.records())
        assertNull(scanner.state.value.session!!.pendingPosition)
    }

    @Test
    fun theModeSurvivesARestart() = runTest {
        scanner(TimingMode.SCAN_ORDER).scanLater("A111111")
        val reopened = ScannerRepository(device())
        assertEquals(TimingMode.SCAN_ORDER, reopened.state.value.session!!.mode)
        assertEquals(listOf("A111111" to 1), reopened.records())
    }
}
