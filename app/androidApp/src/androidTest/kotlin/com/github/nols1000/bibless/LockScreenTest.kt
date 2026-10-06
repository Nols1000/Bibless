package com.github.nols1000.bibless

import android.app.KeyguardManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.github.nols1000.bibless.barcode.BarcodeFormat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** The first barcode over the lock screen, from the Quick Settings tile. */
@RunWith(AndroidJUnit4::class)
class LockScreenTest {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val repository = Bibless.repository(context)
    private val keyguard = context.getSystemService(KeyguardManager::class.java)
    private val tile = ComponentName(context, BarcodeQuickSettingsTile::class.java).flattenToString()
    /** Whether the emulator had its lock screen turned off before; emulator images often do. */
    private var lockScreenWasDisabled = ""

    @Before
    fun setUp() {
        // DemoData replaces the saved barcodes, so never run this on a real device
        check(Build.HARDWARE == "ranchu") { "UI tests only run on an emulator (they replace saved barcodes)" }
        DemoData.load(repository)
        lockScreenWasDisabled = device.executeShellCommand("locksettings get-disabled").trim()
        device.executeShellCommand("locksettings set-disabled false")
        // Locked: the screen goes off, and comes back on to the lock screen
        device.sleep()
        check(device.wait({ !device.isScreenOn && keyguard.isKeyguardLocked }, TIMEOUT)) { "The device did not lock" }
    }

    @After
    fun tearDown() {
        device.executeShellCommand("cmd statusbar collapse")
        device.executeShellCommand("cmd statusbar remove-tile $tile")
        // Unlocked before the next test locks again, so the request can't reach that one's lock screen
        device.wakeUp()
        device.executeShellCommand("wm dismiss-keyguard")
        check(device.wait({ !keyguard.isKeyguardLocked }, TIMEOUT)) { "The device did not unlock" }
        device.executeShellCommand("locksettings set-disabled $lockScreenWasDisabled")
    }

    @Test
    fun theTileShowsTheFirstBarcodeWithoutUnlocking() {
        device.wakeUp()
        assertTrue(keyguard.isKeyguardLocked)
        device.executeShellCommand("cmd statusbar add-tile $tile")
        device.executeShellCommand("cmd statusbar expand-settings")

        // Enabled once the tile has caught up with the list; small tiles leave out the name
        val button = By.descStartsWith("Barcode").enabled(true)
        check(device.wait(Until.hasObject(button), TIMEOUT)) { "The tile does not offer the first barcode" }
        device.findObject(button).click()

        check(device.wait(Until.hasObject(By.text("A0123456")), TIMEOUT)) { "The first barcode was not shown" }
        assertTrue(keyguard.isKeyguardLocked)
    }

    @Test
    fun turnsTheScreenOnAndBackGoesToTheLockScreen() {
        context.startActivity(LockScreenActivity.intent(context))

        check(device.wait(Until.hasObject(By.text("A0123456")), TIMEOUT)) { "The first barcode was not shown" }
        assertTrue(device.isScreenOn)
        assertTrue(keyguard.isKeyguardLocked)

        device.pressBack()
        check(device.wait(Until.gone(By.text("A0123456")), TIMEOUT)) { "The barcode stayed open" }
        // Not the list: that stays behind the lock
        assertFalse(device.hasObject(By.text("Jamie (junior)")))
        assertTrue(keyguard.isKeyguardLocked)
    }

    @Test
    fun swipesToTheOtherFormatButNotToTheOtherBarcodes() {
        repository.setDefaultFormat(Device.PHONE, BarcodeFormat.CODE128)
        context.startActivity(LockScreenActivity.intent(context))

        val barcode = By.desc("Barcode for A0123456")
        check(device.wait(Until.hasObject(barcode), TIMEOUT)) { "The first barcode was not shown" }
        device.findObject(barcode).swipe(Direction.LEFT, 0.8f)
        val qrCode = By.desc("QR code for A0123456")
        check(device.wait(Until.hasObject(qrCode), TIMEOUT)) { "The QR code was not shown" }

        device.findObject(qrCode).swipe(Direction.UP, 0.8f)
        // The others stay behind the lock
        assertFalse(device.wait(Until.hasObject(By.text("A0246802")), 2_000L))
        assertTrue(device.hasObject(qrCode))
    }

    @Test
    fun closesWhenThereIsNoBarcode() {
        repository.state.value.barcodes.forEach { repository.delete(it.id) }
        device.wakeUp()

        val scenario = ActivityScenario.launch(LockScreenActivity::class.java)
        check(device.wait({ scenario.state == Lifecycle.State.DESTROYED }, TIMEOUT)) { "It stayed open" }
        assertEquals(Lifecycle.State.DESTROYED, scenario.state)
    }

    private fun UiDevice.wait(condition: () -> Boolean, timeout: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeout
        while (!condition()) {
            if (System.currentTimeMillis() > deadline) return false
            Thread.sleep(100)
        }
        return true
    }

    private companion object {
        const val TIMEOUT = 10_000L
    }
}
