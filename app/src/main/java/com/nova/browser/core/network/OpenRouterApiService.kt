package com.nova.browser.core.network

import com.nova.browser.core.network.models.OpenRouterModelsResponse
import com.nova.browser.core.network.models.OpenRouterRequest
import com.nova.browser.core.network.models.OpenRouterResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface OpenRouterApiService {

    @POST("chat/completions")
    suspend fun chatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: OpenRouterRequest
    ): Response<OpenRouterResponse>

    @GET("models")
    suspend fun listModels(): Response<OpenRouterModelsResponse>
}
