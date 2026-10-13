// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.cjk

import android.content.Context
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import helium314.keyboard.event.Event
import helium314.keyboard.latin.RichInputMethodSubtype
import helium314.keyboard.latin.utils.Log

/**
 * Coordinates CJK engine lifecycle, key event interception, composing text synchronization,
 * and candidate UI updates.
 */
class CjkInputManager private constructor(private val context: Context) {

    interface CjkUiListener {
        fun onCjkContextUpdated(contextData: CjkContextData)
        fun onCjkCompositionCleared()
    }

    private var uiListener: CjkUiListener? = null
    private var isComposing = false

    fun setUiListener(listener: CjkUiListener?) {
        this.uiListener = listener
    }

    /**
     * Checks if the currently active subtype is a CJK subtype (Chinese, Japanese, or Korean),
     * or uses a layout specifically designed for CJK composition.
     */
    fun isCjkSubtypeActive(subtype: RichInputMethodSubtype?): Boolean {
        if (subtype == null) return false
        val lang = subtype.locale.language.lowercase()
        if (lang == "zh" || lang == "ja" || lang == "ko") return true
        val layout = subtype.mainLayoutName.lowercase()
        if (layout.contains("pinyin") || layout.contains("wubi") || layout.contains("romaji")) return true
        return subtype.hasExtraValue("CjkEngine")
    }

    /**
     * Intercepts an input event if CJK is active.
     * Returns true if the event was consumed by the CJK engine and should bypass LatinIME InputLogic.
     */
    fun onEvent(event: Event, ic: InputConnection?, subtype: RichInputMethodSubtype?): Boolean {
        if (!isCjkSubtypeActive(subtype)) return false

        val engine = CjkPluginLoader.getEngine(context) ?: return false
        val keysym = KeycodeTranslator.toKeysym(event)
        if (keysym == 0) return false

        val mask = KeycodeTranslator.getModifierMask(event.metaState)
        val consumed = engine.processKey(keysym, mask)

        if (consumed) {
            syncStateWithInputConnection(engine, ic)
            return true
        }

        // If the engine did not consume the key, but we were composing, check if backspace or enter cleared it
        if (isComposing) {
            syncStateWithInputConnection(engine, ic)
        }

        return false
    }

    /**
     * Synchronizes engine state with the target application's InputConnection and the candidate UI.
     */
    private fun syncStateWithInputConnection(engine: ICjkEngine, ic: InputConnection?) {
        // 1. Commit text if any
        val commitText = engine.getCommit()
        if (!commitText.isNullOrEmpty()) {
            ic?.commitText(commitText, 1)
        }

        // 2. Fetch Pre-edit and Candidates
        val ctx = engine.getContext()
        if (ctx != null && ctx.preedit.isNotEmpty()) {
            isComposing = true
            ic?.setComposingText(ctx.preedit, 1)
            uiListener?.onCjkContextUpdated(ctx)
        } else {
            isComposing = false
            if (commitText.isNullOrEmpty()) {
                ic?.finishComposingText()
            }
            uiListener?.onCjkCompositionCleared()
        }
    }

    /**
     * Selects a candidate by page index and commits it to the InputConnection.
     */
    fun selectCandidate(index: Int, ic: InputConnection?): Boolean {
        val engine = CjkPluginLoader.getEngine(context) ?: return false
        val selected = engine.selectCandidate(index)
        if (selected) {
            syncStateWithInputConnection(engine, ic)
            return true
        }
        return false
    }

    /**
     * Clears active composition in both the engine and the InputConnection.
     */
    fun clearComposition(ic: InputConnection? = null) {
        val engine = CjkPluginLoader.getEngine(context)
        engine?.clearComposition()
        isComposing = false
        ic?.finishComposingText()
        uiListener?.onCjkCompositionCleared()
    }

    /**
     * Detects cursor jumping outside the active composing span and cleans up phantom state.
     */
    fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        composingSpanStart: Int, composingSpanEnd: Int,
        ic: InputConnection? = null
    ) {
        // If the composing span was discarded by the target app or cursor moved away, clear engine composition
        if (isComposing && (composingSpanStart < 0 || composingSpanEnd < 0)) {
            Log.d(TAG, "Cursor moved outside composing span; clearing CJK composition")
            clearComposition(ic)
        }
    }

    fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        // Ready for new input view session
    }

    fun onFinishInputView(finishingInput: Boolean, ic: InputConnection? = null) {
        if (isComposing) {
            clearComposition(ic)
        }
        CjkPluginLoader.getEngine(context)?.destroySession()
    }

    companion object {
        private const val TAG = "CjkInputManager"

        @Volatile
        private var instance: CjkInputManager? = null

        fun getInstance(context: Context): CjkInputManager =
            instance ?: synchronized(this) {
                instance ?: CjkInputManager(context.applicationContext).also { instance = it }
            }
    }
}
