package com.violinsheet.android

import android.content.Intent
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Holding the S Pen side button erases, and the pen draws again once it is released. adb can't hold the button, so
 * this injects stylus events carrying BUTTON_STYLUS_PRIMARY. It only drives the pen: check the result in the database
 * (the stroke under the first swipe is erased or cut, a new pen stroke from the second swipe is there).
 * Arguments: `sheet` (default "Test Etude"), `eraseX`, `eraseFromY`, `eraseToY`, `drawY` in screen pixels.
 * Run with `source scripts/device.sh; device_test SideButtonEraseDeviceTest`.
 */
@RunWith(AndroidJUnit4::class)
class SideButtonEraseDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val args = InstrumentationRegistry.getArguments()

    private fun arg(name: String, default: Float) = args.getString(name)?.toFloat() ?: default

    @Test
    fun sideButtonErasesWhileHeldThenPenDrawsAgain() {
        val sheet = args.getString("sheet") ?: "Test Etude"
        val context = instrumentation.targetContext
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        device.wait(Until.findObject(By.text(sheet)), 5_000)?.click() ?: error("Sheet \"$sheet\" not in the list")
        assertNotNull(device.wait(Until.findObject(By.descStartsWith("Page 1 of")), 5_000))
        device.findObject(By.desc("Pen")).click()
        device.waitForIdle()

        val x = arg("eraseX", 1650f)
        stylusSwipe(x, arg("eraseFromY", 450f), x, arg("eraseToY", 550f), MotionEvent.BUTTON_STYLUS_PRIMARY)
        Thread.sleep(500)
        val y = arg("drawY", 300f)
        stylusSwipe(1000f, y, 1400f, y, buttons = 0)
        Thread.sleep(500)
        // The toolbar shows the pen again after the button is released (Compose reports a selected Choice as checked).
        assertTrue("pen not chosen after release", device.findObject(By.desc("Pen")).isChecked)
    }

    private fun stylusSwipe(fromX: Float, fromY: Float, toX: Float, toY: Float, buttons: Int) {
        val down = SystemClock.uptimeMillis()
        val steps = 20
        fun send(action: Int, at: Long, x: Float, y: Float) {
            val properties = MotionEvent.PointerProperties().apply {
                id = 0
                toolType = MotionEvent.TOOL_TYPE_STYLUS
            }
            val coords = MotionEvent.PointerCoords().apply {
                this.x = x
                this.y = y
                pressure = 0.5f
                size = 0.01f
            }
            val event = MotionEvent.obtain(
                down, at, action, 1, arrayOf(properties), arrayOf(coords), 0,
                if (action == MotionEvent.ACTION_UP) 0 else buttons, 1f, 1f, 0, 0,
                InputDevice.SOURCE_STYLUS or InputDevice.SOURCE_TOUCHSCREEN, 0,
            )
            instrumentation.uiAutomation.injectInputEvent(event, true)
            event.recycle()
        }
        send(MotionEvent.ACTION_DOWN, down, fromX, fromY)
        for (i in 1..steps) {
            val t = i / steps.toFloat()
            send(MotionEvent.ACTION_MOVE, down + i * 10L, fromX + (toX - fromX) * t, fromY + (toY - fromY) * t)
        }
        send(MotionEvent.ACTION_UP, down + (steps + 1) * 10L, toX, toY)
    }
}
