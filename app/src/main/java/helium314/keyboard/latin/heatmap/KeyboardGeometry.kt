// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.heatmap

import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.common.Constants

/** [TypingHeatmap.KeyGeometry] for a [Keyboard] */
class KeyboardGeometry(private val keyboard: Keyboard, landscape: Boolean) : TypingHeatmap.KeyGeometry {
    override val layoutId = keyboard.mId.mSubtype.mainLayoutName + if (landscape) " (landscape)" else ""

    override val source: Any get() = keyboard

    override val aspectRatio: Float
        get() = if (keyboard.mOccupiedWidth > 0) keyboard.mOccupiedHeight.toFloat() / keyboard.mOccupiedWidth else 0.35f

    override fun keyRect(codePoint: Int): IntArray? {
        val key = keyboard.getKey(codePoint)
            ?: keyboard.getKey(Character.toUpperCase(codePoint))
            ?: keyboard.getKey(Character.toTitleCase(codePoint))
            ?: return null
        return intArrayOf(key.x, key.y, key.width, key.height)
    }

    override fun snapshot(): List<TypingHeatmap.KeyShape> {
        val w = keyboard.mOccupiedWidth.toFloat()
        val h = keyboard.mOccupiedHeight.toFloat()
        if (w <= 0 || h <= 0) return emptyList()
        return keyboard.sortedKeys.filter { it.width > 0 && it.height > 0 && !it.isSpacer }.map { key ->
            val label = when (key.code) {
                Constants.CODE_SPACE -> "␣"
                Constants.CODE_ENTER -> "⏎"
                KeyCode.DELETE -> "⌫"
                KeyCode.SHIFT -> "⇧"
                else -> key.label ?: ""
            }
            TypingHeatmap.KeyShape(
                Character.toLowerCase(key.code), label.lowercase(),
                key.x / w, key.y / h, key.width / w, key.height / h
            )
        }
    }
}
