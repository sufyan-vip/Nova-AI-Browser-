package com.nova.browser.features.codeworkspace.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlin.system.measureTimeMillis

/** Minimal REST client backing the API tester in the code workspace. */
@Singleton
class ApiTester @Inject constructor(
    @Named("standard") private val client: OkHttpClient
) {
    data class ApiResponse(
        val code: Int,
        val message: String,
        val body: String,
        val headers: List<Pair<String, String>>,
        val durationMs: Long,
        val sizeBytes: Long
    ) {
        val isSuccess: Boolean get() = code in 200..299
    }

    /**
     * Sends a request. [headerLines] uses one `Name: value` pair per line.
     * Errors are returned as a failed [Result] rather than thrown.
     */
    suspend fun send(
        method: String,
        url: String,
        headerLines: String,
        body: String
    ): Result<ApiResponse> = withContext(Dispatchers.IO) {
        val target = url.trim()
        if (target.isBlank()) return@withContext Result.failure(IllegalArgumentException("Enter a URL"))
        if (!target.startsWith("http://") && !target.startsWith("https://")) {
            return@withContext Result.failure(IllegalArgumentException("The URL must start with http:// or https://"))
        }

        val headers = parseHeaders(headerLines)
        val contentType = headers.get("Content-Type")
            ?: headers.get("content-type")
            ?: "application/json"

        val requestBody = when (method.uppercase()) {
            "GET", "HEAD" -> null
            else -> body.toRequestBody(contentType.toMediaTypeOrNull())
        }

        val request = try {
            Request.Builder()
                .url(target)
                .headers(headers)
                .method(method.uppercase(), requestBody)
                .build()
        } catch (e: Exception) {
            return@withContext Result.failure(IllegalArgumentException("That URL isn't valid"))
        }

        try {
            var response: ApiResponse
            val elapsed = measureTimeMillis {
                client.newCall(request).execute().use { raw ->
                    val text = raw.body?.string().orEmpty()
                    response = ApiResponse(
                        code = raw.code,
                        message = raw.message.ifBlank { statusLabel(raw.code) },
                        body = prettify(text),
                        headers = raw.headers.map { it.first to it.second },
                        durationMs = 0,
                        sizeBytes = text.toByteArray().size.toLong()
                    )
                }
            }
            Result.success(response.copy(durationMs = elapsed))
        } catch (e: IOException) {
            Result.failure(IOException("Couldn't reach that endpoint. Check your connection and the URL."))
        } catch (e: Exception) {
            Result.failure(Exception("The request failed: ${e.message ?: "unknown error"}"))
        }
    }

    private fun parseHeaders(lines: String): Headers {
        val builder = Headers.Builder()
        lines.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && it.contains(":") }
            .forEach { line ->
                val name = line.substringBefore(":").trim()
                val value = line.substringAfter(":").trim()
                if (name.isNotEmpty() && value.isNotEmpty()) {
                    runCatching { builder.add(name, value) }
                }
            }
        return builder.build()
    }

    /** Pretty-prints JSON bodies; other content types are returned unchanged. */
    private fun prettify(text: String): String {
        val trimmed = text.trim()
        return try {
            when {
                trimmed.startsWith("{") -> JSONObject(trimmed).toString(2)
                trimmed.startsWith("[") -> JSONArray(trimmed).toString(2)
                else -> text
            }
        } catch (e: Exception) {
            text
        }
    }

    private fun statusLabel(code: Int): String = when (code) {
        200 -> "OK"
        201 -> "Created"
        204 -> "No Content"
        301, 302 -> "Redirect"
        400 -> "Bad Request"
        401 -> "Unauthorized"
        403 -> "Forbidden"
        404 -> "Not Found"
        429 -> "Too Many Requests"
        500 -> "Server Error"
        else -> "HTTP $code"
    }

    companion object {
        val METHODS = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD")
    }
}
