package com.nova.browser.core.di

import com.nova.browser.features.ai.engine.AIEngine
import com.nova.browser.features.ai.engine.GeminiEngine
import com.nova.browser.features.ai.engine.OpenRouterEngine
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AIModule {

    @Provides
    @Singleton
    @Named("geminiEngine")
    fun provideGeminiEngine(engine: GeminiEngine): AIEngine = engine

    @Provides
    @Singleton
    @Named("openRouterEngine")
    fun provideOpenRouterEngine(engine: OpenRouterEngine): AIEngine = engine

    @Provides
    @Singleton
    fun provideEngines(gemini: GeminiEngine, openRouter: OpenRouterEngine): List<AIEngine> =
        listOf(gemini, openRouter)
}
