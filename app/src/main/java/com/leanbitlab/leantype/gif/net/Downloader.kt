package com.leanbitlab.leantype.gif.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.ConnectionPool
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

object Downloader {
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 LeanTypeGifPlugin/1.0"

    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .writeTimeout(12, TimeUnit.SECONDS)
        .callTimeout(12, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .addInterceptor { chain ->
            val request = chain.request().newBuilder()
                .header("User-Agent", USER_AGENT)
                .build()
            chain.proceed(request)
        }
        .addNetworkInterceptor { chain ->
            val requestUrl = chain.request().url.toString()
            if (UrlFilter.validateUrl(requestUrl) == null) {
                throw IOException("Access to unapproved host is blocked: ${chain.request().url.host}")
            }
            chain.proceed(chain.request())
        }
        .connectionPool(ConnectionPool(5, 5, TimeUnit.MINUTES))
        .build()

    suspend fun get(httpUrl: HttpUrl): Response = suspendCancellableCoroutine { cont ->
        val request = Request.Builder().url(httpUrl).build()
        val call = client.newCall(request)
        cont.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (cont.isActive) cont.resumeWithException(e)
            }

            override fun onResponse(call: Call, response: Response) {
                cont.resume(response)
            }
        })
    }

    suspend fun downloadToFile(httpUrl: HttpUrl, destination: File, maxBytes: Long): Boolean = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(httpUrl).build()
        val call = client.newCall(request)
        val parent = destination.parentFile ?: return@withContext false
        if (!parent.exists()) parent.mkdirs()
        val tempFile = File.createTempFile("dl_", ".tmp", parent)
        try {
            val response = suspendCancellableCoroutine<Response> { cont ->
                cont.invokeOnCancellation { call.cancel() }
                call.enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        if (cont.isActive) cont.resumeWithException(e)
                    }

                    override fun onResponse(call: Call, resp: Response) {
                        cont.resume(resp)
                    }
                })
            }

            response.use { resp ->
                if (!resp.isSuccessful) return@withContext false
                val body = resp.body ?: return@withContext false
                val contentLength = body.contentLength()
                if (contentLength > maxBytes) return@withContext false

                body.byteStream().use { input ->
                    FileOutputStream(tempFile).use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        var totalRead = 0L
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            totalRead += bytesRead
                            if (totalRead > maxBytes) {
                                return@withContext false
                            }
                            output.write(buffer, 0, bytesRead)
                        }
                        output.fd.sync()
                    }
                }
            }

            if (!tempFile.renameTo(destination)) {
                tempFile.copyTo(destination, overwrite = true)
                tempFile.delete()
            }
            true
        } catch (_: Exception) {
            false
        } finally {
            if (tempFile.exists()) tempFile.delete()
        }
    }
}
