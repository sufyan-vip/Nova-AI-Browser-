

# NOVA AI Browser — Complete Master Specification

## 📋 Full Implementation Prompt & Specification Document

---

## STRICTLY FOLLOW THESE RULES — NO EXCEPTIONS

```
CRITICAL REQUIREMENTS:
1. You MUST write EVERY SINGLE LINE of code. No placeholders. No "TODO". No "implement later". No "add here". No shortcuts.
2. Every file MUST be COMPLETE and FUNCTIONAL. If a file has 500 lines, write all 500 lines.
3. You will NOT skip any feature listed in this document.
4. If the code for a feature exceeds your output limit, STOP at a logical break point and say "CONTINUE FROM: [exact file and line]" — then continue in the next message.
5. Every UI component MUST have full styling, full interactivity, full animations.
6. Every function MUST have full implementation, full error handling, full edge cases.
7. The app MUST compile and run without ANY errors on first build.
8. Use Kotlin + Jetpack Compose for ALL UI. Native Android. No Flutter. No React Native. No WebView-only shortcuts.
9. The browser engine will use Android WebView (CustomTabsClient where beneficial) with full custom controls.
10. Before finishing, CREATE a GitHub Actions workflow file (.github/workflows/build.yml) and include it.
11. Push ALL files. No file should be left unwritten.
12. Test every feature mentally before writing — if it would crash, fix it in the code BEFORE writing.
13. Minimum app size target: optimize imports, no unused dependencies.
14. Every screen must work on phones 5" to 7.5" screens, portrait and landscape.
15. DO NOT use any deprecated APIs. Target SDK 34, minimum SDK 26.
16. The AI features must gracefully handle: no API key, no internet, rate limits, invalid responses, timeout.
17. All sensitive data (passwords, API keys) MUST use Android Keystore / EncryptedSharedPreferences.
18. The app MUST handle configuration changes (rotation) without data loss.
19. Every list MUST handle empty states with proper UI.
20. Every network call MUST show loading state, handle error state, handle success state.
```

---

## 01_PROJECT_VISION

```
Project Name: NOVA AI Browser
Platform: Android (Native Kotlin)
Architecture: MVVM + Clean Architecture + Multi-Module
Minimum SDK: 26 (Android 8.0)
Target SDK: 34 (Android 14)
Language: Kotlin 100%
UI Framework: Jetpack Compose (Material 3 + Custom Design System)
Browser Engine: Android WebView with custom enhancement layer

Vision: An AI-native mobile browser where AI is not a chatbot bolted on — 
AI IS the browser's brain. It understands pages, navigates, automates, 
researches, codes, writes, and manages — while the user stays in control.

The browser should feel like a futuristic operating system, not just 
another Chrome clone with a chat button.
```

---

## 02_TECH_STACK

```yaml
Language: Kotlin 1.9+
UI: Jetpack Compose + Material 3 + Custom Design System
Architecture: MVVM + Clean Architecture
DI: Hilt (Dagger)
Navigation: Compose Navigation
Networking: Retrofit2 + OkHttp3 + Kotlin Coroutines
JSON: Kotlinx.serialization + Gson (for legacy)
Database: Room Database
Key-Value: DataStore (Preferences + Proto)
Security: Android Keystore + EncryptedSharedPreferences
Image Loading: Coil (Compose)
WebView: Android WebView + WebChromeClient + WebViewClient
PDF: Android PdfRenderer + Custom PDF Viewer
Downloads: Custom Download Manager (OkHttp-based multi-thread)
Media: ExoPlayer (for media playback)
AI: Retrofit-based API clients for Gemini + OpenRouter
Background: WorkManager
Animations: Compose Animations + Lottie
Icons: Material Icons Extended + Custom SVG
Build: Gradle Kotlin DSL
CI/CD: GitHub Actions
Testing: JUnit5 + Mockk + Compose Testing
Code Quality: KtLint + Detekt
ProGuard: R8 with custom rules
```

---

## 03_ARCHITECTURE

