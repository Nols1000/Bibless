package com.github.nols1000.bibless

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.intent.Intents
import androidx.test.espresso.intent.Intents.intended
import androidx.test.espresso.intent.Intents.intending
import androidx.test.espresso.intent.matcher.IntentMatchers.hasAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.github.nols1000.bibless.race.RunLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Sharing recorded sessions with the organiser. */
@RunWith(AndroidJUnit4::class)
class SessionsTest {
    @get:Rule
    val compose = createComposeRule()

    private val device = RaceDevice(
        InMemoryStore(),
        AndroidRaceClock(InstrumentationRegistry.getInstrumentation().targetContext),
        CoroutineScope(Dispatchers.Default),
    )
    private val timer = TimerRepository(device)
    private val scanner = ScannerRepository(device)

    @Before
    fun setUp() = Intents.init()

    @After
    fun tearDown() = Intents.release()

    private fun showApp(share: ((List<ExportFile>) -> Unit)? = null) = compose.setContent {
        App(BarcodeRepository(InMemoryStore(), Device.PHONE), timer, scanner, device, shareFiles = share)
    }

    private fun recordATimerSession() {
        timer.start()
        timer.tap()
        timer.tap()
        timer.stop()
    }

    @Test
    fun listsSessionsAndSharesTheirFiles() {
        recordATimerSession()
        var shared: List<ExportFile>? = null
        showApp { shared = it }
        compose.onNodeWithContentDescription("Timer").performClick()
        compose.onNodeWithText("Recorded sessions").performClick()
        compose.onNodeWithText("2 finish times · no run").assertExists()

        compose.onNodeWithTag("share-${timer.sessionIds().single()}").performClick()
        compose.waitUntil(5_000) { shared != null }

        val files = shared!!
        assertEquals(listOf("csv", "json"), files.map { it.name.substringAfterLast('.') })
        assertTrue(files[0].name, files[0].name.startsWith("bibless-timer-"))
        assertEquals(3, files[0].content.lines().filter { it.isNotEmpty() }.size)
        assertEquals(3, RunLog.fromJson(files[1].content).events.size)
    }

    @Test
    fun sharingOpensTheShareSheetWithBothFiles() {
        recordATimerSession()
        intending(hasAction(Intent.ACTION_CHOOSER)).respondWith(Instrumentation.ActivityResult(Activity.RESULT_OK, null))
        showApp()
        compose.onNodeWithContentDescription("Timer").performClick()
        compose.onNodeWithText("Recorded sessions").performClick()
        compose.onNodeWithText("Share").performClick()

        compose.waitUntil(5_000) { runCatching { intended(hasAction(Intent.ACTION_CHOOSER)) }.isSuccess }
        val chooser = Intents.getIntents().last { it.action == Intent.ACTION_CHOOSER }
        @Suppress("DEPRECATION")
        val send = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        @Suppress("DEPRECATION")
        val uris = send.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)!!
        assertEquals(2, uris.size)
        val resolver = InstrumentationRegistry.getInstrumentation().targetContext.contentResolver
        val csv = resolver.openInputStream(uris.first { it.path!!.endsWith(".csv") })!!.bufferedReader().readText()
        assertTrue(csv, csv.startsWith("position,time,elapsed_ms\n1,"))
    }

    @Test
    fun withoutSessionsTheListExplainsItself() {
        showApp()
        compose.onNodeWithContentDescription("Scanner").performClick()
        compose.onNodeWithText("Recorded sessions").performClick()
        compose.onNodeWithText("Timer and scanner sessions show up here", substring = true).assertExists()
    }
}
