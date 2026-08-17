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
            imm.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        // Termux-style terminal input contract (see termux-app TerminalView):
        // TYPE_NULL tells the IME this is a dumb terminal. IMEs with their
        // own composition (Vietnamese Telex, CJK) then compose INTERNALLY and
        // deliver finished characters via commitText / key events, instead of
        // revising previously-committed text — the revision dance is what a
        // mirror-diff implementation kept getting subtly wrong.
        // IME_ACTION_NONE is deliberately absent: it breaks newline input on
        // some keyboards (termux-app#221).
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or
            EditorInfo.IME_FLAG_NO_FULLSCREEN
        outAttrs.inputType = android.text.InputType.TYPE_NULL
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

        override fun getEditable(): Editable = view.editableText

        private fun logConn(message: String) {
            view.logInput("conn=$connectionId $message")
        }

        fun armSuppression() {
            logConn("armSuppression -> clearBuffer")
            clearBuffer()
        }

        fun invalidateImeMirror(restartIme: Boolean = false) {
            logConn("invalidateImeMirror restartIme=$restartIme")
            clearBuffer()
            if (restartIme) {
                view.inputMethodManager?.restartInput(view)
            }
        }

        private fun clearBuffer() {
            val editable = getEditable()
            removeComposingSpans(editable)
            if (editable.isNotEmpty()) editable.clear()
            Selection.setSelection(editable, 0)
        }

        // Termux pattern (termux-app TerminalView.onCreateInputConnection):
        // nothing is sent while the IME is composing; the whole editable is
        // sent on commit/finish and then dropped, and delete requests become
        // literal DEL bytes. No mirror, no diffing — nothing to desync.
        private fun sendEditableToTerminalAndClear(source: String) {
            val content = getEditable()
            if (content.isNotEmpty()) {
                val payload = buildString(content.length) {
                    for (ch in content) append(if (ch == '\n') '\r' else ch)
                }
                logConn("$source send=${view.describeText(payload)}")
                view.emitTerminalText(source, payload)
            }
            clearBuffer()
        }

        private fun consumeSuppressionCleanup(source: String): Boolean {
            if (!view.isSuppressionCleanupWindowActive()) return false
            logConn("$source swallowed by suppression window")
            clearBuffer()
            view.clearSuppression("$source cleanup")
            return true
        }

        override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
            logConn("commitText text=${view.describeText((text ?: "").toString())} cursor=$newCursorPosition")
            super.commitText(text ?: "", newCursorPosition)
            if (consumeSuppressionCleanup("commitText")) return true
            view.clearSuppression("commitText real input")
            sendEditableToTerminalAndClear("commitText")
            return true
        }

        override fun finishComposingText(): Boolean {
            logConn("finishComposingText")
            super.finishComposingText()
            if (consumeSuppressionCleanup("finishComposingText")) return true
            sendEditableToTerminalAndClear("finishComposingText")
            return true
        }

        override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
            logConn("deleteSurroundingText beforeLen=$beforeLength afterLen=$afterLength")
            if (consumeSuppressionCleanup("deleteSurroundingText")) {
                return super.deleteSurroundingText(beforeLength, afterLength)
            }
            // One payload per request — DEL bytes for the left side, forward
            // deletes for the right — instead of per-key bursts.
            val payload = buildString {
                repeat(beforeLength.coerceAtLeast(0)) { append('\u007F') }
                repeat(afterLength.coerceAtLeast(0)) { append("\u001b[3~") }
            }
            if (payload.isNotEmpty()) {
                view.emitTerminalText("deleteSurroundingText", payload)
            }
            return super.deleteSurroundingText(beforeLength, afterLength)
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
                "sendKeyEvent action=${event.action} keyCode=${event.keyCode} unicode=${event.unicodeChar} meta=${event.metaState}",
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