```
nova-ai-browser/
├── .github/
│   └── workflows/
│       └── build.yml
├── app/
│   └── src/
│       └── main/
│           ├── java/com/nova/browser/
│           │   ├── NovaApplication.kt
│           │   ├── MainActivity.kt
│           │   ├── core/
│           │   │   ├── di/
│           │   │   │   ├── AppModule.kt
│           │   │   │   ├── DatabaseModule.kt
│           │   │   │   ├── NetworkModule.kt
│           │   │   │   └── AIModule.kt
│           │   │   ├── database/
│           │   │   │   ├── NovaDatabase.kt
│           │   │   │   ├── entities/
│           │   │   │   │   ├── BookmarkEntity.kt
│           │   │   │   │   ├── HistoryEntity.kt
│           │   │   │   │   ├── DownloadEntity.kt
│           │   │   │   │   ├── TabEntity.kt
│           │   │   │   │   ├── PasswordEntity.kt
│           │   │   │   │   ├── AIMemoryEntity.kt
│           │   │   │   │   ├── WorkspaceEntity.kt
│           │   │   │   │   ├── AutomationEntity.kt
│           │   │   │   │   └── NoteEntity.kt
│           │   │   │   └── dao/
│           │   │   │       ├── BookmarkDao.kt
│           │   │   │       ├── HistoryDao.kt
│           │   │   │       ├── DownloadDao.kt
│           │   │   │       ├── TabDao.kt
│           │   │   │       ├── PasswordDao.kt
│           │   │   │       ├── AIMemoryDao.kt
│           │   │   │       ├── WorkspaceDao.kt
│           │   │   │       ├── AutomationDao.kt
│           │   │   │       └── NoteDao.kt
│           │   │   ├── network/
│           │   │   │   ├── GeminiApiService.kt
│           │   │   │   ├── OpenRouterApiService.kt
│           │   │   │   ├── interceptors/
│           │   │   │   │   ├── AuthInterceptor.kt
│           │   │   │   │   └── RateLimitInterceptor.kt
│           │   │   │   └── models/
│           │   │   │       ├── GeminiRequest.kt
│           │   │   │       ├── GeminiResponse.kt
│           │   │   │       ├── OpenRouterRequest.kt
│           │   │   │       └── OpenRouterResponse.kt
│           │   │   ├── security/
│           │   │   │   ├── SecureStorage.kt
│           │   │   │   ├── PasswordEncryption.kt
│           │   │   │   └── BiometricAuth.kt
│           │   │   ├── utils/
│           │   │   │   ├── Extensions.kt
│           │   │   │   ├── Constants.kt
│           │   │   │   ├── NetworkUtils.kt
│           │   │   │   ├── DateUtils.kt
│           │   │   │   ├── FileUtils.kt
│           │   │   │   ├── UrlUtils.kt
│           │   │   │   └── HtmlUtils.kt
│           │   │   └── theme/
│           │   │       ├── Theme.kt
│           │   │       ├── Color.kt
│           │   │       ├── Typography.kt
│           │   │       ├── Shape.kt
│           │   │       ├── Dimens.kt
│           │   │       ├── Animation.kt
│           │   │       └── GlassComponents.kt
│           │   ├── features/
│           │   │   ├── browser/
│           │   │   │   ├── ui/
│           │   │   │   │   ├── BrowserScreen.kt
│           │   │   │   │   ├── AddressBar.kt
│           │   │   │   │   ├── WebViewContainer.kt
│           │   │   │   │   ├── BrowserMenuSheet.kt
│           │   │   │   │   ├── FindInPage.kt
│           │   │   │   │   └── PageLoadingIndicator.kt
│           │   │   │   ├── viewmodel/
│           │   │   │   │   └── BrowserViewModel.kt
│           │   │   │   └── repository/
│           │   │   │       └── BrowserRepository.kt
│           │   │   ├── tabs/
│           │   │   │   ├── ui/
│           │   │   │   │   ├── TabManagerScreen.kt
│           │   │   │   │   ├── TabCard.kt
│           │   │   │   │   ├── TabGroupHeader.kt
│           │   │   │   │   └── VerticalTabList.kt
│           │   │   │   ├── viewmodel/
│           │   │   │   │   └── TabViewModel.kt
│           │   │   │   └── repository/
│           │   │   │       └── TabRepository.kt
│           │   │   ├── ai/
│           │   │   │   ├── ui/
│           │   │   │   │   ├── AISidebar.kt
│           │   │   │   │   ├── AIChatMessage.kt
│           │   │   │   │   ├── AIModelSelector.kt
│           │   │   │   │   ├── AIModeSelector.kt
│           │   │   │   │   ├── AICommandPalette.kt
│           │   │   │   │   ├── AIFloatingButton.kt
│           │   │   │   │   ├── AIResearchPanel.kt
│           │   │   │   │   ├── AIWritingPanel.kt
│           │   │   │   │   ├── AICodePanel.kt
│           │   │   │   │   ├── AIVisionPanel.kt
│           │   │   │   │   └── AITranslatePanel.kt
│           │   │   │   ├── viewmodel/
│           │   │   │   │   └── AIViewModel.kt
│           │   │   │   ├── repository/
│           │   │   │   │   └── AIRepository.kt
│           │   │   │   └── engine/
│           │   │   │       ├── AIEngine.kt
│           │   │   │       ├── GeminiEngine.kt
│           │   │   │       ├── OpenRouterEngine.kt
│           │   │   │       ├── ContextEngine.kt
│           │   │   │       ├── ModelSelector.kt
│           │   │   │       └── TokenTracker.kt
│           │   │   ├── agent/
│           │   │   │   ├── ui/
│           │   │   │   │   ├── AgentScreen.kt
│           │   │   │   │   ├── AgentStepIndicator.kt
│           │   │   │   │   ├── AgentConfirmationDialog.kt
│           │   │   │   │   └── AgentResultCard.kt
│           │   │   │   ├── viewmodel/
│           │   │   │   │   └── AgentViewModel.kt
│           │   │   │   ├── repository/
│           │   │   │   │   └── AgentRepository.kt
│           │   │   │   └── engine/
│           │   │   │       ├── BrowserAgent.kt
│           │   │   │       ├── PageInteractor.kt
│           │   │   │       ├── ActionExecutor.kt
│           │   │   │       ├── WorkflowEngine.kt
│           │   │   │       └── SafetyChecker.kt
│           │   │   ├── search/
│           │   │   │   ├── ui/
│           │   │   │   │   ├── SearchScreen.kt
│           │   │   │   │   ├── SearchSuggestions.kt
│           │   │   │   │   ├── AISearchResults.kt
│           │   │   │   │   └── SourceCard.kt
│           │   │   │   ├── viewmodel/
│           │   │   │   │   └── SearchViewModel.kt
│           │   │   │   └── repository/
│           │   │   │       └── SearchRepository.kt
│           │   │   ├── privacy/
│           │   │   │   ├── ui/
│           │   │   │   │   ├── PrivacyDashboard.kt
│           │   │   │   │   ├── TrackerBlocker.kt
│           │   │   │   │   ├── PermissionManager.kt
│           │   │   │   │   ├── PrivacyInspector.kt
│           │   │   │   │   └── CookieManager.kt
│           │   │   │   ├── viewmodel/
│           │   │   │   │   └── PrivacyViewModel.kt
│           │   │   │   ├── repository/
│           │   │   │   │   └── PrivacyRepository.kt
│           │   │   │   └── engine/
│           │   │   │       ├── TrackerDatabase.kt
│           │   │   │       ├── AdBlockEngine.kt
│           │   │   │       └── FingerprintProtection.kt
│           │   │   ├── downloads/
│           │   │   │   ├── ui/
│           │   │   │   │   ├── DownloadManagerScreen.kt
│           │   │   │   │   ├── DownloadItem.kt
│           │   │   │   │   └── DownloadSettings.kt
│           │   │   │   ├── viewmodel/
│           │   │   │   │   └── DownloadViewModel.kt
│           │   │   │   ├── repository/
│           │   │   │   │   └── DownloadRepository.kt
│           │   │   │   └── engine/
│           │   │   │       ├── DownloadEngine.kt
│           │   │   │       ├── MultiThreadDownloader.kt
│           │   │   │       └── DownloadScheduler.kt
│           │   │   ├── bookmarks/
│           │   │   │   ├── ui/
│           │   │   │   │   ├── BookmarkScreen.kt
│           │   │   │   │   ├── BookmarkItem.kt
│           │   │   │   │   └── BookmarkFolderDialog.kt
│           │   │   │   ├── viewmodel/
│           │   │   │   │   └── BookmarkViewModel.kt
│           │   │   │   └── repository/
│           │   │   │       └── BookmarkRepository.kt
│           │   │   ├── history/
│           │   │   │   ├── ui/
│           │   │   │   │   ├── HistoryScreen.kt
│           │   │   │   │   └── HistoryItem.kt
│           │   │   │   ├── viewmodel/
│           │   │   │   │   └── HistoryViewModel.kt
│           │   │   │   └── repository/
│           │   │   │       └── HistoryRepository.kt
│           │   │   ├── passwords/
│           │   │   │   ├── ui/
│           │   │   │   │   ├── PasswordManagerScreen.kt
│           │   │   │   │   ├── PasswordItem.kt
│           │   │   │   │   ├── PasswordGenerator.kt
│           │   │   │   │   └── SecurityDashboard.kt
│           │   │   │   ├── viewmodel/
│           │   │   │   │   └── PasswordViewModel.kt
│           │   │   │   └── repository/
│           │   │   │       └── PasswordRepository.kt
│           │   │   ├── pdf/
│           │   │   │   ├── ui/
│           │   │   │   │   ├── PdfViewerScreen.kt
│           │   │   │   │   ├── PdfPageView.kt
│           │   │   │   │   ├── PdfThumbnailStrip.kt
│           │   │   │   │   └── PdfAIPanel.kt
│           │   │   │   ├── viewmodel/
│           │   │   │   │   └── PdfViewModel.kt
│           │   │   │   └── repository/
│           │   │   │       └── PdfRepository.kt
│           │   │   ├── devtools/
│           │   │   │   ├── ui/
│           │   │   │   │   ├── DevToolsScreen.kt
│           │   │   │   │   ├── ConsolePanel.kt
│           │   │   │   │   ├── NetworkPanel.kt
│           │   │   │   │   ├── DOMInspector.kt
│           │   │   │   │   ├── StorageInspector.kt
│           │   │   │   │   └── AIDebugger.kt
│           │   │   │   ├── viewmodel/
│           │   │   │   │   └── DevToolsViewModel.kt
│           │   │   │   └── repository/
│           │   │   │       └── DevToolsRepository.kt
│           │   │   ├── codeworkspace/
│           │   │   │   ├── ui/
│           │   │   │   │   ├── CodeWorkspaceScreen.kt
│           │   │   │   │   ├── CodeEditor.kt
│           │   │   │   │   ├── CodePreview.kt
│           │   │   │   │   └── AICodeAssistant.kt
│           │   │   │   ├── viewmodel/
│           │   │   │   │   └── CodeViewModel.kt
│           │   │   │   └── repository/
│           │   │   │       └── CodeRepository.kt
│           │   │   ├── automation/
│           │   │   │   ├── ui/
│           │   │   │   │   ├── AutomationStudioScreen.kt
│           │   │   │   │   ├── WorkflowBuilder.kt
│           │   │   │   │   ├── WorkflowNode.kt
│           │   │   │   │   └── AutomationHistory.kt
│           │   │   │   ├── viewmodel/
│           │   │   │   │   └── AutomationViewModel.kt
│           │   │   │   ├── repository/
│           │   │   │   │   └── AutomationRepository.kt
│           │   │   │   └── engine/
│           │   │   │       ├── AutomationRunner.kt
│           │   │   │       └── TriggerManager.kt
│           │   │   ├── media/
│           │   │   │   ├── ui/
│           │   │   │   │   ├── MediaPlayerScreen.kt
│           │   │   │   │   ├── PictureInPicture.kt
│           │   │   │   │   └── MediaControls.kt
│           │   │   │   ├── viewmodel/
│           │   │   │   │   └── MediaViewModel.kt
│           │   │   │   └── repository/
│           │   │   │       └── MediaRepository.kt
│           │   │   ├── settings/
│           │   │   │   ├── ui/
│           │   │   │   │   ├── SettingsScreen.kt
│           │   │   │   │   ├── GeneralSettings.kt
│           │   │   │   │   ├── PrivacySettings.kt
│           │   │   │   │   ├── AISettings.kt
│           │   │   │   │   ├── AppearanceSettings.kt
│           │   │   │   │   ├── DownloadSettings.kt
│           │   │   │   │   └── AboutScreen.kt
│           │   │   │   ├── viewmodel/
│           │   │   │   │   └── SettingsViewModel.kt
│           │   │   │   └── repository/
│           │   │   │       └── SettingsRepository.kt
│           │   │   ├── home/
│           │   │   │   ├── ui/
│           │   │   │   │   ├── HomeScreen.kt
│           │   │   │   │   ├── QuickActions.kt
│           │   │   │   │   ├── RecentSites.kt
│           │   │   │   │   └── AIGreeting.kt
│           │   │   │   └── viewmodel/
│           │   │   │       └── HomeViewModel.kt
│           │   │   └── memory/
│           │   │       ├── ui/
│           │   │       │   ├── AIMemoryScreen.kt
│           │   │       │   └── MemoryItem.kt
│           │   │       ├── viewmodel/
│           │   │       │   └── MemoryViewModel.kt
│           │   │       └── repository/
│           │   │           └── MemoryRepository.kt
│           │   └── navigation/
│           │       └── NavGraph.kt
│           ├── res/
│           │   ├── values/
│           │   │   ├── strings.xml
│           │   │   ├── colors.xml
│           │   │   └── themes.xml
│           │   ├── drawable/
│           │   ├── raw/
│           │   └── xml/
│           │       └── network_security_config.xml
│           └── AndroidManifest.xml
├── build.gradle.kts (project)
├── app/build.gradle.kts (app)
├── settings.gradle.kts
├── gradle.properties
├── proguard-rules.pro
└── README.md
```

