// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * What one request to the local AI is doing, shown while it runs. Passed along in the coroutine
 * context, so a request that was cancelled and keeps running in the plugin can't change the status
 * of the next one.
 */
class AiProgress : AbstractCoroutineContextElement(Key) {
    enum class Phase { LOADING, WAITING, GENERATING }

    companion object Key : CoroutineContext.Key<AiProgress>

    val start = android.os.SystemClock.uptimeMillis()

    /** null while nothing worth showing happens, e.g. a translation by the translation plugin */
    @Volatile
    var phase: Phase? = null

    val seconds get() = ((android.os.SystemClock.uptimeMillis() - start) / 1000).toInt()
}
