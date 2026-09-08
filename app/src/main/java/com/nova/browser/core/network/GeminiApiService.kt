package com.nova.browser.core.network

import com.nova.browser.core.network.models.GeminiModelListResponse
import com.nova.browser.core.network.models.GeminiRequest
import com.nova.browser.core.network.models.GeminiResponse
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface GeminiApiService {

    @POST("models/{model}:generateContent")
    suspend fun generateContent(
        @Path("model") model: String,
        @Query("key") apiKey: String,
        @Body request: GeminiRequest
    ): Response<GeminiResponse>

    @GET("models")
    suspend fun listModels(
        @Query("key") apiKey: String
    ): Response<GeminiModelListResponse>
}
