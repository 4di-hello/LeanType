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

class KlipyProvider(
    private val credentials: CredentialStore,
    private val registry: ThumbRegistry
) : GifProvider {
    companion object {
        const val ID = "klipy"
        private const val BASE_URL = "https://api.klipy.com/v1"
        private const val FALLBACK_PUBLIC_KEY = "klipy_public_v1"
    }

    override val info: ProviderInfo
        get() = ProviderInfo(
            id = ID,
            displayName = "Klipy",
            kinds = Contract.KIND_GIF,
            capabilities = Contract.CAP_TRENDING or Contract.CAP_SEARCH,
            credentialFields = listOf("api_key"),
            configured = true,
            needsNetwork = true,
            attribution = "Powered by Klipy"
        )

    private fun getApiKey(): String = credentials.get(ID, "api_key")?.takeIf { it.isNotBlank() } ?: FALLBACK_PUBLIC_KEY

    override suspend fun search(query: String, offset: String?): ProviderResult {
        val urlBuilder = "$BASE_URL/gifs/search".toHttpUrlOrNull()?.newBuilder() ?: return ProviderResult.Error(Contract.ERR_OTHER, "Malformed URL")
        urlBuilder.addQueryParameter("key", getApiKey())
        urlBuilder.addQueryParameter("q", query)
        urlBuilder.addQueryParameter("limit", "20")
        if (offset != null) urlBuilder.addQueryParameter("offset", offset)

        return executeRequest(urlBuilder.build())
    }

    override suspend fun trending(offset: String?): ProviderResult {
        val urlBuilder = "$BASE_URL/gifs/trending".toHttpUrlOrNull()?.newBuilder() ?: return ProviderResult.Error(Contract.ERR_OTHER, "Malformed URL")
        urlBuilder.addQueryParameter("key", getApiKey())
        urlBuilder.addQueryParameter("limit", "20")
        if (offset != null) urlBuilder.addQueryParameter("offset", offset)

        return executeRequest(urlBuilder.build())
    }

    private suspend fun executeRequest(httpUrl: okhttp3.HttpUrl): ProviderResult {
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
            val nextOffset = pagination?.optString("next_offset")?.takeIf { it.isNotEmpty() }

            val parsedItems = mutableListOf<ParsedItem>()
            for (i in 0 until data.length()) {
                val itemObj = data.getJSONObject(i)
                val id = itemObj.optString("id")
                val images = itemObj.optJSONObject("images") ?: continue
                val previewObj = images.optJSONObject("fixed_height_small")
                    ?: images.optJSONObject("preview_gif")
                    ?: images.optJSONObject("original")
                    ?: continue
                val previewUrl = previewObj.optString("url")
                val w = previewObj.optInt("width", 200)
                val h = previewObj.optInt("height", 200)

                val fullObj = images.optJSONObject("original") ?: previewObj
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
                                kind = Contract.KIND_GIF,
                                previewUri = contentUri,
                                width = parsed.w,
                                height = parsed.h,
                                isPack = false,
                                attribution = "Klipy",
                                extra = parsed.fullUrl
                            )
                        } else null
                    }
                }.awaitAll().filterNotNull()
            }

            ProviderResult.Success(finalItems, nextOffset)
        } catch (e: Exception) {
            ProviderResult.Error(Contract.ERR_NETWORK, e.message ?: "Network error")
        }
    }

    override suspend fun resolveFullMediaUrl(item: GifItem): String? = item.extra

    private data class ParsedItem(
        val id: String,
        val previewUrl: String,
        val fullUrl: String,
        val w: Int,
        val h: Int
    )
}
