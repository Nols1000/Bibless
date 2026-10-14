package com.github.nols1000.bibless

import android.app.Application
import android.app.KeyguardManager
import android.app.PendingIntent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeUp
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.nols1000.bibless.barcode.BarcodeFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadow.api.Shadow
import org.robolectric.shadows.ShadowTileService

/**
 * The Quick Settings tile and the barcode it opens over the lock screen. Robolectric has no system
 * UI, so this checks what the app hands the system: the tile's state, the activity it starts, and
 * that the activity asks to show over the lock and turn the screen on.
 */
@RunWith(AndroidJUnit4::class)
class LockScreenTest {
    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val repository = Bibless.repository(app)

    @Before
    fun setUp() {
        DemoData.load(repository)
        shadowOf(app.getSystemService(KeyguardManager::class.java)).setKeyguardLocked(true)
    }

    @Test
    @Config(shadows = [CollapsingTileService::class])
    fun theTileOffersTheFirstBarcodeAndOpensItOverTheLock() {
        val tile = Robolectric.setupService(BarcodeQuickSettingsTile::class.java)
        tile.onStartListening()
        assertEquals(Tile.STATE_INACTIVE, tile.qsTile.state)
        assertEquals("Me", tile.qsTile.subtitle)

        tile.onClick()
        val opened = checkNotNull(Shadow.extract<CollapsingTileService>(tile).opened) { "The tile opened nothing" }
        assertTrue(shadowOf(opened).isActivity)
        assertEquals(LockScreenActivity::class.java.name, shadowOf(opened).savedIntent.component?.className)
    }

    @Test
    fun theTileIsUnavailableWithoutABarcode() {
        repository.state.value.barcodes.forEach { repository.delete(it.id) }
        val tile = Robolectric.setupService(BarcodeQuickSettingsTile::class.java)
        tile.onStartListening()
        assertEquals(Tile.STATE_UNAVAILABLE, tile.qsTile.state)
        assertNull(tile.qsTile.subtitle)
    }

    @Test
    fun opensTheFirstBarcodeOverTheLockScreen() {
        ActivityScenario.launch<LockScreenActivity>(LockScreenActivity.intent(app)).use { scenario ->
            compose.onNodeWithText("A0123456").assertExists()
            scenario.onActivity {
                assertTrue(shadowOf(it).showWhenLocked)
                assertTrue(shadowOf(it).turnScreenOn)
            }
        }
    }

    @Test
    fun backClosesItWithoutOpeningTheList() {
        ActivityScenario.launch<LockScreenActivity>(LockScreenActivity.intent(app)).use { scenario ->
            compose.onNodeWithText("A0123456").assertExists()

            pressBackUnconditionally()
            compose.waitUntil(TIMEOUT) { scenario.isFinishing() }
            // Not the list: that stays behind the lock
            assertNull(shadowOf(app).nextStartedActivity)
        }
    }

    @Test
    fun swipesToTheOtherFormatButNotToTheOtherBarcodes() {
        repository.setDefaultFormat(Device.PHONE, BarcodeFormat.CODE128)
        ActivityScenario.launch<LockScreenActivity>(LockScreenActivity.intent(app)).use {
            awaitCode("Barcode for A0123456")
            compose.onNodeWithContentDescription("Barcode for A0123456").performTouchInput { swipeLeft() }
            awaitCode("QR code for A0123456")

            compose.onNodeWithContentDescription("QR code for A0123456").performTouchInput { swipeUp() }
            compose.waitForIdle()
            // The others stay behind the lock
            assertFalse(compose.onAllNodesWithText("A0246802").fetchSemanticsNodes().isNotEmpty())
            compose.onNodeWithContentDescription("QR code for A0123456").assertExists()
        }
    }

    @Test
    fun closesWhenThereIsNoBarcode() {
        repository.state.value.barcodes.forEach { repository.delete(it.id) }

        ActivityScenario.launch(LockScreenActivity::class.java).use { scenario ->
            compose.waitUntil(TIMEOUT) { scenario.isFinishing() }
        }
    }

    private fun awaitCode(description: String) {
        compose.waitUntil(TIMEOUT) {
            compose.onAllNodesWithContentDescription(description).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun ActivityScenario<LockScreenActivity>.isFinishing(): Boolean {
        var finishing = false
        onActivity { finishing = it.isFinishing }
        return finishing
    }

    /** Robolectric only knows the Intent overload that Android 14 deprecated; this records the PendingIntent one. */
    @Implements(TileService::class)
    class CollapsingTileService : ShadowTileService() {
        var opened: PendingIntent? = null

        @Implementation(minSdk = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        protected fun startActivityAndCollapse(pendingIntent: PendingIntent) {
            opened = pendingIntent
        }
    }

    private companion object {
        const val TIMEOUT = 5_000L
    }
}
