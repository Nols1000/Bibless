package com.github.nols1000.bibless

import android.Manifest
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.nols1000.bibless.race.TimingMode
import androidx.test.rule.GrantPermissionRule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import com.google.mlkit.common.sdkinternal.MlKitContext
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** The finish scanner, with a stand-in camera that "sees" whatever the test shows it. */
@RunWith(AndroidJUnit4::class)
class ScannerTest {
    @get:Rule
    val compose = createComposeRule()

    @get:Rule
    val camera: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.CAMERA)

    @Before
    fun startMlKit() {
        // On a device ML Kit starts with the app; the camera preview asks it for a scanner. Once per
        // process, like on a device, while Robolectric runs every test in the same one
        MlKitContext.initializeIfNeeded(ApplicationProvider.getApplicationContext())
    }

    private val clock = object : RaceClock {
        var now = 1_000_000L
        override fun wallMillis() = 1_700_000_000_000 + now
        override fun monotonicMillis() = now
        override fun bootId() = "boot"
    }
    private val device = RaceDevice(InMemoryStore(), clock, CoroutineScope(Dispatchers.Default))
    private val scanner = ScannerRepository(device)
    private var onCode: ((String) -> Unit)? = null

    private fun showScanner() = compose.setContent {
        val state by scanner.state.collectAsState()
        ScannerScreen(
            session = state.session,
            joinedRun = null,
            onJoin = {},
            onLeave = {},
            onSessions = {},
            onStart = scanner::start,
            onScan = scanner::scan,
            onEnter = scanner::enter,
            onUnknown = scanner::addUnknown,
            onUndo = scanner::undo,
            onStop = scanner::stop,
            onBack = {},
            camera = { code, modifier ->
                onCode = code
                Box(modifier)
            },
        )
    }

    /** The camera sees [code], after the previous one left its view. */
    private fun see(code: String) {
        clock.now += 2_000
        compose.runOnIdle { onCode!!(code) }
    }

    private fun records() = scanner.state.value.session!!.records.map { it.athleteId to it.position }

    @Test
    fun pairsARunnerWithTheirToken() {
        showScanner()
        compose.onNodeWithText("Start").performClick()
        compose.onNodeWithTag("nextStep").assertTextEquals("Scan a runner's barcode and their finish token")

        see("A123456")
        compose.onNodeWithTag("nextStep").assertTextEquals("Scan the finish token for A123456")
        compose.onNodeWithText("A123456: now scan their finish token").assertExists()

        see("P0042")
        compose.onNodeWithText("P42: A123456").assertExists()
        compose.onNodeWithText("1 finisher").assertExists()
        assertEquals(listOf("A123456" to 42), records())
    }

    @Test
    fun warnsAboutDuplicatesAndUnknownCodes() {
        scanner.start(TimingMode.TOKENS)
        showScanner()
        see("A123456")
        see("P1")

        see("A123456")
        compose.onNodeWithText("A123456 was already recorded.").assertExists()
        see("https://example.com")
        compose.onNodeWithText("Not an athlete barcode or finish token: https://example.com").assertExists()
    }

    @Test
    fun undoClearsTheHalfPairThenTheLastPair() {
        scanner.start(TimingMode.TOKENS)
        showScanner()
        compose.onNodeWithText("Undo").assertIsNotEnabled()
        see("A111111")
        see("P1")
        see("A222222")

        compose.onNodeWithText("Clear scan").performClick()
        compose.onNodeWithText("Undo P1").performClick()

        assertEquals(emptyList<Pair<String?, Int?>>(), records())
        compose.onNodeWithText("0 finishers").assertExists()
    }

    @Test
    fun typesACodeThatWontScan() {
        scanner.start(TimingMode.TOKENS)
        showScanner()
        see("P0007")

        compose.onNodeWithText("Type a code").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("a7654321")
        compose.onNode(hasText("Add") and hasAnyAncestor(isDialog())).performClick()

        assertEquals(listOf("A7654321" to 7), records())
    }

    @Test
    fun stoppingAsksFirst() {
        scanner.start(TimingMode.TOKENS)
        showScanner()
        compose.onNodeWithText("Stop").performClick()
        compose.onNode(hasText("Stop") and hasAnyAncestor(isDialog())).performClick()

        assertNull(scanner.state.value.session)
        compose.onNodeWithText("Start").assertExists()
    }

    @Test
    fun opensFromTheBarcodeList() {
        compose.setContent {
            App(BarcodeRepository(InMemoryStore(), Device.PHONE), TimerRepository(device), scanner, device)
        }
        compose.onNodeWithContentDescription("Scanner").performClick()
        compose.onNodeWithText("Start").performClick()

        compose.onNodeWithTag("nextStep").assertExists()
    }

    @Test
    fun scanOrderGivesEachRunnerTheNextPosition() {
        showScanner()
        compose.onNodeWithText("Scan order").performClick()
        compose.onNodeWithText("A missed or swapped scan moves everyone behind it.", substring = true).assertExists()
        compose.onNodeWithText("Start").performClick()
        compose.onNodeWithTag("nextPosition").assertTextEquals("Next: P1")

        see("A111111")
        see("A222222")
        compose.onNodeWithTag("nextPosition").assertTextEquals("Next: P3")
        compose.onNodeWithText("P2: A222222").assertExists()
        assertEquals(listOf("A111111" to 1, "A222222" to 2), records())

        compose.onNodeWithText("Undo P2").performClick()
        compose.onNodeWithTag("nextPosition").assertTextEquals("Next: P2")
    }

    @Test
    fun unknownFinishersTakeAPositionWithAnOptionalName() {
        scanner.start(TimingMode.SCAN_ORDER)
        showScanner()
        see("A111111")

        compose.onNodeWithText("No barcode").performClick()
        compose.onNodeWithText("Finisher P2 without a barcode").assertExists()
        compose.onNode(hasSetTextAction()).performTextInput("Sam")
        compose.onNode(hasText("Add") and hasAnyAncestor(isDialog())).performClick()

        compose.onNodeWithText("Sam (no barcode)").assertExists()
        assertEquals(listOf("A111111" to 1, null to 2), records())
    }

    @Test
    fun withTokensNoBarcodeNeedsTheTokenFirst() {
        scanner.start(TimingMode.TOKENS)
        showScanner()
        compose.onNodeWithText("No barcode").assertIsNotEnabled()

        see("P0009")
        compose.onNodeWithText("No barcode").performClick()
        compose.onNode(hasText("Add") and hasAnyAncestor(isDialog())).performClick()

        compose.onNodeWithText("P9: Unknown").assertExists()
        assertEquals(listOf(null to 9), records())
    }

    @Test
    fun aJoinedRunDecidesTheMode() {
        device.join(com.github.nols1000.bibless.race.RunHint("run-1", "B".repeat(87), "Riverside 5k", TimingMode.SCAN_ORDER).toJson())
        compose.setContent {
            val state by scanner.state.collectAsState()
            val joined by device.joinedRun.collectAsState()
            ScannerScreen(
                session = state.session, joinedRun = joined, onJoin = {}, onLeave = {}, onSessions = {},
                onStart = scanner::start, onScan = scanner::scan, onEnter = scanner::enter,
                onUnknown = scanner::addUnknown, onUndo = scanner::undo, onStop = scanner::stop, onBack = {},
                camera = { _, modifier -> Box(modifier) },
            )
        }
        compose.onNodeWithText("Finish tokens").assertDoesNotExist()
        compose.onNodeWithText("Start").performClick()

        assertEquals(TimingMode.SCAN_ORDER, scanner.state.value.session!!.mode)
    }
}
