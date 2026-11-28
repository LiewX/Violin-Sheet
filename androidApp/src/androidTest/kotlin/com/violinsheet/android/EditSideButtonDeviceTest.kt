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
 * The S Pen side button erases Symbols in Edit mode with any tool, decided at pen down (#27 part 3). adb can't hold
 * the button, so this injects stylus events carrying BUTTON_STYLUS_PRIMARY. It only drives the pen: the calling script
 * checks the database (Symbol rows, and the Ink under the swipe unchanged).
 * - `gesture=swipe`: with the `tool` ("Symbol" or "Lasso"), an optional plain pen tap at (`tapX`, `tapY`) first (to
 *   select a Symbol), then a button-held swipe from (`fromX`, `fromY`) to (`toX`, `toY`).
 * - `gesture=place`: Symbol tool, a drag from (`fromX`, `fromY`) to (`toX`, `toY`) whose events carry the button only
 *   from halfway, so it places at the lift and erases nothing.
 *
 * Arguments are screen pixels, plus `sheet` (default "Test Etude"). Run with
 * `source scripts/device.sh; device_test EditSideButtonDeviceTest -e gesture swipe -e tool Symbol -e fromX 1100 …`.
 */
@RunWith(AndroidJUnit4::class)
class EditSideButtonDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val device = UiDevice.getInstance(instrumentation)
    private val args = InstrumentationRegistry.getArguments()

    private fun arg(name: String) = args.getString(name)?.toFloat() ?: error("argument $name missing")

    private fun tapDesc(desc: String) {
        (device.wait(Until.findObject(By.desc(desc)), 5_000) ?: error("no \"$desc\"")).click()
        device.waitForIdle()
    }

    @Test
    fun sideButtonErasesSymbolsInEditMode() {
        val sheet = args.getString("sheet") ?: "Test Etude"
        val context = instrumentation.targetContext
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK),
        )
        device.wait(Until.findObject(By.text(sheet)), 5_000)?.click() ?: error("Sheet \"$sheet\" not in the list")
        assertNotNull(device.wait(Until.findObject(By.descStartsWith("Page 1 of")), 5_000))
        tapDesc("Edit mode")
        val from = arg("fromX") to arg("fromY")
        val to = arg("toX") to arg("toY")
        when (val gesture = args.getString("gesture")) {
            "swipe" -> {
                tapDesc(args.getString("tool") ?: "Symbol")
                if (args.getString("tapX") != null) {
                    val at = arg("tapX") to arg("tapY")
                    stylusPath(at, at, steps = 2) { 0 }
                    Thread.sleep(500)
                }
                stylusPath(from, to) { MotionEvent.BUTTON_STYLUS_PRIMARY }
            }
            "place" -> {
                tapDesc("Symbol")
                stylusPath(from, to) { i -> if (i >= 10) MotionEvent.BUTTON_STYLUS_PRIMARY else 0 }
            }
            else -> error("gesture \"$gesture\": swipe or place")
        }
        // Long enough for the write to land before the run ends and the app is killed.
        Thread.sleep(1_500)
    }

    /**
     * One pen gesture along the straight line [from] → [to] in [steps] moves; event i (0 = the down, steps + 1 = the
     * lift, which never carries a button) holds [buttons] of i.
     */
    private fun stylusPath(from: Pair<Float, Float>, to: Pair<Float, Float>, steps: Int = 20, buttons: (Int) -> Int) {
        val (fromX, fromY) = from
        val (toX, toY) = to
        val down = SystemClock.uptimeMillis()
        fun send(action: Int, i: Int, x: Float, y: Float) {
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
                down, down + i * 10L, action, 1, arrayOf(properties), arrayOf(coords), 0,
                if (action == MotionEvent.ACTION_UP) 0 else buttons(i), 1f, 1f, 0, 0,
                InputDevice.SOURCE_STYLUS or InputDevice.SOURCE_TOUCHSCREEN, 0,
            )
            instrumentation.uiAutomation.injectInputEvent(event, true)
            event.recycle()
        }
        send(MotionEvent.ACTION_DOWN, 0, fromX, fromY)
        for (i in 1..steps) {
            val t = i / steps.toFloat()
            send(MotionEvent.ACTION_MOVE, i, fromX + (toX - fromX) * t, fromY + (toY - fromY) * t)
        }
        send(MotionEvent.ACTION_UP, steps + 1, toX, toY)
    }
}