---

## 04_UI_SYSTEM — DESIGN SPECIFICATION

```
DESIGN LANGUAGE: "Nova Glass"

Concept: Glassmorphism + Material 3 Dynamic Color + Futuristic/Sci-fi touches

PRIMARY PALETTE:
- Background: Deep navy/dark (#0A0E1A)
- Surface: Semi-transparent dark (#141929 with 85% opacity)
- Glass Surface: White 8% opacity with blur
- Primary: Electric blue (#4A9EFF)
- Secondary: Cyan (#00E5FF)
- Tertiary: Purple (#B388FF)
- Accent: Neon green (#69F0AE) for AI elements
- Error: Coral (#FF5252)
- Warning: Amber (#FFD740)
- Success: Green (#69F0AE)
- Text Primary: White 95%
- Text Secondary: White 70%
- Text Tertiary: White 40%

GLASS EFFECT SPECIFICATION:
- Background blur: 20dp
- Surface opacity: 8-15% white
- Border: 1dp white at 10% opacity
- Shadow: 0dp 4dp 20dp black at 30%
- Inner glow: subtle white gradient at top edge

TYPOGRAPHY:
- Font Family: Google Sans (or Inter as fallback)
- Display: 32sp Bold
- Headline: 24sp SemiBold
- Title: 20sp SemiBold
- Body: 16sp Regular
- Label: 14sp Medium
- Caption: 12sp Regular
- Mono: JetBrains Mono (for code)

SPACING SYSTEM:
- xs: 4dp
- sm: 8dp
- md: 12dp
- lg: 16dp
- xl: 24dp
- xxl: 32dp
- xxxl: 48dp

CORNER RADIUS:
- Small: 8dp
- Medium: 12dp
- Large: 16dp
- XLarge: 24dp
- Full: 50% (circles)

ANIMATIONS:
- Page transitions: 300ms ease-out slide
- Bottom sheet: 250ms spring
- Cards: 200ms scale on press
- AI responses: typewriter/streaming effect
- Tab switching: 200ms crossfade
- Loading: pulse glow animation
- Floating button: breathing glow
- Sidebar: 300ms slide from right
- Glass shimmer: subtle moving highlight

HAPTICS:
- Button press: light click
- Long press: heavy click
- Swipe action: light tick
- AI response complete: success pattern
- Error: error pattern

COMPONENTS TO BUILD:
- GlassCard
- GlassButton
- GlassTextField
- GlassBottomSheet
- GlassDialog
- GlassChip
- GlassTopBar
- GlassNavigationBar
- GlassSwitch
- GlassSlider
- GlassDropdown
- GlassTabRow
- GlassSearchBar
- GlassFAB
- GlowingBorder
- AnimatedGradient
- ShimmerEffect
- PulseAnimation
- TypewriterText
- StreamingText
- NovaLoadingIndicator
- NovaProgressBar
- NovaSnackbar
- NovaTooltip
```

---

## 05_BROWSER_ENGINE

