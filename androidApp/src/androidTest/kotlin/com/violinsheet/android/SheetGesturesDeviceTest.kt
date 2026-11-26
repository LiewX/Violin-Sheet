package com.violinsheet.android

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Finger gestures on a real Sheet, on the tablet. adb's `input` can't pinch, so this drives two-finger gestures.
 * Needs a Sheet of at least 3 Pages in the list (argument `sheet`, default "Scales"), in landscape.
 * Run with `source scripts/device.sh; device_test SheetGesturesDeviceTest` (keeps the app's data, unlike
 * connectedAndroidTest, which uninstalls the app afterwards).
 */
@RunWith(AndroidJUnit4::class)
class SheetGesturesDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)

    @Before
    fun openSheet() {
        val sheet = InstrumentationRegistry.getArguments().getString("sheet") ?: "Scales"
        val context = instrumentation.targetContext
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        val row = device.wait(Until.findObject(By.text(sheet)), 5_000)
        assertNotNull("Sheet \"$sheet\" not in the list", row)
        row.click()
        assertNotNull(device.wait(Until.findObject(By.descStartsWith("Page 1 of")), 5_000))
    }

    private fun page(number: Int) = device.wait(Until.findObject(By.descStartsWith("Page $number of")), 2_000)


    @Test
    fun pinchZoomsSwipePansAndSwipePastEdgeTurns() {
        page(1).pinchOpen(0.7f)
        // The Page's label carries the zoom level ("Page 1 of 5, zoomed 250%").
        assertNotNull("pinch didn't zoom", device.wait(Until.findObject(By.descContains("zoomed")), 2_000))
        // For checking by eye that the zoomed view turns sharp: adb pull /sdcard/Android/data/com.violinsheet/files/zoomed.png
        Thread.sleep(1_000)
        device.takeScreenshot(File(instrumentation.targetContext.getExternalFilesDir(null), "zoomed.png"))

        // While zoomed, a short swipe pans and doesn't turn (at fit it would turn to Pages 2-3 or 3-4).
        val y = device.displayHeight / 2
        device.swipe(device.displayWidth / 2 + 150, y, device.displayWidth / 2 - 150, y, 20)
        device.waitForIdle()
        assertNull("a pan turned the page", device.wait(Until.findObject(By.descStartsWith("Page 3 of")), 1_000))

        // Swiping on keeps panning until the content's edge, then turns the page.
        repeat(8) {
            if (page(3) != null) return
            device.swipe(device.displayWidth - 100, y, 100, y, 20)
            device.waitForIdle()
        }
        assertNotNull("swiping past the edge didn't turn the page", page(3))
    }
}
