package com.nova.browser.core.di

import com.google.gson.Gson
import com.nova.browser.core.network.GeminiApiService
import com.nova.browser.core.network.OpenRouterApiService
import com.nova.browser.core.network.interceptors.AuthInterceptor
import com.nova.browser.core.network.interceptors.RateLimitInterceptor
import com.nova.browser.core.utils.Constants
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply {
            // Bodies can contain page content and keys — never log them in release.
            level = if (com.nova.browser.BuildConfig.DEBUG) {
                HttpLoggingInterceptor.Level.BASIC
            } else {
                HttpLoggingInterceptor.Level.NONE
            }
        }

    @Provides
    @Singleton
    @Named("standard")
    fun provideOkHttpClient(
        logging: HttpLoggingInterceptor,
        auth: AuthInterceptor,
        rateLimit: RateLimitInterceptor
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(Constants.NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(Constants.NETWORK_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(auth)
        .addInterceptor(rateLimit)
        .addInterceptor(logging)
        .build()

    /** Long read timeout and no rate-limit retry: streaming must not be replayed. */
    @Provides
    @Singleton
    @Named("streaming")
    fun provideStreamingClient(logging: HttpLoggingInterceptor): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(Constants.AI_STREAM_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .retryOnConnectionFailure(false)
            .addInterceptor(logging)
            .build()

    /** Plain client for downloads (no logging, no throttling). */
    @Provides
    @Singleton
    @Named("download")
    fun provideDownloadClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()

    @Provides
    @Singleton
    fun provideGeminiApi(@Named("standard") client: OkHttpClient, gson: Gson): GeminiApiService =
        Retrofit.Builder()
            .baseUrl(Constants.GEMINI_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(GeminiApiService::class.java)

    @Provides
    @Singleton
    fun provideOpenRouterApi(@Named("standard") client: OkHttpClient, gson: Gson): OpenRouterApiService =
        Retrofit.Builder()
            .baseUrl(Constants.OPENROUTER_BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(OpenRouterApiService::class.java)
}