```
ENGINE: Android WebView (with extensive customization)

WEBVIEW CONFIGURATION:
- JavaScript enabled
- DOM storage enabled
- Database enabled
- Cache mode: LOAD_DEFAULT
- Mixed content: LOAD_NORMAL
- Allow file access: controlled
- Geolocation: permission-gated
- Media playback: user gesture not required for background
- Text zoom: user configurable
- User agent: custom NOVA string
- Safe browsing: enabled
- Multiple windows: supported

CUSTOM WEBVIEW CLIENT:
- URL loading interception
- SSL error handling (with user warning)
- Page started/finished callbacks
- Error page (custom Nova error page)
- HTTP auth handling
- Override URL loading for custom schemes

CUSTOM CHROME CLIENT:
- Progress tracking
- Title/icon extraction
- File chooser
- Geolocation permission
- Media permission (camera/mic)
- Full screen video
- Console messages capture (for DevTools)
- JavaScript dialogs (alert/confirm/prompt)
- Custom tabs color

ADDITIONAL BROWSER FEATURES:
- Pull-to-refresh
- Long press context menu (link/image options)
- Text selection with AI options
- Find in page
- Reader mode (via Readability.js injection)
- Desktop mode toggle
- Page zoom
- Print page
- Save page (MHTML)
- View source
- Page info (SSL, permissions)
- Share page
- QR code for page URL
- Screenshot page (full page scroll capture)

JAVASCRIPT INJECTION LAYER:
- Content extraction (for AI context)
- DOM analysis (for DevTools)
- Form detection (for password manager)
- Tracker detection script
- Readability extraction
- Table extraction
- Link extraction
- Image extraction
- Video detection
- Accessibility analysis
- Page structure analysis
- Performance metrics collection
- Click/scroll automation (for AI agent)
- Text selection extraction
```

---

## 06_AI_ENGINE

```
DUAL ENGINE ARCHITECTURE:

┌─────────────────────────┐
│     AI Engine Manager    │
│                         │
│  ┌─────────┐ ┌────────┐│
│  │ Gemini  │ │OpenRouter││
│  │ Engine  │ │ Engine  ││
│  └────┬────┘ └───┬─────┘│
│       └─────┬────┘      │
│             │           │
│     Context Engine      │
│             │           │
│     Token Tracker       │
│             │           │
│     Response Cache      │
│             │           │
│     Stream Handler      │
└─────────────────────────┘

FEATURES:
1. User selects preferred provider (Gemini/OpenRouter)
2. Per-task model selection
3. Automatic fallback (if primary fails, try secondary)
4. Model capability detection (vision, code, chat, etc.)
5. API key management (encrypted storage)
6. Token/usage tracking with budget alerts
7. Context window management (auto-truncate)
8. Streaming responses (SSE parsing)
9. Response caching (same question = cached answer)
10. Custom system prompts per mode
11. Conversation history management
12. Multi-turn conversations
13. Rate limiting handler
14. Error classification (auth, rate limit, server, network)
15. Retry logic with exponential backoff

CONTEXT ENGINE:
- Extract page content via JavaScript injection
- Identify main content vs. navigation/ads
- Compress content to fit context window
- Maintain conversation context
- Add page metadata (URL, title, type)
- Detect content type (article, product, code, etc.)
- Extract structured data where possible

SUPPORTED TASKS:
- Chat (general conversation)
- Page Q&A (ask about current page)
- Summarize (page/PDF/selected text)
- Translate (any language pair)
- Explain (simplify complex content)
- Research (multi-source synthesis)
- Write (compose/rewrite/grammar)
- Code (generate/explain/debug/refactor)
- Vision (analyze images/screenshots)
- Agent (browser automation commands)
- Search (AI-enhanced search)
```

---

## 07_GEMINI_API

```
BASE URL: https://generativelanguage.googleapis.com/v1beta/

ENDPOINTS:
- POST /models/{model}:generateContent
- POST /models/{model}:streamGenerateContent

SUPPORTED MODELS:
- gemini-2.0-flash (fast, default)
- gemini-1.5-pro (powerful)
- gemini-1.5-flash (balance)
- gemini-pro-vision (multimodal)

REQUEST FORMAT:
{
  "contents": [
    {
      "role": "user",
      "parts": [
        {"text": "..."},
        {"inlineData": {"mimeType": "image/jpeg", "data": "base64..."}}
      ]
    }
  ],
  "systemInstruction": {
    "parts": [{"text": "system prompt"}]
  },
  "generationConfig": {
    "temperature": 0.7,
    "topP": 0.95,
    "topK": 40,
    "maxOutputTokens": 8192,
    "candidateCount": 1
  },
  "safetySettings": [...]
}

RESPONSE PARSING:
- Extract candidates[0].content.parts[0].text
- Handle SAFETY blocked responses
- Handle RECITATION blocked responses
- Parse streaming chunks (SSE format)
- Extract usage metadata (token counts)

ERROR HANDLING:
- 400: Bad request (invalid model/params)
- 401/403: Invalid API key
- 429: Rate limit (implement backoff)
- 500/503: Server error (retry)
- Network timeout: Show offline message
```

---

## 08_OPENROUTER_API

```
BASE URL: https://openrouter.ai/api/v1/

ENDPOINTS:
- POST /chat/completions
- GET /models

REQUEST FORMAT (OpenAI compatible):
{
  "model": "anthropic/claude-3-haiku",
  "messages": [
    {"role": "system", "content": "..."},
    {"role": "user", "content": "..."}
  ],
  "temperature": 0.7,
  "max_tokens": 4096,
  "stream": true
}

HEADERS:
- Authorization: Bearer {api_key}
- HTTP-Referer: https://nova-browser.app
- X-Title: NOVA AI Browser

POPULAR MODELS TO LIST:
- anthropic/claude-3-haiku (fast)
- anthropic/claude-3-sonnet (balanced)
- anthropic/claude-3-opus (powerful)
- google/gemini-pro (Google via OR)
- meta-llama/llama-3-70b-instruct (open)
- mistralai/mixtral-8x7b-instruct (fast open)
- openai/gpt-4o-mini (fast)
- openai/gpt-4o (powerful)
- deepseek/deepseek-chat (code)

STREAMING:
- SSE format: data: {"choices":[{"delta":{"content":"..."}}]}
- data: [DONE] = end of stream

RESPONSE PARSING:
- choices[0].message.content (non-streaming)
- choices[0].delta.content (streaming)
- usage.prompt_tokens, usage.completion_tokens, usage.total_tokens

ERROR HANDLING:
- Same HTTP error codes as standard APIs
- Model-specific errors
- Credit/balance errors
- Rate limiting per model
```

---

## 09_AI_AGENT

```
AGENT ARCHITECTURE:

User Command (natural language)
        ↓
  Command Parser (AI)
        ↓
  Action Plan Generation
        ↓
  Safety Check
        ↓
  [Confirmation if destructive]
        ↓
  Action Execution Loop:
    ├── Navigate to URL
    ├── Click element (CSS selector)
    ├── Type text into field
    ├── Scroll page
    ├── Wait for element
    ├── Extract content
    ├── Screenshot
    ├── Switch tab
    ├── Open new tab
    ├── Close tab
    ├── Go back/forward
    ├── Download file
    ├── Read page content
    └── AI analyze content
        ↓
  Result Compilation
        ↓
  Present to User

IMPLEMENTATION:
- JavaScript injection for DOM manipulation
- evaluateJavascript() for actions
- WebView.addJavascriptInterface() for callbacks
- Step-by-step UI showing agent progress
- Cancel button at any step
- Timeout per action (10 seconds default)
- Max steps per workflow (50 default)
- Error recovery (if step fails, AI decides next action)

SAFETY RULES:
- NEVER auto-submit payments/purchases
- NEVER auto-send messages/emails without confirmation
- NEVER auto-delete anything without confirmation
- NEVER auto-login to financial services
- ALWAYS show what agent is about to do
- User can set custom safety rules

AGENT CAPABILITIES:
1. Open URL
2. Search (via address bar or search engine)
3. Click button/link (by text, CSS selector, or description)
4. Fill form field (by label or placeholder)
5. Select dropdown option
6. Check/uncheck checkbox
7. Scroll to element or by amount
8. Wait for page load
9. Wait for element to appear
10. Extract text from element
11. Extract all links
12. Extract all images
13. Extract table data
14. Take screenshot
15. Compare two pages
16. Read current page
17. Summarize current page
18. Navigate back/forward
19. Open new tab
20. Switch between tabs
21. Close tab
22. Download file from URL
23. Save text to notes
24. Chain multiple actions (workflow)

AGENT UI:
- Step indicator (Step 1 of N)
- Current action display
- Live page preview
- Cancel/Pause buttons
- Result summary card
- "Retry" and "Modify" options
```

