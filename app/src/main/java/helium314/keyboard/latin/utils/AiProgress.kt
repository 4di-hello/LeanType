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

    /** null while nothing worth showing happens, e.g. a translation by the translation plugin */
    @Volatile
    var phase: Phase? = null
        set(value) {
            if (value == Phase.GENERATING && field != Phase.GENERATING) generatingSince = android.os.SystemClock.uptimeMillis()
            field = value
        }

    /** when the model started on this request, 0 if not yet */
    @Volatile
    var generatingSince = 0L
        private set
}
