package com.jossephus.chuchu.ui.terminal

import android.content.Context
import android.os.SystemClock
import android.text.Editable
import android.text.Selection
import android.util.Log
import android.view.KeyEvent
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.ExtractedText
import android.view.inputmethod.ExtractedTextRequest
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.EditText

class TerminalInputView(context: Context) : EditText(context) {

    companion object {
        private const val LOG_TAG = "TerminalInput"
        private const val DEBUG_INPUT_LOGS = false
        private const val SUPPRESSION_CLEANUP_WINDOW_MS = 120L
    }

    var onTerminalText: ((String) -> Unit)? = null
    var onTerminalKey: ((Int, Int, Int, Int, Int) -> Unit)? = null

    /**
     * When true, suppress IME text input. Used to prevent double-sends
     * when accessory-bar virtual keys like Tab are tapped: we send the
     * key directly via [onTerminalKey], then ignore the IME's follow-up
     * cleanup edits within [SUPPRESSION_CLEANUP_WINDOW_MS].
     */
    @Volatile
    var suppressInput = false

    @Volatile
    private var suppressionEpoch = 0L

    @Volatile
    private var suppressionSnapshot = ""

    @Volatile
    private var suppressionDeadlineUptimeMs = 0L

    /** Active input connection, used for IME mirror resets. */
    private var activeInputConnection: TerminalInputConnection? = null

    /** Cached InputMethodManager for IME restarts. */
    private var inputMethodManager: InputMethodManager? = null

    private fun logInput(message: String) {
        if (!DEBUG_INPUT_LOGS) return
        Log.d(LOG_TAG, message)
    }

    private fun describeText(text: String): String = buildString {
        text.forEach { char ->
            when (char) {
                '\u001b' -> append("<ESC>")
                '\t' -> append("<TAB>")
                '\r' -> append("<CR>")
                '\n' -> append("<LF>")
                '\u007f' -> append("<BS>")
                else -> append(char)
            }
        }
    }