---

## 10_CONTEXT_ENGINE

```
PURPOSE: Efficiently extract and compress page content for AI

PIPELINE:
1. JavaScript injected into page
2. Extract: title, URL, meta description, main content, headings, links, images
3. Readability.js-style extraction for article pages
4. Content type detection (article/product/code/forum/search/video/social)
5. Relevant content prioritization based on user query
6. Token counting
7. Context window fitting (truncate intelligently if too long)
8. Add metadata wrapper

CONTENT EXTRACTION JAVASCRIPT:
- document.title
- document.querySelector('meta[name="description"]')?.content
- Main content heuristics (article, main, #content, .post, etc.)
- Heading structure (h1-h6)
- List items
- Table data
- Code blocks
- Image alt texts
- Link texts and URLs
- Form fields (for agent)
- Button texts (for agent)

COMPRESSION STRATEGIES:
- Remove navigation/footer/sidebar content
- Remove repeated elements
- Summarize long sections
- Keep headings + first paragraph
- Preserve code blocks fully
- Preserve tables fully
- Remove HTML tags (text only)
- Limit images to alt text descriptions

TOKEN MANAGEMENT:
- Count tokens (approximate: words * 1.3)
- Gemini context windows: 32K (flash), 1M (pro)
- OpenRouter: varies by model (4K to 200K)
- Always leave 25% of context window for response
- If content > window: progressive summarization
```

---

## 11_RESEARCH_MODE

```
MULTI-SOURCE RESEARCH:

User Query
    ↓
AI generates search queries (3-5 variations)
    ↓
Search via multiple sources
    ↓
Open top results (3-5 pages)
    ↓
Extract content from each
    ↓
AI synthesizes findings
    ↓
Structured report with:
  - Summary
  - Key findings
  - Source comparison
  - Conflicting information
  - Citations with links
  - Follow-up questions
  - Confidence assessment

FEATURES:
- Save research as note
- Export as markdown
- Continue researching (follow-up)
- Add more sources manually
- Compare specific pages
- Fact extraction mode
- Timeline extraction
- Quote extraction
- Statistic extraction
```

---

## 12_AUTOMATION_STUDIO

```
VISUAL WORKFLOW BUILDER:

NODE TYPES:
1. Trigger: Manual / Schedule / URL Match / Page Load
2. Navigate: Open URL / Search / Click Link
3. Extract: Text / Links / Images / Tables / Price
4. Input: Type Text / Select Dropdown / Click Button
5. Wait: Delay / Element Appear / Page Load
6. Condition: If/Else based on content
7. AI: Summarize / Analyze / Decide / Generate
8. Loop: Repeat N times / For each item
9. Save: To Notes / To File / To Clipboard
10. Notify: Show notification / Alert

WORKFLOW STORAGE:
- Save/load workflows
- Share workflows (export JSON)
- Import workflows
- Workflow templates (pre-built)

TEMPLATES:
- Price checker (daily price check)
- News summarizer
- Form auto-filler
- Data scraper
- Social media poster (with confirmation)
- Email checker
- Weather briefing
- Stock price tracker

SCHEDULING:
- One-time
- Daily
- Weekly
- Custom interval
- Via WorkManager for background execution
```

---

## 13_DOWNLOAD_MANAGER

```
FEATURES:
- Multi-thread downloading (split file into chunks)
- Pause/resume support
- Download queue management
- Speed throttling
- Retry on failure (3 attempts)
- Download history with search
- File categorization (Documents/Images/Videos/Audio/Archives/Other)
- Duplicate URL detection (warn before re-download)
- Clipboard URL detection (prompt to download)
- Hash verification (MD5/SHA256 if provided)
- MIME type detection
- Custom download directory per category
- Download notification with progress
- Background download support
- WiFi-only option
- Download scheduler (download later)
- Batch download (AI: "download all PDFs from this page")

IMPLEMENTATION:
- OkHttp for HTTP requests
- Support Range header for resume
- ContentResolver for saving to public directories
- Notification with progress bar
- Room database for download records
- Foreground service for active downloads
```

---

## 14_PDF_SYSTEM

```
BUILT-IN PDF VIEWER:
- Android PdfRenderer for rendering
- Page-by-page display
- Pinch-to-zoom
- Page thumbnails strip
- Jump to page
- Search in PDF (text extraction)
- Bookmark pages
- Night mode (dark background)
- Horizontal/Vertical scroll
- Single/Double page mode

AI FEATURES:
- Summarize entire PDF
- Ask questions about PDF
- Extract specific information
- Translate PDF content
- Generate notes from PDF
- Generate flashcards
- Generate MCQs
- Find specific mentions
- Extract tables
- Extract images (as descriptions)

TEXT EXTRACTION:
- PdfRenderer + Canvas for bitmap rendering
- Text extraction via PDF text content where available
- OCR fallback using on-device ML Kit if text extraction fails

UI:
- Bottom bar: page navigation, zoom, AI button
- Side panel: thumbnails
- Top bar: search, share, bookmark
- AI overlay: chat about PDF
```

---

## 15_DEVELOPER_TOOLS

```
CONSOLE:
- Capture console.log/warn/error/info from WebView
- Filter by level
- Search console output
- Clear console
- Timestamp each entry
- Copy individual entries
- JSON pretty-print

NETWORK INSPECTOR:
- Intercept WebView resource requests via shouldInterceptRequest
- Log: URL, method, status, size, time, type
- Filter by type (XHR, Script, CSS, Image, Font, Doc)
- Search requests
- View request/response headers
- View response body (text/JSON)
- Copy as cURL
- Replay request (open in API tester)

DOM INSPECTOR:
- Inject JavaScript to get DOM tree
- View element hierarchy
- View element properties (tag, classes, id, attributes)
- View computed styles
- Highlight element on page
- Copy CSS selector
- Copy XPath
- View element dimensions

STORAGE INSPECTOR:
- LocalStorage viewer/editor
- SessionStorage viewer/editor
- Cookie viewer/editor/deleter
- IndexedDB viewer
- Cache storage viewer

AI DEBUGGER:
- "Why is this page slow?" → AI analyzes network + console + DOM
- "Find JavaScript errors" → AI reads console
- "Analyze this API response" → AI parses network data
- "Suggest performance improvements" → AI reviews all data
- "Explain this error" → AI explains console error

SOURCE VIEWER:
- View page HTML source
- Syntax highlighting
- Search in source
- Copy source
- View specific resource source (JS/CSS)
```

---

## 16_CODE_WORKSPACE

