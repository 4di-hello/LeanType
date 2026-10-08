package com.leanbitlab.leantype.gif.providers

import com.leanbitlab.leantype.gif.GifItem
import com.leanbitlab.leantype.gif.ProviderInfo

sealed class ProviderResult {
    data class Success(val items: List<GifItem>, val nextOffset: String?) : ProviderResult()
    data class Error(val errorCode: Int, val message: String) : ProviderResult()
}

interface GifProvider {
    val info: ProviderInfo
    suspend fun search(query: String, offset: String?): ProviderResult
    suspend fun trending(offset: String?): ProviderResult
    suspend fun resolveFullMediaUrl(item: GifItem): String?
}
