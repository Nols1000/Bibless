package com.github.nols1000.bibless

import android.Manifest
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.github.nols1000.bibless.race.RunHint
import com.github.nols1000.bibless.race.TimingMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import com.google.mlkit.common.sdkinternal.MlKitContext
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Joining a run with its QR code before timing or scanning. */
@RunWith(AndroidJUnit4::class)
class JoinRunTest {
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

    private val hint = RunHint("run-1", "B".repeat(87), "Riverside 5k", TimingMode.SCAN_ORDER, "2026-10-10")
    private val device = RaceDevice(InMemoryStore(), AndroidRaceClock(ApplicationProvider.getApplicationContext()), CoroutineScope(Dispatchers.Default))
    private val timer = TimerRepository(device)

    @Test
    fun theJoinScreenTakesOnlyRunCodes() {
        var onCode: ((String) -> Unit)? = null
        var joined = false
        compose.setContent {
            JoinRunScreen(
                onCode = device::join,
                onJoined = { joined = true },
                onBack = {},
                camera = { code, modifier ->
                    onCode = code
                    Box(modifier)
                },
            )
        }

        compose.runOnIdle { onCode!!("A123456") }
        compose.onNodeWithText("That's not a run's QR code", substring = true).assertExists()
        assertNull(device.joinedRun.value)

        compose.runOnIdle { onCode!!(hint.toJson()) }
        compose.runOnIdle { assertEquals(true, joined) }
        assertEquals(hint, device.joinedRun.value)
    }

    @Test
    fun theTimerShowsTheRunAndRecordsForIt() {
        compose.setContent {
            App(BarcodeRepository(InMemoryStore(), Device.PHONE), timer, ScannerRepository(device), device)
        }
        compose.onNodeWithContentDescription("Timer").performClick()
        compose.onNodeWithText("No run").assertExists()

        device.join(hint.toJson())
        compose.onNodeWithText("Riverside 5k").assertExists()
        compose.onNodeWithText("Scan order", substring = true).assertExists()
        compose.onNodeWithText("Start").performClick()

        compose.onNodeWithText("Riverside 5k").assertExists()
        assertEquals("run-1", timer.state.value.session!!.run)
    }

    @Test
    fun leavingRecordsWithoutARun() {
        device.join(hint.toJson())
        compose.setContent {
            App(BarcodeRepository(InMemoryStore(), Device.PHONE), timer, ScannerRepository(device), device)
        }
        compose.onNodeWithContentDescription("Scanner").performClick()
        compose.onNodeWithText("Leave").performClick()

        compose.onNodeWithText("No run").assertExists()
        assertNull(device.joinedRun.value)
    }

    @Test
    fun joinRunOpensTheJoinScreen() {
        compose.setContent {
            App(BarcodeRepository(InMemoryStore(), Device.PHONE), timer, ScannerRepository(device), device)
        }
        compose.onNodeWithContentDescription("Timer").performClick()
        compose.onNodeWithText("Join run").performClick()

        compose.onNodeWithText("Join a run").assertExists()
    }
}