```
IN-BROWSER CODE EDITOR:
- Syntax highlighting (HTML, CSS, JS, JSON, Markdown, Python, Kotlin)
- Line numbers
- Auto-indent
- Bracket matching
- Word wrap toggle
- Find/Replace
- Multiple files (tabs)
- File tree sidebar
- Live preview (HTML/CSS/JS in WebView)
- Markdown preview
- JSON formatter/validator

AI CODE ASSISTANT:
- Generate code from description
- Explain code
- Find bugs
- Refactor code
- Add comments
- Convert between languages
- Generate unit tests
- Optimize performance
- Create boilerplate
- Code completion suggestions

API TESTER (Postman-lite):
- HTTP method selection (GET/POST/PUT/DELETE/PATCH)
- URL input
- Headers editor
- Body editor (raw/JSON/form)
- Auth options (Bearer, Basic, API Key)
- Send request
- View response (status, headers, body, time)
- Response body formatting (JSON pretty-print)
- Save requests
- Request history
- Collections
```

---

## 17_PRIVACY_ENGINE

```
TRACKER BLOCKING:
- Built-in tracker database (curated list)
- Categories: Analytics, Advertising, Social, Fingerprinting
- Per-site whitelist
- Counter (trackers blocked per page/session/total)
- Network request interception via shouldInterceptRequest
- Block known tracker domains
- Block tracking parameters from URLs (utm_, fbclid, etc.)

AD BLOCKING:
- Cosmetic blocking (hide ad elements via CSS injection)
- Network blocking (block ad domains)
- Custom filter list support (EasyList format parsing simplified)
- Per-site toggle

FINGERPRINT PROTECTION:
- Canvas fingerprint noise injection
- WebGL fingerprint protection
- AudioContext protection
- Font enumeration protection
- Screen resolution masking
- Hardware concurrency masking
- JavaScript injection to override fingerprinting APIs

COOKIE CONTROLS:
- Block third-party cookies
- Auto-delete cookies on tab close
- Cookie whitelist per site
- View all cookies per site
- Clear all cookies

PERMISSION CONTROLS:
- Location: ask/allow/block per site
- Camera: ask/allow/block per site
- Microphone: ask/allow/block per site
- Notifications: ask/allow/block per site
- Clipboard: controlled
- Auto-play: controlled

ADDITIONAL:
- HTTPS-only mode (warn on HTTP)
- WebRTC IP leak protection
- Referrer policy control
- Do Not Track header
- Global Privacy Control header
- Clear browsing data (history, cache, cookies, passwords, form data, downloads)
- Clear on exit option
- Incognito mode (private browsing, no history/cookies saved)

AI PRIVACY INSPECTOR:
- Scan current page
- List all trackers found
- List all cookies
- List all permissions requested
- Identify suspicious scripts
- Privacy score (A-F rating)
- Recommendations
```

---

## 18_PASSWORD_MANAGER

```
FEATURES:
- Auto-detect login forms (JavaScript injection)
- Auto-fill credentials (after biometric/PIN verification)
- Save new credentials prompt
- Password generator:
  - Length slider (8-64)
  - Uppercase/lowercase/numbers/symbols toggles
  - Pronounceable option
  - Copy to clipboard
- Credential storage:
  - Encrypted with Android Keystore
  - Room database for metadata
  - EncryptedSharedPreferences for master verification
- Breach detection:
  - Check against HIBP API (k-anonymity, only hash prefix sent)
  - Mark compromised passwords
- Weak password detection:
  - Length < 8
  - No complexity
  - Common passwords list
  - Reused passwords
- Security dashboard:
  - Total passwords
  - Weak passwords count
  - Reused passwords count
  - Compromised passwords count
  - Overall score
- Biometric unlock (fingerprint/face)
- Export passwords (encrypted JSON)
- Import passwords (CSV/JSON)
- Auto-clear clipboard after copy (30 seconds)
- NEVER send passwords to AI APIs
- Password categories/tags
- Search passwords
- Sort by site/date/strength
```

---

## 19_MEDIA_FEATURES

```
VIDEO:
- Picture-in-Picture (Android PiP API)
- Background playback (audio continues when minimized)
- Playback speed control (0.5x to 3x)
- Video detection on page
- Mini player overlay
- Fullscreen controls

AUDIO:
- Media session integration (notification controls)
- Background audio playback
- Play/pause/skip in notification

SCREENSHOTS:
- Full page screenshot (scroll capture)
- Visible area screenshot
- Screenshot with annotation
- Save/share screenshot

AI MEDIA FEATURES:
- Describe image (vision model)
- Transcribe video (if captions available)
- Summarize video (via transcript/description)
- Translate media content
```

---

## 20_AI_MEMORY

```
LOCAL MEMORY SYSTEM:
- Room database for memory entries
- Memory types:
  - Preference ("User prefers dark mode")
  - Workflow ("When on GitHub, show repos")
  - Instruction ("Always summarize in bullet points")
  - Context ("User is a developer working on Android")
  - Site-specific ("On Amazon, compare prices")

MEMORY FEATURES:
- Auto-create memories from conversations (AI suggests)
- Manual create/edit/delete
- Enable/disable individual memories
- Memory categories
- Memory search
- Memory used indicator (show when AI uses a memory)
- Memory export/import
- Privacy: memories stored locally only, never sent to API except as system prompt context
- Token budget for memories (limit total memory tokens in system prompt)

MEMORY FORMAT IN SYSTEM PROMPT:
"User preferences and memories:
- [memory 1]
- [memory 2]
- [memory 3]
Consider these when responding."
```

---

## 21_SYNC (LOCAL FIRST)

```
INITIAL VERSION: Local-only storage
ARCHITECTURE PREPARED FOR: Future sync

Data stored locally:
- Bookmarks (Room)
- History (Room)
- Tabs/Sessions (Room)
- Downloads (Room)
- Passwords (Encrypted Room)
- Settings (DataStore)
- AI Memories (Room)
- Workspaces (Room)
- Automations (Room)
- Notes (Room)

EXPORT/IMPORT:
- Full data export (encrypted ZIP)
- Selective export
- Import from file
- This serves as manual "sync" / backup

FUTURE SYNC ARCHITECTURE:
- Firebase / custom server
- End-to-end encryption
- Conflict resolution
- Selective sync (choose what to sync)
(Not implemented in v1, but data models support it)
```

---

## 22_ADDITIONAL_MOBILE_FEATURES

```
MOBILE-SPECIFIC ENHANCEMENTS:

GESTURES:
- Swipe left/right on address bar: back/forward
- Swipe down: refresh
- Swipe up from bottom: quick actions
- Long press back button: history popup
- Two-finger swipe: switch tabs
- Pull down address bar: search
- Shake device: AI command (optional)

BOTTOM NAVIGATION:
- Optimized for one-handed use
- Bottom address bar option
- Bottom toolbar
- Tab switcher at bottom
- AI button at bottom-right

NOTIFICATIONS:
- Download progress
- Download complete
- AI task complete
- Automation results
- Security alerts

WIDGETS:
- Search widget
- Quick actions widget
- Bookmarks widget

SHORTCUTS:
- App shortcuts (long press icon):
  - New tab
  - Private tab
  - Search
  - AI Assistant
  - Scan QR

SHARE:
- Share to NOVA (receive URLs from other apps)
- Share from NOVA (share pages to other apps)
- Share page as QR code
- Copy link

ACCESSIBILITY:
- Screen reader support
- Content descriptions on all elements
- Sufficient contrast ratios
- Touch target sizes (48dp minimum)
- Font size scaling
- Reduce motion option

PERFORMANCE:
- Tab hibernation (free memory for background tabs)
- Image lazy loading
- Cache management
- WebView pool (reuse WebView instances)
- Aggressive garbage collection on low memory
- Battery optimization (reduce network when battery low)
```

