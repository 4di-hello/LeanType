package com.leanbitlab.leantype.gif.providers

import com.leanbitlab.leantype.gif.Contract
import com.leanbitlab.leantype.gif.GifItem
import com.leanbitlab.leantype.gif.ProviderInfo
import com.leanbitlab.leantype.gif.net.Downloader
import com.leanbitlab.leantype.gif.storage.CredentialStore
import com.leanbitlab.leantype.gif.storage.ThumbRegistry
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject

class GiphyProvider(
    private val credentials: CredentialStore,
    private val registry: ThumbRegistry
) : GifProvider {
    companion object {
        const val ID = "giphy"
        private const val BASE_URL_GIFS = "https://api.giphy.com/v1/gifs"
        private const val BASE_URL_STICKERS = "https://api.giphy.com/v1/stickers"
        private const val BASE_URL_EMOJIS = "https://api.giphy.com/v2/emoji"
        private const val DEFAULT_LIMIT = 20
    }

    override val info: ProviderInfo
        get() = ProviderInfo(
            id = ID,
            displayName = "GIPHY",
            kinds = Contract.KINDS_GIF or Contract.KINDS_STICKER or Contract.KINDS_ANIMATED_EMOJI,
            capabilities = Contract.CAP_TRENDING or Contract.CAP_SEARCH,
            credentialFields = listOf("api_key"),
            configured = credentials.get(ID, "api_key")?.isNotBlank() == true,
            needsNetwork = true,
            attribution = "Powered by GIPHY"
        )

    private fun getApiKey(): String? = credentials.get(ID, "api_key")?.takeIf { it.isNotBlank() }

    override suspend fun search(query: String, offset: String?, kind: Int): ProviderResult {
        val key = getApiKey() ?: return ProviderResult.Error(Contract.ERR_NOT_CONFIGURED, "GIPHY API key required")
        val offsetInt = offset?.toIntOrNull() ?: 0

        val targetUrl = when (kind) {
            Contract.KIND_STICKER -> "$BASE_URL_STICKERS/search"
            Contract.KIND_ANIMATED_EMOJI -> {
                if (query.isBlank()) BASE_URL_EMOJIS else "$BASE_URL_STICKERS/search"
            }
            else -> "$BASE_URL_GIFS/search"
        }

        val urlBuilder = targetUrl.toHttpUrlOrNull()?.newBuilder() ?: return ProviderResult.Error(Contract.ERR_OTHER, "Malformed URL")
        urlBuilder.addQueryParameter("api_key", key)
        if (kind == Contract.KIND_ANIMATED_EMOJI && query.isBlank()) {
            urlBuilder.addQueryParameter("limit", DEFAULT_LIMIT.toString())
            urlBuilder.addQueryParameter("offset", offsetInt.toString())
        } else {
            val q = if (kind == Contract.KIND_ANIMATED_EMOJI && !query.contains("emoji", ignoreCase = true)) {
                "$query emoji"
            } else {
                query
            }
            urlBuilder.addQueryParameter("q", q)
            urlBuilder.addQueryParameter("limit", DEFAULT_LIMIT.toString())
            urlBuilder.addQueryParameter("rating", "pg-13")
            urlBuilder.addQueryParameter("lang", "en")
            urlBuilder.addQueryParameter("offset", offsetInt.toString())
        }

        return executeRequest(urlBuilder.build(), offsetInt, kind)
    }

    override suspend fun trending(offset: String?, kind: Int): ProviderResult {
        val key = getApiKey() ?: return ProviderResult.Error(Contract.ERR_NOT_CONFIGURED, "GIPHY API key required")
        val offsetInt = offset?.toIntOrNull() ?: 0

        val targetUrl = when (kind) {
            Contract.KIND_STICKER -> "$BASE_URL_STICKERS/trending"
            Contract.KIND_ANIMATED_EMOJI -> BASE_URL_EMOJIS
            else -> "$BASE_URL_GIFS/trending"
        }

        val urlBuilder = targetUrl.toHttpUrlOrNull()?.newBuilder() ?: return ProviderResult.Error(Contract.ERR_OTHER, "Malformed URL")
        urlBuilder.addQueryParameter("api_key", key)
        urlBuilder.addQueryParameter("limit", DEFAULT_LIMIT.toString())
        if (kind != Contract.KIND_ANIMATED_EMOJI) {
            urlBuilder.addQueryParameter("rating", "pg-13")
        }
        urlBuilder.addQueryParameter("offset", offsetInt.toString())

        return executeRequest(urlBuilder.build(), offsetInt, kind)
    }

    private suspend fun executeRequest(httpUrl: okhttp3.HttpUrl, currentOffset: Int, kind: Int = Contract.KIND_GIF): ProviderResult {
        return try {
            val response = Downloader.get(httpUrl)
            if (!response.isSuccessful) {
                return when (response.code) {
                    429 -> ProviderResult.Error(Contract.ERR_RATE_LIMITED, "Rate limited")
                    401, 403 -> ProviderResult.Error(Contract.ERR_NOT_CONFIGURED, "Invalid API key")
                    else -> ProviderResult.Error(Contract.ERR_NETWORK, "HTTP ${response.code}")
                }
            }
            val bodyString = response.body?.string() ?: return ProviderResult.Error(Contract.ERR_NETWORK, "Empty response")
            val json = JSONObject(bodyString)
            val data = json.optJSONArray("data") ?: return ProviderResult.Success(emptyList(), null)
            val pagination = json.optJSONObject("pagination")
            val count = pagination?.optInt("count", data.length()) ?: data.length()
            val totalCount = pagination?.optInt("total_count", 0) ?: 0
            val nextOffset = if (currentOffset + count < totalCount && count > 0) {
                (currentOffset + count).toString()
            } else {
                null
            }

            val parsedItems = mutableListOf<ParsedItem>()
            for (i in 0 until data.length()) {
                val itemObj = data.getJSONObject(i)
                val id = itemObj.optString("id")
                if (id.isEmpty()) continue

                val images = itemObj.optJSONObject("images") ?: continue

                // Preferred preview: fixed_height_small or preview_gif or fixed_width_small
                val previewObj = images.optJSONObject("fixed_height_small")
                    ?: images.optJSONObject("preview_gif")
                    ?: images.optJSONObject("fixed_width_small")
                    ?: images.optJSONObject("downsized")
                    ?: continue
                val previewUrl = previewObj.optString("url")
                val w = previewObj.optInt("width", 200)
                val h = previewObj.optInt("height", 200)

                // Full media: original or downsized_medium
                val fullObj = images.optJSONObject("original")
                    ?: images.optJSONObject("downsized_medium")
                    ?: previewObj
                val fullUrl = fullObj.optString("url")

                if (previewUrl.isNotEmpty() && fullUrl.isNotEmpty()) {
                    parsedItems.add(ParsedItem(id, previewUrl, fullUrl, w, h))
                }
            }

            val finalItems = coroutineScope {
                parsedItems.map { parsed ->
                    async {
                        val contentUri = ThumbPrefetcher.prefetchAndRegister(registry, ID, parsed.previewUrl)
                        if (contentUri != null) {
                            GifItem(
                                id = parsed.id,
                                provider = ID,
                                kind = kind,
                                previewUri = contentUri,
                                width = parsed.w,
                                height = parsed.h,
                                isPack = false,
                                attribution = "Powered by GIPHY",
                                extra = parsed.fullUrl
                            )
                        } else {
                            null
                        }
                    }
                }.awaitAll().filterNotNull()
            }

            ProviderResult.Success(finalItems, nextOffset)
        } catch (e: Exception) {
            ProviderResult.Error(Contract.ERR_NETWORK, e.message ?: "Network error")
        }
    }

    override suspend fun resolveFullMediaUrl(item: GifItem): String? {
        return item.extra?.takeIf { it.isNotBlank() }
    }

    private data class ParsedItem(
        val id: String,
        val previewUrl: String,
        val fullUrl: String,
        val w: Int,
        val h: Int
    )
}
