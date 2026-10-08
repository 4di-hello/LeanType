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

class TenorProvider(
    private val credentials: CredentialStore,
    private val registry: ThumbRegistry
) : GifProvider {
    companion object {
        const val ID = "tenor"
        private const val BASE_URL = "https://tenor.googleapis.com/v2"
    }

    override val info: ProviderInfo
        get() = ProviderInfo(
            id = ID,
            displayName = "Tenor",
            kinds = Contract.KIND_GIF,
            capabilities = Contract.CAP_TRENDING or Contract.CAP_SEARCH,
            credentialFields = listOf("api_key"),
            configured = credentials.get(ID, "api_key")?.isNotBlank() == true,
            needsNetwork = true,
            attribution = "Powered by Tenor"
        )

    private fun getApiKey(): String? = credentials.get(ID, "api_key")?.takeIf { it.isNotBlank() }

    override suspend fun search(query: String, offset: String?): ProviderResult {
        val key = getApiKey() ?: return ProviderResult.Error(Contract.ERR_NOT_CONFIGURED, "Tenor API key required")
        val urlBuilder = "$BASE_URL/search".toHttpUrlOrNull()?.newBuilder() ?: return ProviderResult.Error(Contract.ERR_OTHER, "Malformed URL")
        urlBuilder.addQueryParameter("key", key)
        urlBuilder.addQueryParameter("q", query)
        urlBuilder.addQueryParameter("limit", "20")
        urlBuilder.addQueryParameter("media_filter", "tinygif,gif,tinywebp")
        if (offset != null) urlBuilder.addQueryParameter("pos", offset)

        return executeRequest(urlBuilder.build())
    }

    override suspend fun trending(offset: String?): ProviderResult {
        val key = getApiKey() ?: return ProviderResult.Error(Contract.ERR_NOT_CONFIGURED, "Tenor API key required")
        val urlBuilder = "$BASE_URL/featured".toHttpUrlOrNull()?.newBuilder() ?: return ProviderResult.Error(Contract.ERR_OTHER, "Malformed URL")
        urlBuilder.addQueryParameter("key", key)
        urlBuilder.addQueryParameter("limit", "20")
        urlBuilder.addQueryParameter("media_filter", "tinygif,gif,tinywebp")
        if (offset != null) urlBuilder.addQueryParameter("pos", offset)

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
            val results = json.optJSONArray("results") ?: return ProviderResult.Success(emptyList(), null)
            val nextOffset = json.optString("next").takeIf { it.isNotEmpty() }

            val parsedItems = mutableListOf<ParsedItem>()
            for (i in 0 until results.length()) {
                val itemObj = results.getJSONObject(i)
                val id = itemObj.optString("id")
                val mediaFormats = itemObj.optJSONObject("media_formats") ?: continue
                val previewObj = mediaFormats.optJSONObject("tinywebp")
                    ?: mediaFormats.optJSONObject("tinygif")
                    ?: mediaFormats.optJSONObject("gif")
                    ?: continue
                val previewUrl = previewObj.optString("url")
                val dims = previewObj.optJSONArray("dims")
                val w = if (dims != null && dims.length() >= 2) dims.getInt(0) else 200
                val h = if (dims != null && dims.length() >= 2) dims.getInt(1) else 200

                val fullObj = mediaFormats.optJSONObject("gif") ?: previewObj
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
                                attribution = "Tenor",
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