---

## 23_DATABASE_SCHEMA

```kotlin
// Room Database: NovaDatabase

@Entity(tableName = "bookmarks")
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val favicon: String? = null,
    val folderId: Long? = null,
    val folderName: String? = null,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val isFolder: Boolean = false
)

@Entity(tableName = "history")
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val favicon: String? = null,
    val visitedAt: Long = System.currentTimeMillis(),
    val visitCount: Int = 1
)

@Entity(tableName = "tabs")
data class TabEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val title: String = "New Tab",
    val url: String = "",
    val favicon: String? = null,
    val isActive: Boolean = false,
    val isPinned: Boolean = false,
    val isPrivate: Boolean = false,
    val groupId: String? = null,
    val groupName: String? = null,
    val position: Int = 0,
    val parentTabId: String? = null,
    val lastAccessed: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val screenshot: String? = null, // file path
    val isSleeping: Boolean = false
)

@Entity(tableName = "downloads")
data class DownloadEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val fileName: String,
    val filePath: String,
    val mimeType: String,
    val totalSize: Long = 0,
    val downloadedSize: Long = 0,
    val status: String = "pending", // pending, downloading, paused, completed, failed
    val threadCount: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val completedAt: Long? = null,
    val category: String = "Other", // Document, Image, Video, Audio, Archive, Other
    val hash: String? = null,
    val errorMessage: String? = null
)

@Entity(tableName = "passwords")
data class PasswordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val domain: String,
    val username: String,
    val encryptedPassword: String, // encrypted with Android Keystore
    val iv: String, // initialization vector for decryption
    val favicon: String? = null,
    val notes: String? = null,
    val category: String? = null,
    val isCompromised: Boolean = false,
    val isWeak: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastUsedAt: Long? = null
)

@Entity(tableName = "ai_memory")
data class AIMemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val content: String,
    val type: String, // preference, workflow, instruction, context, site_specific
    val isEnabled: Boolean = true,
    val category: String? = null,
    val relatedSite: String? = null,
    val usageCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "workspaces")
data class WorkspaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val icon: String? = null,
    val color: String? = null,
    val tabIds: String = "[]", // JSON array of tab IDs
    val isActive: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "automations")
data class AutomationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val description: String,
    val workflowJson: String, // JSON workflow definition
    val triggerType: String, // manual, schedule, url_match
    val triggerValue: String? = null, // cron expression or URL pattern
    val isEnabled: Boolean = true,
    val lastRunAt: Long? = null,
    val runCount: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val sourceUrl: String? = null,
    val sourceTitle: String? = null,
    val type: String = "note", // note, research, summary, code
    val tags: String = "[]", // JSON array
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
```

---

## 24_ERROR_HANDLING

```
EVERY LAYER MUST HANDLE ERRORS:

NETWORK:
- No internet → show offline message + cached content if available
- Timeout → retry once, then show error
- Server error → show appropriate message
- SSL error → warn user, option to proceed

AI:
- No API key → prompt to add key
- Invalid API key → clear error message
- Rate limit → show "please wait" with countdown
- Model not available → fallback to another model
- Context too long → auto-truncate with warning
- Empty response → "AI couldn't generate a response"
- Parsing error → show raw response with error note
- Streaming interrupted → show partial response + retry button

BROWSER:
- Page load error → custom error page with retry
- SSL certificate error → warning page with details
- File not found (404) → custom 404 page
- Permission denied → explanation + settings link
- WebView crash → recover tab with last URL

DOWNLOADS:
- File already exists → ask overwrite/rename
- Storage full → clear message
- Permission denied → request permission
- Connection lost during download → auto-pause, retry when connected

DATABASE:
- Migration failure → backup + reset
- Corruption → detect + recover
- Full storage → warn user

GENERAL:
- Any unhandled exception → crash recovery (save state, restore on restart)
- ANR prevention → all heavy work on background threads
- Memory pressure → release caches, hibernate tabs
```

---

## 25_PERFORMANCE

```
TARGETS:
- Cold start: < 2 seconds
- Tab switch: < 200ms
- AI response start (streaming): < 1 second
- Page load: match or beat Chrome WebView
- Memory per tab: < 100MB average
- Battery: no background drain when not actively downloading/automating

OPTIMIZATIONS:
- Lazy loading of all features (modules loaded on first use)
- WebView pool (max 3 active WebViews, others hibernated)
- Tab hibernation (free WebView memory for background tabs, keep state)
- Image caching with size limits (Coil)
- Response caching (AI responses for identical queries)
- Database query optimization (indices on frequently queried columns)
- Compose performance (remember, derivedStateOf, stable parameters)
- ProGuard/R8 minification
- Remove unused resources
- Baseline profiles (if time permits)
- Efficient recomposition (avoid unnecessary state changes)
```

---

## 26_TESTING_STRATEGY

```
UNIT TESTS:
- AI engine (mock API responses)
- Context engine (content extraction)
- URL parsing
- Token counting
- Download engine
- Password encryption/decryption
- Tracker matching
- Automation workflow parsing

INTEGRATION TESTS:
- Database operations (all DAOs)
- Repository layer
- ViewModel state management

UI TESTS:
- Navigation flow
- Address bar interaction
- Tab management
- Settings changes
- AI sidebar modes

MANUAL TEST CHECKLIST:
- Fresh install flow
- Add API key flow
- Browse website
- AI chat on page
- AI summarize
- AI agent basic task
- Download file
- Save bookmark
- View history
- Password save/autofill
- Privacy dashboard
- DevTools console
- PDF viewer
- Rotation handling
- Low memory behavior
- No internet behavior
- Back button behavior
```

---

## 27_BUILD_CONFIGURATION

```
// build.gradle.kts (Project)
- Kotlin 1.9.22
- AGP 8.2.2
- Compose compiler 1.5.10
- Hilt 2.50

// build.gradle.kts (App)
- compileSdk = 34
- minSdk = 26
- targetSdk = 34
- versionCode = 1
- versionName = "1.0.0"
- buildFeatures.compose = true
- buildFeatures.buildConfig = true

// Key Dependencies:
- compose-bom: 2024.02.00
- compose-material3: latest
- compose-material-icons-extended
- hilt-android: 2.50
- hilt-navigation-compose: 1.2.0
- room-runtime/compiler/ktx: 2.6.1
- retrofit: 2.9.0
- okhttp: 4.12.0
- okhttp-logging-interceptor
- okhttp-sse (server-sent events)
- kotlinx-serialization-json: 1.6.3
- coil-compose: 2.5.0
- datastore-preferences: 1.0.0
- exoplayer: 2.19.1 (or media3)
- biometric: 1.2.0-alpha05
- security-crypto: 1.1.0-alpha06
- work-runtime-ktx: 2.9.0
- lottie-compose: 6.4.0
- accompanist-permissions
- accompanist-webview (reference only, using custom)
```

---

## 28_GITHUB_ACTIONS_WORKFLOW

