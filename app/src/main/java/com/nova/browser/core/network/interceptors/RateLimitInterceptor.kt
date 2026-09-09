package com.nova.browser.core.network.interceptors

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.min

/**
 * Client-side throttle + exponential backoff for 429/5xx responses
 * (spec 06_AI_ENGINE → rate limiting handler, retry with backoff).
 */
@Singleton
class RateLimitInterceptor @Inject constructor() : Interceptor {

    companion object {
        private const val MIN_INTERVAL_MS = 250L
        private const val MAX_RETRIES = 3
        private const val BASE_BACKOFF_MS = 800L
        private const val MAX_BACKOFF_MS = 8_000L
    }

    private val lastRequestAt = AtomicLong(0)

    @Volatile
    var retryAfterUntil: Long = 0
        private set

    override fun intercept(chain: Interceptor.Chain): Response {
        throttle()

        var attempt = 0
        var lastError: IOException? = null

        while (attempt <= MAX_RETRIES) {
            if (attempt > 0) {
                val backoff = min(BASE_BACKOFF_MS * (1L shl (attempt - 1)), MAX_BACKOFF_MS)
                sleep(backoff)
            }
            try {
                val response = chain.proceed(chain.request())
                val code = response.code
                val retryable = code == 429 || code == 500 || code == 502 || code == 503 || code == 504
                if (!retryable || attempt == MAX_RETRIES) {
                    if (code == 429) {
                        val retryAfter = response.header("Retry-After")?.toLongOrNull() ?: 30L
                        retryAfterUntil = System.currentTimeMillis() + retryAfter * 1000
                    }
                    return response
                }
                // Honour Retry-After when the server provides it.
                val retryAfterSeconds = response.header("Retry-After")?.toLongOrNull()
                response.close()
                if (retryAfterSeconds != null) {
                    sleep(min(retryAfterSeconds * 1000, MAX_BACKOFF_MS))
                }
            } catch (e: IOException) {
                lastError = e
                if (attempt == MAX_RETRIES) throw e
            }
            attempt++
        }
        throw lastError ?: IOException("Request failed after $MAX_RETRIES retries")
    }

    private fun throttle() {
        val now = System.currentTimeMillis()
        val previous = lastRequestAt.get()
        val wait = MIN_INTERVAL_MS - (now - previous)
        if (previous > 0 && wait > 0) sleep(wait)
        lastRequestAt.set(System.currentTimeMillis())
    }

    private fun sleep(millis: Long) {
        try {
            Thread.sleep(millis)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Request interrupted", e)
        }
    }
}