    /**
     * Keys that should drop the IME mirror buffer when delivered as
     * hardware/Ghostty key events. The terminal handles cursor motion
     * and line editing itself, so the mirror would only get out of sync.
     */
    private fun shouldInvalidateImeMirrorForKey(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_DEL ||
            keyCode == KeyEvent.KEYCODE_FORWARD_DEL ||
            keyCode == KeyEvent.KEYCODE_ENTER ||
            keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER ||
            keyCode == KeyEvent.KEYCODE_TAB ||
            keyCode == KeyEvent.KEYCODE_DPAD_UP ||
            keyCode == KeyEvent.KEYCODE_DPAD_DOWN ||
            keyCode == KeyEvent.KEYCODE_DPAD_LEFT ||
            keyCode == KeyEvent.KEYCODE_DPAD_RIGHT ||
            keyCode == KeyEvent.KEYCODE_MOVE_HOME ||
            keyCode == KeyEvent.KEYCODE_MOVE_END ||
            keyCode == KeyEvent.KEYCODE_PAGE_UP ||
            keyCode == KeyEvent.KEYCODE_PAGE_DOWN ||
            keyCode == KeyEvent.KEYCODE_INSERT ||
            keyCode == KeyEvent.KEYCODE_ESCAPE

    private fun shouldRestartImeAfterMirrorInvalidate(keyCode: Int): Boolean =
        keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER

    private fun emitTerminalText(source: String, text: String) {
        logInput("emit source=$source text=${describeText(text)}")
        onTerminalText?.invoke(text)
    }

    private fun emitBackspaceText(source: String) {
        logInput("emit source=$source text=<BS>")
        onTerminalText?.invoke("\u007f")
    }

    fun armInputSuppression(reason: String) {
        suppressInput = true
        suppressionSnapshot = editableText.toString()
        suppressionDeadlineUptimeMs = SystemClock.uptimeMillis() + SUPPRESSION_CLEANUP_WINDOW_MS
        val epoch = suppressionEpoch + 1
        suppressionEpoch = epoch
        logInput(
            "armSuppression reason=$reason epoch=$epoch snapshot=${describeText(suppressionSnapshot)}",
        )
        activeInputConnection?.armSuppression()
        inputMethodManager?.let { imm ->
            post {
                if (suppressInput && suppressionEpoch == epoch) {
                    logInput("armSuppression.restartInput epoch=$epoch")
                    imm.restartInput(this)
                }
            }
        }
    }

    private fun clearSuppression(reason: String) {
        if (!suppressInput && suppressionSnapshot.isEmpty()) return
        if (suppressInput) {
            logInput("clearSuppression reason=$reason")
        }
        suppressInput = false
        suppressionSnapshot = ""
        suppressionDeadlineUptimeMs = 0L
    }

    private fun isSuppressionCleanupWindowActive(): Boolean =
        suppressInput && SystemClock.uptimeMillis() <= suppressionDeadlineUptimeMs

    init {
        setBackgroundColor(android.graphics.Color.TRANSPARENT)
        setTextColor(android.graphics.Color.TRANSPARENT)
        isCursorVisible = false
        isFocusableInTouchMode = true
        isFocusable = true
        // Never let the framework auto-show the IME on focus: OEM keyboards
        // (Funtouch/MIUI) treat focus gains — including tab switches — as an
        // invitation to pop the keyboard. The IME is shown ONLY via the
        // explicit showSoftInput in [showKeyboard] (terminal tap, accessory).
        showSoftInputOnFocus = false
        setSingleLine(false)
        imeOptions =
            EditorInfo.IME_FLAG_NO_EXTRACT_UI or
                EditorInfo.IME_ACTION_NONE
        inputType = android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        logInput(
            "onKeyDown keyCode=$keyCode unicode=${event.unicodeChar} meta=${event.metaState} flags=${event.flags}",
        )
        val ghosttyAction = GhosttyKeyAction.fromAndroid(event.action, event.repeatCount)
        val mapped = KeyMapper.map(keyCode, event.unicodeChar, event.metaState)
        if (mapped != null && ghosttyAction != null) {
            clearSuppression("onKeyDown keyCode=$keyCode")
            if (ghosttyAction == GhosttyKeyAction.Press && shouldInvalidateImeMirrorForKey(keyCode)) {
                activeInputConnection?.invalidateImeMirror(
                    restartIme = shouldRestartImeAfterMirrorInvalidate(keyCode),
                )
            }
            onTerminalKey?.invoke(mapped.key, mapped.codepoint, mapped.mods, ghosttyAction, mapped.charCode)
            return true
        }
        val unicodeChar = event.unicodeChar
        if (unicodeChar != 0) {
            clearSuppression("onKeyDown.unicode keyCode=$keyCode")
            emitTerminalText("onKeyDown.unicode", unicodeChar.toChar().toString())
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        logInput("onKeyUp keyCode=$keyCode flags=${event.flags}")
        val ghosttyAction = GhosttyKeyAction.fromAndroid(event.action, event.repeatCount)
        val mapped = KeyMapper.map(keyCode, event.unicodeChar, event.metaState)
        if (mapped != null && ghosttyAction != null) {
            onTerminalKey?.invoke(mapped.key, mapped.codepoint, mapped.mods, ghosttyAction, mapped.charCode)
            return true
        }
        if (event.unicodeChar != 0) return true
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Take input focus (so hardware keys and the IME target this view)
     * WITHOUT summoning the soft keyboard. Used on automatic focus paths
     * like tab switches, where popping the IME uninvited is unwanted —
     * the keyboard still appears on an explicit tap via [showKeyboard].
     */
    fun takeFocusSilently(imm: InputMethodManager?) {
        if (imm != null) inputMethodManager = imm
        if (!hasFocus()) {
            // Plain requestFocus only — requestFocusFromTouch simulates a tap
            // and OEM IMEs (Funtouch) auto-show the keyboard for it.
            requestFocus()
        }
    }

    fun showKeyboard(imm: InputMethodManager?) {
        if (imm == null) return
        inputMethodManager = imm
        if (!hasFocus()) {
            requestFocus()
            requestFocusFromTouch()
        }
        post {
            logInput("showKeyboard.restartInput")
            imm.restartInput(this)
            // Flag 0 = explicit user request. SHOW_IMPLICIT is advisory and
            // OEM IMEs (Funtouch) ignore it once showSoftInputOnFocus=false.
            imm.showSoftInput(this, 0)
        }
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        // COMPOSING terminal contract. TYPE_NULL (the termux default) told
        // this user's keyboard to disable composition entirely — raw telex
        // letters came out, no diacritics at all. Declare a normal text box
        // (minus suggestions) so Vietnamese/CJK IMEs compose, and let the
        // echo-diff below mirror every revision to the terminal.
        // IME_ACTION_NONE is deliberately absent: it breaks newline input on
        // some keyboards (termux-app#221).
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or
            EditorInfo.IME_FLAG_NO_FULLSCREEN
        outAttrs.inputType = android.text.InputType.TYPE_CLASS_TEXT or
            android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE or
            android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        outAttrs.initialSelStart = selectionStart
        outAttrs.initialSelEnd = selectionEnd

        val conn = TerminalInputConnection(this)
        activeInputConnection = conn
        logInput("onCreateInputConnection conn=${conn.connectionId}")
        return conn
    }

    private class TerminalInputConnection(
        private val view: TerminalInputView,
    ) : BaseInputConnection(view, true) {

        val connectionId: Int = System.identityHashCode(this)

        private val maxContextChars = 1024

        /** Exactly what has been sent to the terminal for the current
         * context window. The editable is the source of truth; after every
         * IME operation [sync] diffs the editable against this and sends
         * DELs + the new suffix in ONE payload. There is no other emission
         * path — one code path, nothing to desync. */
        private var echoed: String = ""

        private var batchDepth = 0

        override fun getEditable(): Editable = view.editableText

        private fun logConn(message: String) {
            view.logInput("conn=$connectionId $message")
        }

        fun armSuppression() {
            logConn("armSuppression -> reset")
            resetSilently()
        }

        fun invalidateImeMirror(restartIme: Boolean = false) {
            logConn("invalidateImeMirror restartIme=$restartIme")
            resetSilently()
            if (restartIme) {
                view.inputMethodManager?.restartInput(view)
            }
        }

        /** Drop context and echo state without sending anything. Used when a
         * control key (Enter, arrows, Esc…) makes terminal-side state diverge
         * from the local text — from then on the old context is meaningless. */
        private fun resetSilently() {
            val editable = getEditable()
            removeComposingSpans(editable)
            if (editable.isNotEmpty()) editable.clear()
            Selection.setSelection(editable, 0)
            echoed = ""
        }

        /** Mirror the editable to the terminal: DELs for the abandoned
         * suffix, then the replacement, one payload. Newline submits the
         * line and resets the context window. */
        private fun sync(source: String) {
            if (batchDepth > 0) return
            val cur = getEditable().toString()
            if (cur == echoed) return

            if (view.isSuppressionCleanupWindowActive() &&
                (cur.isEmpty() || echoed.startsWith(cur))
            ) {
                logConn("$source swallowed by suppression window")
                resetSilently()
                view.clearSuppression("$source cleanup")
                return
            }
            view.clearSuppression("$source real input")

            val commonLen = echoed.zip(cur).takeWhile { it.first == it.second }.size
            val payload = buildString {
                repeat(echoed.length - commonLen) { append('\u007F') }
                for (ch in cur.substring(commonLen)) append(if (ch == '\n') '\r' else ch)
            }
            logConn("$source sync echoed=${view.describeText(echoed)} cur=${view.describeText(cur)}")
            if (payload.isNotEmpty()) {
                view.emitTerminalText(source, payload)
            }

            if (cur.contains('\n')) {
                resetSilently()
                return
            }
            echoed = cur

            if (echoed.length > maxContextChars) {
                // Trim the OLD half from the front of both, silently and in
                // lockstep, so diffs keep working on the recent tail.
                val cut = echoed.length / 2
                echoed = echoed.substring(cut)
                val editable = getEditable()
                editable.delete(0, cut)
                Selection.setSelection(editable, editable.length)
            }
        }

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            logConn("commitText text=${view.describeText((text ?: "").toString())}")
            val ok = super.commitText(text ?: "", newCursorPosition)
            sync("commitText")
            return ok
        }

        override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
            logConn("setComposingText text=${view.describeText((text ?: "").toString())}")
            val ok = super.setComposingText(text ?: "", newCursorPosition)
            sync("setComposingText")
            return ok
        }

        override fun setComposingRegion(start: Int, end: Int): Boolean {
            logConn("setComposingRegion $start..$end")
            val ok = super.setComposingRegion(start, end)
            sync("setComposingRegion")
            return ok
        }

        override fun finishComposingText(): Boolean {
            logConn("finishComposingText")
            val ok = super.finishComposingText()
            sync("finishComposingText")
            return ok
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            logConn("deleteSurroundingText before=$beforeLength after=$afterLength")
            val ok = super.deleteSurroundingText(beforeLength, afterLength)
            sync("deleteSurroundingText")
            return ok
        }

        override fun beginBatchEdit(): Boolean {
            batchDepth += 1
            return super.beginBatchEdit()
        }

        override fun endBatchEdit(): Boolean {
            val ok = super.endBatchEdit()
            if (batchDepth > 0) batchDepth -= 1
            if (batchDepth == 0) sync("endBatchEdit")
            return ok
        }

        override fun getExtractedText(request: ExtractedTextRequest?, flags: Int): ExtractedText {
            val editable = getEditable()
            return ExtractedText().apply {
                text = editable.toString()
                startOffset = 0
                partialStartOffset = -1
                partialEndOffset = -1
                selectionStart = Selection.getSelectionStart(editable).coerceAtLeast(0)
                selectionEnd = Selection.getSelectionEnd(editable).coerceAtLeast(0)
            }
        }

        override fun sendKeyEvent(event: KeyEvent): Boolean {
            logConn(
                "sendKeyEvent action=${event.action} keyCode=${event.keyCode} unicode=${event.unicodeChar}",
            )
            val ghosttyAction = GhosttyKeyAction.fromAndroid(event.action, event.repeatCount)
            if (ghosttyAction != null) {
                val mapped = KeyMapper.map(event.keyCode, event.unicodeChar, event.metaState)
                if (mapped != null) {
                    if (ghosttyAction == GhosttyKeyAction.Press && view.shouldInvalidateImeMirrorForKey(event.keyCode)) {
                        invalidateImeMirror(
                            restartIme = view.shouldRestartImeAfterMirrorInvalidate(event.keyCode),
                        )
                    }
                    view.onTerminalKey?.invoke(mapped.key, mapped.codepoint, mapped.mods, ghosttyAction, mapped.charCode)
                    return true
                }
            }
            return super.sendKeyEvent(event)
        }
    }
}