```yaml
# .github/workflows/build.yml
name: NOVA AI Browser CI

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
    - name: Checkout code
      uses: actions/checkout@v4
    
    - name: Set up JDK 17
      uses: actions/setup-java@v4
      with:
        java-version: '17'
        distribution: 'temurin'
        cache: gradle
    
    - name: Grant execute permission for gradlew
      run: chmod +x gradlew
    
    - name: Cache Gradle packages
      uses: actions/cache@v4
      with:
        path: |
          ~/.gradle/caches
          ~/.gradle/wrapper
        key: ${{ runner.os }}-gradle-${{ hashFiles('**/*.gradle*', '**/gradle-wrapper.properties') }}
        restore-keys: |
          ${{ runner.os }}-gradle-
    
    - name: Run Lint
      run: ./gradlew lint
    
    - name: Run Unit Tests
      run: ./gradlew testDebugUnitTest
    
    - name: Build Debug APK
      run: ./gradlew assembleDebug
    
    - name: Upload APK
      uses: actions/upload-artifact@v4
      with:
        name: nova-browser-debug
        path: app/build/outputs/apk/debug/app-debug.apk
    
    - name: Upload Test Results
      if: always()
      uses: actions/upload-artifact@v4
      with:
        name: test-results
        path: app/build/reports/tests/
    
    - name: Upload Lint Results
      if: always()
      uses: actions/upload-artifact@v4
      with:
        name: lint-results
        path: app/build/reports/lint-results-debug.html
```

---

## 29_PHASED_IMPLEMENTATION

```
YOU MUST IMPLEMENT IN THIS ORDER:

PHASE 1: Project Setup + Core
- build.gradle files
- AndroidManifest.xml
- Application class
- Theme/Design system (ALL glass components)
- Database (ALL entities + DAOs)
- DI modules
- Navigation graph
- Constants/Utils

PHASE 2: Browser Core
- WebView container with full configuration
- Address bar (URL input + search)
- Navigation controls (back/forward/refresh/stop)
- Page loading indicator
- Tab system (create/switch/close/list)
- Home screen
- Basic history recording
- Basic bookmarks

PHASE 3: Premium UI
- Glass design system components (ALL of them)
- Home screen with AI greeting
- Tab manager with cards + groups
- Settings screens (all sections)
- Animations and transitions
- Bottom toolbar
- Menu bottom sheet
- Find in page

PHASE 4: AI Core
- Gemini API client
- OpenRouter API client  
- AI Engine manager
- Model selector
- API key manager (encrypted)
- Streaming response handler
- Token tracker
- AI Sidebar (all modes)
- AI floating button
- Command palette
- Context engine

PHASE 5: AI Agent
- Page interaction via JavaScript
- Action executor
- Workflow engine
- Safety checker
- Confirmation dialogs
- Step indicator UI
- Agent results display
- Basic multi-step workflows

PHASE 6: Search + Research
- AI-enhanced search
- Search suggestions
- AI search results display
- Source cards
- Research mode (multi-page)
- Research results panel
- Citations

PHASE 7: Privacy + Security
- Tracker blocker
- Ad blocker (basic)
- Cookie controls
- Permission manager
- Fingerprint protection
- HTTPS-only mode
- Privacy dashboard
- AI privacy inspector
- Password manager
- Password generator
- Biometric auth
- Security dashboard

PHASE 8: Downloads + PDF
- Download manager engine
- Multi-thread downloads
- Download UI
- Download notifications
- PDF viewer
- PDF AI features
- PDF navigation

PHASE 9: Developer Tools
- Console
- Network inspector
- DOM inspector
- Storage inspector
- Source viewer
- AI debugger
- Code workspace
- Code editor
- Live preview
- API tester

PHASE 10: Automation + Media + Polish
- Automation studio
- Workflow builder
- Automation templates
- Media player features
- PiP
- Screenshots
- AI memory system
- Workspaces
- Notes
- Export/Import
- Final error handling
- Performance optimization
- Testing

EACH PHASE:
- Write ALL code for that phase
- Every file must be COMPLETE
- Handle ALL error cases
- Include ALL UI states (loading, error, empty, success)
- After each phase, the app should COMPILE AND RUN
```

---

## 30_FINAL_ACCEPTANCE_CRITERIA

```
THE APP IS ONLY COMPLETE WHEN:

✅ App compiles without errors on SDK 34
✅ App installs and runs on Android 8+ device
✅ Home screen loads with AI greeting and quick actions
✅ User can enter URL and navigate
✅ Back/forward/refresh works
✅ Tabs can be created, switched, closed
✅ Tab groups work
✅ Vertical tab list works
✅ History is recorded and displayed
✅ Bookmarks can be saved, organized, deleted
✅ Address bar shows suggestions
✅ Glass UI theme is applied everywhere
✅ All glass components render correctly
✅ Animations are smooth (60fps)
✅ AI sidebar opens with all modes
✅ Gemini API integration works (with key)
✅ OpenRouter API integration works (with key)
✅ Model selection works
✅ Streaming responses display correctly
✅ AI can summarize current page
✅ AI can answer questions about page
✅ AI can translate page content
✅ AI agent can navigate to URLs
✅ AI agent can click elements
✅ AI agent can fill forms
✅ AI agent shows step progress
✅ Destructive actions require confirmation
✅ AI search works
✅ Research mode works
✅ Tracker blocking works
✅ Privacy dashboard shows stats
✅ Cookie controls work
✅ Permission controls work
✅ HTTPS-only mode works
✅ Password save/autofill works
✅ Password generator works
✅ Biometric auth works
✅ Security dashboard works
✅ Downloads work (pause/resume)
✅ Download notifications work
✅ PDF viewer works
✅ PDF AI features work
✅ DevTools console works
✅ Network inspector works
✅ Code editor works
✅ Automation studio works
✅ AI memory works
✅ Workspaces work
✅ Settings all work
✅ Rotation handling works
✅ Error states handled everywhere
✅ Empty states shown everywhere
✅ Loading states shown everywhere
✅ No internet handled gracefully
✅ No API key handled gracefully
✅ Rate limits handled gracefully
✅ GitHub Actions build passes
✅ APK size < 30MB
✅ Cold start < 2 seconds
✅ All sensitive data encrypted
✅ Passwords NEVER sent to AI
✅ ProGuard/R8 configured
✅ No deprecated API usage
✅ Touch targets >= 48dp
✅ Content descriptions on interactive elements
```

---

## 🚀 IMPLEMENTATION COMMAND

```
NOW START IMPLEMENTING.

Begin with PHASE 1: Project Setup + Core.

Write EVERY file completely. 
No placeholders. No TODOs. No shortcuts.
If you hit output limits, say "CONTINUE FROM: [file:line]" and continue in next message.

Start with:
1. settings.gradle.kts
2. build.gradle.kts (project)
3. app/build.gradle.kts
4. gradle.properties
5. AndroidManifest.xml
6. NovaApplication.kt
7. Theme system (ALL files)
8. Glass components (ALL)
9. Database (ALL entities + DAOs + Database class)
10. DI modules (ALL)
11. Utils (ALL)
12. Security (ALL)
13. Navigation
14. MainActivity.kt

Then proceed to PHASE 2, then PHASE 3, and so on until PHASE 10.

DO NOT STOP until every file in the architecture is written.
GO.
```
