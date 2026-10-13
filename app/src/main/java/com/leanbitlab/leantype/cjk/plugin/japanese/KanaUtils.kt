// SPDX-License-Identifier: GPL-3.0-only
package com.leanbitlab.leantype.cjk.plugin.japanese

/**
 * Utility functions for Japanese kana transformations:
 * - Hiragana to Full-width Katakana (algorithmic offset)
 * - Katakana to Half-width Katakana (Hankaku Katakana)
 */
object KanaUtils {

    /**
     * Converts a Hiragana string to Full-width Katakana.
     * Hiragana range: \u3041 (ぁ) to \u3096 (ゖ)
     * Katakana range: \u30A1 (ァ) to \u30F6 (ヶ)
     * Difference is exactly 0x60 (96).
     */
    fun hiraganaToKatakana(hiragana: String): String {
        val sb = StringBuilder(hiragana.length)
        for (ch in hiragana) {
            if (ch in '\u3041'..'\u3096') {
                sb.append((ch.code + 0x60).toChar())
            } else {
                sb.append(ch)
            }
        }
        return sb.toString()
    }

    /**
     * Converts a Full-width Katakana string to Half-width Katakana.
     */
    fun katakanaToHalfWidth(katakana: String): String {
        val sb = StringBuilder(katakana.length * 2)
        for (ch in katakana) {
            when (ch) {
                'ア' -> sb.append("ｱ")
                'イ' -> sb.append("ｲ")
                'ウ' -> sb.append("ｳ")
                'エ' -> sb.append("ｴ")
                'オ' -> sb.append("ｵ")
                'カ' -> sb.append("ｶ")
                'キ' -> sb.append("ｷ")
                'ク' -> sb.append("ｸ")
                'ケ' -> sb.append("ｹ")
                'コ' -> sb.append("ｺ")
                'サ' -> sb.append("ｻ")
                'シ' -> sb.append("ｼ")
                'ス' -> sb.append("ｽ")
                'セ' -> sb.append("ｾ")
                'ソ' -> sb.append("ｿ")
                'タ' -> sb.append("ﾀ")
                'チ' -> sb.append("ﾁ")
                'ツ' -> sb.append("ﾂ")
                'テ' -> sb.append("ﾃ")
                'ト' -> sb.append("ﾄ")
                'ナ' -> sb.append("ﾅ")
                'ニ' -> sb.append("ﾆ")
                'ヌ' -> sb.append("ﾇ")
                'ネ' -> sb.append("ﾈ")
                'ノ' -> sb.append("ﾉ")
                'ハ' -> sb.append("ﾊ")
                'ヒ' -> sb.append("ﾋ")
                'フ' -> sb.append("ﾌ")
                'ヘ' -> sb.append("ﾍ")
                'ホ' -> sb.append("ﾎ")
                'マ' -> sb.append("ﾏ")
                'ミ' -> sb.append("ﾐ")
                'ム' -> sb.append("ﾑ")
                'メ' -> sb.append("ﾒ")
                'モ' -> sb.append("ﾓ")
                'ヤ' -> sb.append("ﾔ")
                'ユ' -> sb.append("ﾕ")
                'ヨ' -> sb.append("ﾖ")
                'ラ' -> sb.append("ﾗ")
                'リ' -> sb.append("ﾘ")
                'ル' -> sb.append("ﾙ")
                'レ' -> sb.append("ﾚ")
                'ロ' -> sb.append("ﾛ")
                'ワ' -> sb.append("ﾜ")
                'ヲ' -> sb.append("ｦ")
                'ン' -> sb.append("ﾝ")
                'ガ' -> sb.append("ｶﾞ")
                'ギ' -> sb.append("ｷﾞ")
                'グ' -> sb.append("ｸﾞ")
                'ゲ' -> sb.append("ｹﾞ")
                'ゴ' -> sb.append("ｺﾞ")
                'ザ' -> sb.append("ｻﾞ")
                'ジ' -> sb.append("ｼﾞ")
                'ズ' -> sb.append("ｽﾞ")
                'ゼ' -> sb.append("ｾﾞ")
                'ゾ' -> sb.append("ｿﾞ")
                'ダ' -> sb.append("ﾀﾞ")
                'ヂ' -> sb.append("ﾁﾞ")
                'ヅ' -> sb.append("ﾂﾞ")
                'デ' -> sb.append("ﾃﾞ")
                'ド' -> sb.append("ﾄﾞ")
                'バ' -> sb.append("ﾊﾞ")
                'ビ' -> sb.append("ﾋﾞ")
                'ブ' -> sb.append("ﾌﾞ")
                'ベ' -> sb.append("ﾍﾞ")
                'ボ' -> sb.append("ﾎﾞ")
                'パ' -> sb.append("ﾊﾟ")
                'ピ' -> sb.append("ﾋﾟ")
                'プ' -> sb.append("ﾌﾟ")
                'ペ' -> sb.append("ﾍﾟ")
                'ポ' -> sb.append("ﾎﾟ")
                'ヴ' -> sb.append("ｳﾞ")
                'ァ' -> sb.append("ｧ")
                'ィ' -> sb.append("ｨ")
                'ゥ' -> sb.append("ｩ")
                'ェ' -> sb.append("ｪ")
                'ォ' -> sb.append("ｫ")
                'ッ' -> sb.append("ｯ")
                'ャ' -> sb.append("ｬ")
                'ュ' -> sb.append("ｭ")
                'ョ' -> sb.append("ｮ")
                'ー' -> sb.append("ｰ")
                '。' -> sb.append("｡")
                '「' -> sb.append("｢")
                '」' -> sb.append("｣")
                '、' -> sb.append("､")
                '・' -> sb.append("･")
                else -> sb.append(ch)
            }
        }
        return sb.toString()
    }
}
