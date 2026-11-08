// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.e2e

import android.app.Activity
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout

/**
 * Only in debug builds of LeanType+: a plain text field for the end-to-end tests in app/src/androidTest.
 * The field allows auto-correction and has no automatic capitalization, so typed letters stay lower case.
 */
class E2eTextFieldActivity : Activity() {
    lateinit var editText: EditText
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        editText = EditText(this).apply {
            id = android.R.id.edit
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
            hint = "E2E"
        }
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(editText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        setContentView(layout)
        editText.requestFocus()
    }
}
