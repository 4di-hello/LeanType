package com.leanbitlab.leantype.gif.net

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object UrlFilter {
    private val ALLOWED_DOMAINS = setOf(
        "tenor.com",
        "giphy.com",
        "klipy.com",
        "google.com",
        "gstatic.com"
    )

    fun validateUrl(rawUrl: String): HttpUrl? {
        val httpUrl = rawUrl.toHttpUrlOrNull() ?: return null
        if (httpUrl.scheme != "https") return null
        if (httpUrl.username.isNotEmpty() || httpUrl.password.isNotEmpty()) return null
        if (httpUrl.port != 443) return null

        val host = httpUrl.host.lowercase()
        val isAllowed = ALLOWED_DOMAINS.any { allowed ->
            host == allowed || host.endsWith(".$allowed")
        }
        return if (isAllowed) httpUrl else null
    }
}
