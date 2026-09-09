package com.nova.browser.core.utils

object Constants {
    const val APP_NAME = "NOVA AI Browser"
    const val DATABASE_NAME = "nova_database"
    const val SECURE_PREFS = "nova_secure_prefs"
    const val SETTINGS_STORE = "nova_settings"

    const val HOME_URL = "nova://home"
    const val DEFAULT_USER_AGENT_SUFFIX = "NovaBrowser/1.0"

    // Gemini
    const val GEMINI_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/"
    // OpenRouter
    const val OPENROUTER_BASE_URL = "https://openrouter.ai/api/v1/"
    const val OPENROUTER_REFERER = "https://nova-browser.app"
    const val OPENROUTER_TITLE = "NOVA AI Browser"
    // Breach check
    const val HIBP_RANGE_URL = "https://api.pwnedpasswords.com/range/"

    const val NETWORK_TIMEOUT_SECONDS = 60L
    const val AI_STREAM_TIMEOUT_SECONDS = 180L

    const val MAX_AGENT_STEPS = 50
    const val AGENT_ACTION_TIMEOUT_MS = 10_000L
    const val MAX_ACTIVE_WEBVIEWS = 3
    const val CLIPBOARD_CLEAR_DELAY_MS = 30_000L

    const val NOTIF_CHANNEL_DOWNLOADS = "nova_downloads"
    const val NOTIF_CHANNEL_AI = "nova_ai"
    const val NOTIF_CHANNEL_SECURITY = "nova_security"

    const val MEMORY_TOKEN_BUDGET = 800
    const val DEFAULT_CONTEXT_TOKENS = 24_000
}

object SearchEngines {
    data class Engine(val name: String, val queryUrl: String, val suggestUrl: String?, val homeUrl: String)

    val all = listOf(
        Engine("Google", "https://www.google.com/search?q=", "https://suggestqueries.google.com/complete/search?client=firefox&q=", "https://www.google.com"),
        Engine("DuckDuckGo", "https://duckduckgo.com/?q=", "https://duckduckgo.com/ac/?type=list&q=", "https://duckduckgo.com"),
        Engine("Bing", "https://www.bing.com/search?q=", null, "https://www.bing.com"),
        Engine("Brave", "https://search.brave.com/search?q=", null, "https://search.brave.com"),
        Engine("Startpage", "https://www.startpage.com/sp/search?query=", null, "https://www.startpage.com"),
        Engine("Ecosia", "https://www.ecosia.org/search?q=", null, "https://www.ecosia.org")
    )

    fun byName(name: String): Engine = all.firstOrNull { it.name.equals(name, true) } ?: all[0]
}
