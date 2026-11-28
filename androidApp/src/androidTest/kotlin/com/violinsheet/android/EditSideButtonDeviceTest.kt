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
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The S Pen side button does nothing in Edit mode. adb can't hold the button, so this injects stylus events carrying
 * BUTTON_STYLUS_PRIMARY. It only drives the pen: check the result in the database (the Ink under the swipe is
 * unchanged; the tap placed exactly one Symbol).
 * - Select tool: a button-held swipe from (`swipeX`, `swipeFromY`) to (`swipeX`, `swipeToY`), across Ink.
 * - Symbol tool: a button-held tap at (`tapX`, `tapY`) on empty Page.
 *
 * Arguments (screen pixels) as above, plus `sheet` (default "Test Etude"). Run with
 * `source scripts/device.sh; device_test EditSideButtonDeviceTest -e swipeX 1650 …`.
 */
@RunWith(AndroidJUnit4::class)
class EditSideButtonDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val args = InstrumentationRegistry.getArguments()

    private fun arg(name: String, default: Float) = args.getString(name)?.toFloat() ?: default

    private fun tapDesc(desc: String) {
        (device.wait(Until.findObject(By.desc(desc)), 5_000) ?: error("no \"$desc\"")).click()
        device.waitForIdle()
    }

    @Test
    fun sideButtonDoesNothingInEditMode() {
        val sheet = args.getString("sheet") ?: "Test Etude"
        val context = instrumentation.targetContext
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        device.wait(Until.findObject(By.text(sheet)), 5_000)?.click() ?: error("Sheet \"$sheet\" not in the list")
        assertNotNull(device.wait(Until.findObject(By.descStartsWith("Page 1 of")), 5_000))
        tapDesc("Edit mode")

        tapDesc("Select")
        val x = arg("swipeX", 1650f)
        stylusPath(listOf(x to arg("swipeFromY", 450f), x to arg("swipeToY", 550f)), MotionEvent.BUTTON_STYLUS_PRIMARY)
        Thread.sleep(500)

        tapDesc("Symbol")
        val at = arg("tapX", 1000f) to arg("tapY", 300f)
        stylusPath(listOf(at, at), MotionEvent.BUTTON_STYLUS_PRIMARY, steps = 2)
        // Long enough for the placement's write to land before the run ends and the app is killed.
        Thread.sleep(1_500)
    }

    /** One pen gesture along the straight line between [points]' first and last, with [buttons] held until the lift. */
    private fun stylusPath(points: List<Pair<Float, Float>>, buttons: Int, steps: Int = 20) {
        val (fromX, fromY) = points.first()
        val (toX, toY) = points.last()
        val down = SystemClock.uptimeMillis()
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
