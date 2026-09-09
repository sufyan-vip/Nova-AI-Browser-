package com.nova.browser.features.agent.engine

import com.nova.browser.core.utils.UrlUtils
import kotlinx.coroutines.delay
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Turns a single [AgentAction] into real browser work. Navigation and tab
 * operations are delegated back to the browser layer through [BrowserBridge]
 * so the executor stays free of Android UI dependencies.
 */
@Singleton
class ActionExecutor @Inject constructor(
    private val pageInteractor: PageInteractor
) {
    /** Hooks the executor into the running browser session. */
    interface BrowserBridge {
        suspend fun navigate(url: String)
        suspend fun search(query: String)
        suspend fun goBack()
        suspend fun goForward()
        suspend fun newTab(url: String?)
        suspend fun switchTab(indexOrTitle: String)
        suspend fun closeTab()
        suspend fun download(url: String)
        suspend fun screenshot(): String?
        suspend fun saveNote(title: String, content: String)
        suspend fun askAi(instruction: String, pageText: String): String
    }

    data class Outcome(val success: Boolean, val message: String, val data: String? = null)

    suspend fun execute(action: AgentAction, bridge: BrowserBridge): Outcome {
        return try {
            when (action.type) {
            AgentActionType.NAVIGATE -> {
                val url = UrlUtils.toUrlOrSearch(action.target ?: action.value.orEmpty())
                if (url.isBlank()) {
                    Outcome(false, "No URL given to open")
                } else {
                    bridge.navigate(url)
                    pageInteractor.awaitLoad()
                    Outcome(true, "Opened ${UrlUtils.displayUrl(pageInteractor.currentUrl())}")
                }
            }

            AgentActionType.SEARCH -> {
                val query = action.target ?: action.value.orEmpty()
                if (query.isBlank()) {
                    Outcome(false, "No search query given")
                } else {
                    bridge.search(query)
                    pageInteractor.awaitLoad()
                    Outcome(true, "Searched for \"$query\"")
                }
            }

            AgentActionType.CLICK -> {
                val selector = action.target.orEmpty()
                if (selector.isBlank()) return Outcome(false, "No element to click")
                val result = pageInteractor.click(selector)
                if (result.ok) {
                    pageInteractor.awaitLoad(6000)
                    Outcome(true, "Clicked ${result.text.ifBlank { selector }}")
                } else {
                    Outcome(false, result.error ?: "Element not found: $selector")
                }
            }

            AgentActionType.CLICK_TEXT -> {
                val text = action.target ?: action.value.orEmpty()
                if (text.isBlank()) return Outcome(false, "No text to click")
                val result = pageInteractor.clickText(text)
                if (result.ok) {
                    pageInteractor.awaitLoad(6000)
                    Outcome(true, "Clicked \"${result.text.ifBlank { text }}\"")
                } else {
                    Outcome(false, result.error ?: "No clickable element matching \"$text\"")
                }
            }

            AgentActionType.TYPE -> {
                val selector = action.target.orEmpty()
                val value = action.value.orEmpty()
                if (selector.isBlank()) return Outcome(false, "No field to type into")
                val result = pageInteractor.type(selector, value)
                if (result.ok) Outcome(true, "Typed into $selector")
                else Outcome(false, result.error ?: "Field not found: $selector")
            }

            AgentActionType.TYPE_LABEL -> {
                val label = action.target.orEmpty()
                val result = pageInteractor.typeByLabel(label, action.value.orEmpty())
                if (result.ok) Outcome(true, "Filled \"$label\"")
                else Outcome(false, result.error ?: "No field labelled \"$label\"")
            }

            AgentActionType.SELECT -> {
                val result = pageInteractor.select(action.target.orEmpty(), action.value.orEmpty())
                if (result.ok) Outcome(true, "Selected \"${result.text.ifBlank { action.value.orEmpty() }}\"")
                else Outcome(false, result.error ?: "Couldn't select that option")
            }

            AgentActionType.CHECK -> {
                val checked = action.value?.lowercase() != "false" && action.value?.lowercase() != "off"
                val result = pageInteractor.setCheckbox(action.target.orEmpty(), checked)
                if (result.ok) Outcome(true, if (checked) "Checked the box" else "Unchecked the box")
                else Outcome(false, result.error ?: "Checkbox not found")
            }

            AgentActionType.SCROLL -> {
                val direction = (action.value ?: action.target ?: "down").lowercase()
                val amount = when {
                    direction.contains("up") -> -800
                    direction.contains("top") -> -100_000
                    direction.contains("bottom") -> 100_000
                    else -> direction.toIntOrNull() ?: 800
                }
                pageInteractor.scroll(amount)
                delay(400)
                Outcome(true, "Scrolled ${if (amount < 0) "up" else "down"}")
            }

            AgentActionType.SCROLL_TO -> {
                val result = pageInteractor.scrollTo(action.target.orEmpty())
                delay(500)
                if (result.ok) Outcome(true, "Scrolled to ${action.target.orEmpty()}")
                else Outcome(false, result.error ?: "Element not found")
            }

            AgentActionType.WAIT -> {
                val seconds = (action.value ?: action.target)?.toDoubleOrNull()?.coerceIn(0.5, 15.0) ?: 2.0
                delay((seconds * 1000).toLong())
                Outcome(true, "Waited ${seconds}s")
            }

            AgentActionType.WAIT_FOR -> {
                val selector = action.target.orEmpty()
                val found = pageInteractor.waitFor(selector)
                if (found) Outcome(true, "$selector appeared")
                else Outcome(false, "$selector never appeared")
            }

            AgentActionType.EXTRACT -> {
                val selector = action.target.orEmpty().ifBlank { "body" }
                val result = pageInteractor.extract(selector)
                if (result.ok) {
                    val data = result.values.joinToString("\n").ifBlank { result.text }
                    Outcome(true, "Extracted ${result.values.size.coerceAtLeast(1)} item(s)", data)
                } else {
                    Outcome(false, result.error ?: "Nothing matched $selector")
                }
            }

            AgentActionType.EXTRACT_LINKS -> {
                val links = pageInteractor.extractLinks()
                if (links.isEmpty()) Outcome(false, "No links on this page")
                else Outcome(true, "Extracted ${links.size} links", links.joinToString("\n"))
            }

            AgentActionType.EXTRACT_TABLE -> {
                val tables = pageInteractor.extractTables()
                if (tables.isEmpty()) Outcome(false, "No tables on this page")
                else Outcome(true, "Extracted ${tables.size} table(s)", tables.joinToString("\n\n"))
            }

            AgentActionType.SCREENSHOT -> {
                val path = bridge.screenshot()
                if (path != null) Outcome(true, "Screenshot saved", path)
                else Outcome(false, "Couldn't capture a screenshot")
            }

            AgentActionType.READ_PAGE -> {
                val page = pageInteractor.readPage()
                if (page.isEmpty) Outcome(false, "This page has no readable text")
                else Outcome(true, "Read ${page.wordCount} words from \"${page.title}\"", page.text.take(4000))
            }

            AgentActionType.SUMMARIZE -> {
                val page = pageInteractor.readPage()
                if (page.isEmpty) return Outcome(false, "Nothing to summarize")
                val summary = bridge.askAi(
                    action.value ?: "Summarize this page in 5 concise bullet points.",
                    page.text.take(12_000)
                )
                Outcome(summary.isNotBlank(), summary.ifBlank { "The model returned nothing" }, summary)
            }

            AgentActionType.ANALYZE -> {
                val page = pageInteractor.readPage()
                val instruction = action.target ?: action.value ?: "Analyze this page."
                val answer = bridge.askAi(instruction, page.text.take(12_000))
                Outcome(answer.isNotBlank(), answer.ifBlank { "The model returned nothing" }, answer)
            }

            AgentActionType.BACK -> {
                bridge.goBack(); pageInteractor.awaitLoad(6000); Outcome(true, "Went back")
            }

            AgentActionType.FORWARD -> {
                bridge.goForward(); pageInteractor.awaitLoad(6000); Outcome(true, "Went forward")
            }

            AgentActionType.NEW_TAB -> {
                val url = action.target?.let { UrlUtils.toUrlOrSearch(it) }
                bridge.newTab(url)
                pageInteractor.awaitLoad(8000)
                Outcome(true, "Opened a new tab")
            }

            AgentActionType.SWITCH_TAB -> {
                bridge.switchTab(action.target ?: action.value.orEmpty())
                Outcome(true, "Switched tab")
            }

            AgentActionType.CLOSE_TAB -> {
                bridge.closeTab(); Outcome(true, "Closed the tab")
            }

            AgentActionType.DOWNLOAD -> {
                val url = action.target ?: action.value.orEmpty()
                if (!UrlUtils.isUrl(url)) return Outcome(false, "That isn't a downloadable URL")
                bridge.download(url)
                Outcome(true, "Download started")
            }

            AgentActionType.SAVE_NOTE -> {
                val title = action.target ?: pageInteractor.currentTitle().ifBlank { "Agent note" }
                bridge.saveNote(title, action.value.orEmpty())
                Outcome(true, "Saved a note")
            }

            AgentActionType.FINISH -> Outcome(true, action.value ?: action.reason ?: "Done")
        }
    } catch (e: Exception) {
            Outcome(false, e.message ?: "The step failed unexpectedly")
        }
    }
}
