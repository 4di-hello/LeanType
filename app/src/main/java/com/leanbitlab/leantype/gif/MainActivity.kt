package com.leanbitlab.leantype.gif

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val textView = TextView(this).apply {
            text = "LeanType GIF Plugin\n\nActive and ready for LeanType Keyboard.\nYou can configure API keys in Keyboard Settings -> Plugins -> GIF Search."
            textSize = 18f
            setPadding(48, 48, 48, 48)
        }
        setContentView(textView)
    }
}
