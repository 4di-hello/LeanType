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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

class KlipyProvider(
    private val credentials: CredentialStore,
    private val registry: ThumbRegistry
) : GifProvider {
    companion object {
        const val ID = "klipy"
        private const val BASE_URL = "https://api.klipy.com/api/v1"
        private const val DEFAULT_LIMIT = 24
    }

    override val info: ProviderInfo
        get() = ProviderInfo(
            id = ID,
            displayName = "Klipy",
            kinds = Contract.KINDS_GIF or Contract.KINDS_STICKER or Contract.KINDS_ANIMATED_EMOJI or Contract.KINDS_MEME,
            capabilities = Contract.CAP_TRENDING or Contract.CAP_SEARCH,
            credentialFields = listOf("api_key"),
            configured = credentials.get(ID, "api_key")?.trim()?.isNotBlank() == true,
            needsNetwork = true,
            attribution = "Powered by Klipy"
        )

    private fun getApiKey(): String? = credentials.get(ID, "api_key")?.trim()?.takeIf { it.isNotBlank() }

    override suspend fun search(query: String, offset: String?, kind: Int): ProviderResult {
        if (query.isBlank()) {
            return trending(offset, kind)
        }
        val key = getApiKey() ?: return ProviderResult.Error(Contract.ERR_NOT_CONFIGURED, "Klipy API key required")
        val path = when (kind) {
            Contract.KIND_STICKER -> "stickers/search"
            Contract.KIND_MEME -> "static-memes/search"
            Contract.KIND_ANIMATED_EMOJI -> "emojis/search"
            else -> "gifs/search"
        }
        val urlBuilder = "$BASE_URL/$key/$path".toHttpUrlOrNull()?.newBuilder()
            ?: return ProviderResult.Error(Contract.ERR_OTHER, "Malformed URL")
        val pageNum = offset?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        urlBuilder.addQueryParameter("q", query)
        urlBuilder.addQueryParameter("per_page", DEFAULT_LIMIT.toString())
        urlBuilder.addQueryParameter("page", pageNum.toString())

        return executeRequest(urlBuilder.build(), pageNum, kind)
    }

    override suspend fun trending(offset: String?, kind: Int): ProviderResult {
        val key = getApiKey() ?: return ProviderResult.Error(Contract.ERR_NOT_CONFIGURED, "Klipy API key required")
        val path = when (kind) {
            Contract.KIND_STICKER -> "stickers/trending"
            Contract.KIND_MEME -> "static-memes/trending"
            Contract.KIND_ANIMATED_EMOJI -> "emojis/trending"
            else -> "gifs/trending"
        }
        val urlBuilder = "$BASE_URL/$key/$path".toHttpUrlOrNull()?.newBuilder()
            ?: return ProviderResult.Error(Contract.ERR_OTHER, "Malformed URL")
        val pageNum = offset?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        urlBuilder.addQueryParameter("per_page", DEFAULT_LIMIT.toString())
        urlBuilder.addQueryParameter("page", pageNum.toString())

        return executeRequest(urlBuilder.build(), pageNum, kind)
    }

    private suspend fun executeRequest(httpUrl: HttpUrl, currentPage: Int, kind: Int = Contract.KIND_GIF): ProviderResult {
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

            if (json.has("result") && !json.optBoolean("result", true)) {
                val errorMsg = json.optString("error").takeIf { it.isNotEmpty() }
                    ?: json.optString("message").takeIf { it.isNotEmpty() }
                    ?: "Klipy request failed"
                return ProviderResult.Error(Contract.ERR_NETWORK, errorMsg)
            }

            var hasNext = false
            val itemsArray: JSONArray? = when (val dataRaw = json.opt("data")) {
                is JSONObject -> {
                    hasNext = dataRaw.optBoolean("has_next", false)
                    dataRaw.optJSONArray("data")
                }
                is JSONArray -> {
                    val pagination = json.optJSONObject("pagination")
                    hasNext = pagination?.optString("next_offset")?.isNotEmpty() == true
                    dataRaw
                }
                else -> json.optJSONArray("results")
            }

            if (itemsArray == null || itemsArray.length() == 0) {
                return ProviderResult.Success(emptyList(), null)
            }

            val nextOffset = if (hasNext) {
                (currentPage + 1).toString()
            } else {
                json.optJSONObject("pagination")?.optString("next_offset")?.takeIf { it.isNotEmpty() }
                    ?: json.optString("next").takeIf { it.isNotEmpty() }
            }

            val parsedItems = mutableListOf<ParsedItem>()
            for (i in 0 until itemsArray.length()) {
                val itemObj = itemsArray.optJSONObject(i) ?: continue
                if (itemObj.optBoolean("is_ad", false) ||
                    itemObj.optString("type") == "ad" ||
                    itemObj.optString("content_type") == "ad"
                ) {
                    continue
                }

                val id = itemObj.opt("id")?.toString()?.takeIf { it.isNotEmpty() && it != "null" }
                    ?: itemObj.optString("slug").takeIf { it.isNotEmpty() }
                    ?: continue

                var previewUrl: String? = null
                var fullUrl: String? = null
                var width = 200
                var height = 200

                val fileObj = itemObj.optJSONObject("file")
                if (fileObj != null) {
                    fun parseFormat(sizeObj: JSONObject?): Pair<String, Pair<Int, Int>>? {
                        if (sizeObj == null) return null
                        val formatObj = sizeObj.optJSONObject("gif") ?: sizeObj.optJSONObject("webp") ?: return null
                        val url = formatObj.optString("url")
                        if (url.isEmpty()) return null
                        val w = formatObj.optInt("width", 200)
                        val h = formatObj.optInt("height", 200)
                        return Pair(url, Pair(w, h))
                    }

                    val previewCandidate = parseFormat(fileObj.optJSONObject("sm"))
                        ?: parseFormat(fileObj.optJSONObject("xs"))
                        ?: parseFormat(fileObj.optJSONObject("md"))
                        ?: parseFormat(fileObj.optJSONObject("hd"))

                    val fullCandidate = parseFormat(fileObj.optJSONObject("hd"))
                        ?: parseFormat(fileObj.optJSONObject("md"))
                        ?: parseFormat(fileObj.optJSONObject("sm"))
                        ?: parseFormat(fileObj.optJSONObject("xs"))

                    if (previewCandidate != null) {
                        previewUrl = previewCandidate.first
                        width = previewCandidate.second.first
                        height = previewCandidate.second.second
                    }
                    if (fullCandidate != null) {
                        fullUrl = fullCandidate.first
                    }
                } else if (itemObj.optJSONObject("media_formats") != null) {
                    val mediaFormats = itemObj.getJSONObject("media_formats")
                    val previewObj = mediaFormats.optJSONObject("tinygif")
                        ?: mediaFormats.optJSONObject("tinywebp")
                        ?: mediaFormats.optJSONObject("nanogif")
                        ?: mediaFormats.optJSONObject("gif")
                    if (previewObj != null) {
                        previewUrl = previewObj.optString("url")
                        val dims = previewObj.optJSONArray("dims")
                        if (dims != null && dims.length() >= 2) {
                            width = dims.getInt(0)
                            height = dims.getInt(1)
                        }
                    }
                    val fullObj = mediaFormats.optJSONObject("gif")
                        ?: mediaFormats.optJSONObject("mediumgif")
                        ?: previewObj
                    fullUrl = fullObj?.optString("url")
                } else if (itemObj.optJSONObject("images") != null) {
                    val images = itemObj.getJSONObject("images")
                    val previewObj = images.optJSONObject("fixed_height_small")
                        ?: images.optJSONObject("preview_gif")
                        ?: images.optJSONObject("fixed_width_small")
                        ?: images.optJSONObject("downsized")
                        ?: images.optJSONObject("original")
                    if (previewObj != null) {
                        previewUrl = previewObj.optString("url")
                        width = previewObj.optInt("width", 200)
                        height = previewObj.optInt("height", 200)
                    }
                    val fullObj = images.optJSONObject("original")
                        ?: images.optJSONObject("downsized_medium")
                        ?: previewObj
                    fullUrl = fullObj?.optString("url")
                }

                if (!previewUrl.isNullOrEmpty() && !fullUrl.isNullOrEmpty()) {
                    parsedItems.add(ParsedItem(id, previewUrl, fullUrl, width, height))
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
                                attribution = "Powered by Klipy",
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
