// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.e2e

import android.app.Instrumentation
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Point
import android.os.SystemClock
import android.util.Log
import androidx.core.content.edit
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.SuggestedWords
import helium314.keyboard.latin.utils.prefs
import java.io.Closeable
import java.io.File

/** Helpers for the end-to-end tests of LeanType+: the real keyboard typing into a real text field. */
object E2e {
    const val TAG = "LeanTypeE2E"
    val instrumentation: Instrumentation get() = InstrumentationRegistry.getInstrumentation()
    val device: UiDevice get() = UiDevice.getInstance(instrumentation)
    val context: Context get() = instrumentation.targetContext
    val prefs: SharedPreferences get() = context.prefs()
    private val imeId get() = "${context.packageName}/${LatinIME::class.java.name}"

    /** Closes dialogs of the system (e.g. "isn't responding") that would cover the screen */
    fun closeSystemDialogs() {
        device.executeShellCommand("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS")
        device.findObject(androidx.test.uiautomator.By.text("Wait"))?.click()
    }

    /** Makes LeanType+ the current keyboard of the device */
    fun selectKeyboard() {
        closeSystemDialogs()
        log(device.executeShellCommand("ime enable $imeId"))
        log(device.executeShellCommand("ime set $imeId"))
    }

    fun setPrefs(block: SharedPreferences.Editor.() -> Unit) = prefs.edit(commit = true) { block() }

    fun <T> onMain(block: () -> T): T {
        var result: Result<T>? = null
        instrumentation.runOnMainSync { result = runCatching(block) }
        return result!!.getOrThrow()
    }

    fun waitFor(what: () -> String, timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val end = SystemClock.uptimeMillis() + timeoutMs
        while (SystemClock.uptimeMillis() < end) {
            if (condition()) return
            SystemClock.sleep(100)
        }
        val message = what()
        screenshot("timeout-" + message.take(40).replace(Regex("[^A-Za-z0-9]+"), "_"))
        throw AssertionError("timeout: $message")
    }

    /** Screenshots end up in the files of the app, the workflow fetches them with run-as */
    fun screenshot(name: String) {
        val dir = File(context.filesDir, "e2e").apply { mkdirs() }
        device.takeScreenshot(File(dir, "$name.png"))
    }

    fun log(message: String) = Log.i(TAG, message.trim())
}

/** The text field of [E2eTextFieldActivity] with the keyboard shown, and touches on its keys */
class TextField : Closeable {
    private val activity: E2eTextFieldActivity

    init {
        val intent = Intent(E2e.context, E2eTextFieldActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity = E2e.instrumentation.startActivitySync(intent) as E2eTextFieldActivity
        E2e.instrumentation.waitForIdleSync()
        // a real touch on the field, like a user
        val field = E2e.onMain {
            val location = IntArray(2)
            activity.editText.getLocationOnScreen(location)
            Point(location[0] + activity.editText.width / 2, location[1] + activity.editText.height / 2)
        }
        E2e.device.click(field.x, field.y)
        E2e.waitFor({ "keyboard shown: " + keyboardState() }) {
            E2e.onMain {
                val view = KeyboardSwitcher.getInstance().mainKeyboardView
                view != null && view.isShown && view.keyboard != null && view.width > 0
            }
        }
        SystemClock.sleep(800) // the keyboard slides in
        // LeanType has no built-in dictionaries: the test run puts the English one in place (.github/e2e/run.sh)
        E2e.waitFor({ "main dictionary loaded" }, 20_000) {
            E2e.onMain { LatinIME.getInstance()?.getDictionaryFacilitator()?.hasAtLeastOneInitializedMainDictionary() == true }
        }
    }

    val text: String get() = E2e.onMain { activity.editText.text.toString() }

    /** For the message when the keyboard does not appear */
    private fun keyboardState(): String = E2e.onMain {
        val view = KeyboardSwitcher.getInstance().mainKeyboardView
        val ime = LatinIME.getInstance()
        val current = android.provider.Settings.Secure.getString(E2e.context.contentResolver, android.provider.Settings.Secure.DEFAULT_INPUT_METHOD)
        "ime=${ime != null} inputView=${ime?.isInputViewShown} view=${view != null} shown=${view?.isShown} " +
            "keyboard=${view?.keyboard != null} width=${view?.width} focus=${activity.editText.hasFocus()} current=$current"
    }

    val elementId: Int? get() = E2e.onMain { KeyboardSwitcher.getInstance().keyboard?.mId?.mElementId }

    /** The words in the suggestion strip */
    val suggestions: List<String>
        get() = E2e.onMain {
            val inputLogic = LatinIME.getInstance()?.mInputLogic ?: return@onMain emptyList()
            val field = inputLogic.javaClass.getDeclaredField("mSuggestedWords").apply { isAccessible = true }
            val words = field.get(inputLogic) as SuggestedWords
            (0 until words.size()).map { words.getWord(it) }
        }

    private fun keyCenter(code: Int): Point = E2e.onMain {
        val view = KeyboardSwitcher.getInstance().mainKeyboardView ?: error("no keyboard view")
        val key = view.keyboard?.getKey(code) ?: error("no key for code $code")
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        Point(location[0] + view.paddingLeft + key.x + key.width / 2, location[1] + view.paddingTop + key.y + key.height / 2)
    }

    /** A touch in the middle of the key, through the whole touch handling of the keyboard */
    fun tap(code: Int) {
        val point = keyCenter(code)
        E2e.device.click(point.x, point.y)
        E2e.instrumentation.waitForIdleSync()
        SystemClock.sleep(TAP_PAUSE_MS)
    }

    /** Types lower case letters and spaces, '\b' is backspace */
    fun type(text: String) {
        for (c in text) tap(if (c == '\b') KeyCode.DELETE else c.code)
    }

    fun waitForText(expected: String, timeoutMs: Long = 5000) =
        E2e.waitFor({ "text \"$expected\", is \"$text\"" }, timeoutMs) { text == expected }

    override fun close() {
        E2e.onMain { activity.finish() }
        E2e.instrumentation.waitForIdleSync()
        SystemClock.sleep(300)
    }

    companion object {
        private const val TAP_PAUSE_MS = 120L
    }
}
