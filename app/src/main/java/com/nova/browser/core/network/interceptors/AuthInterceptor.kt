package com.nova.browser.core.network.interceptors

import com.nova.browser.core.utils.Constants
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/** Adds the OpenRouter attribution headers required by their API. */
@Singleton
class AuthInterceptor @Inject constructor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("HTTP-Referer", Constants.OPENROUTER_REFERER)
            .header("X-Title", Constants.OPENROUTER_TITLE)
            .header("Accept", "application/json")
            .build()
        return chain.proceed(request)
    }
}
