package com.nova.browser.features.agent.engine

import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Blocks or gates dangerous agent actions (spec 09 → SAFETY RULES).
 * Payments, message sending, deletion and financial logins always require
 * explicit user confirmation and can never be auto-executed.
 */
@Singleton
class SafetyChecker @Inject constructor() {

    enum class Risk { SAFE, CAUTION, DESTRUCTIVE, BLOCKED }

    data class Verdict(val risk: Risk, val reason: String) {
        val requiresConfirmation: Boolean get() = risk == Risk.DESTRUCTIVE || risk == Risk.CAUTION
        val isBlocked: Boolean get() = risk == Risk.BLOCKED
    }

    private val paymentTerms = listOf(
        "pay", "payment", "checkout", "buy now", "place order", "purchase", "subscribe",
        "confirm order", "complete purchase", "add card", "billing", "donate", "tip",
        "transfer", "send money", "withdraw", "deposit", "bid"
    )

    private val sendTerms = listOf(
        "send", "post", "publish", "tweet", "submit review", "comment", "reply",
        "share now", "invite", "message"
    )

    private val deleteTerms = listOf(
        "delete", "remove", "erase", "clear all", "deactivate", "close account",
        "unsubscribe", "cancel subscription", "wipe", "reset account", "revoke"
    )

    private val financialHosts = listOf(
        "bank", "paypal", "stripe", "wise.com", "revolut", "coinbase", "binance",
        "kraken", "chase", "wellsfargo", "hsbc", "barclays", "citi", "amex",
        "venmo", "cashapp", "robinhood", "fidelity", "schwab"
    )

    private val credentialTerms = listOf("password", "passwd", "otp", "2fa", "verification code", "pin")

    /** Evaluates a single planned action in the context of the current page. */
    fun check(action: AgentAction, currentUrl: String): Verdict {
        val haystack = listOfNotNull(action.target, action.value, action.reason)
            .joinToString(" ")
            .lowercase(Locale.US)
        val host = currentUrl.lowercase(Locale.US)

        // Never type credentials — the password manager handles those explicitly.
        if (action.type == AgentActionType.TYPE || action.type == AgentActionType.TYPE_LABEL) {
            if (credentialTerms.any { haystack.contains(it) }) {
                return Verdict(
                    Risk.BLOCKED,
                    "NOVA never lets the agent type passwords or verification codes. Use the password manager instead."
                )
            }
        }

        val isFinancial = financialHosts.any { host.contains(it) }
        val isClick = action.type in setOf(AgentActionType.CLICK, AgentActionType.CLICK_TEXT)

        if (isClick || action.type == AgentActionType.NAVIGATE) {
            if (paymentTerms.any { haystack.contains(it) }) {
                return Verdict(
                    Risk.DESTRUCTIVE,
                    "This step looks like it completes a payment or purchase. NOVA will never do that without your explicit approval."
                )
            }
            if (deleteTerms.any { haystack.contains(it) }) {
                return Verdict(
                    Risk.DESTRUCTIVE,
                    "This step looks like it deletes or cancels something permanently."
                )
            }
            if (sendTerms.any { haystack.contains(it) }) {
                return Verdict(
                    Risk.DESTRUCTIVE,
                    "This step looks like it sends or publishes content on your behalf."
                )
            }
            if (isFinancial) {
                return Verdict(
                    Risk.CAUTION,
                    "You're on a financial site. Confirm before the agent interacts with it."
                )
            }
        }

        if (action.type == AgentActionType.DOWNLOAD) {
            return Verdict(Risk.CAUTION, "The agent wants to download a file.")
        }

        if (action.type == AgentActionType.CLOSE_TAB) {
            return Verdict(Risk.CAUTION, "The agent wants to close a tab.")
        }

        return Verdict(Risk.SAFE, "")
    }

    /** Pre-flight check on the whole plan; returns indices needing confirmation. */
    fun review(plan: List<AgentAction>, currentUrl: String): Map<Int, Verdict> =
        plan.mapIndexed { index, action -> index to check(action, currentUrl) }
            .filter { it.second.risk != Risk.SAFE }
            .toMap()

    /** Guards against runaway loops. */
    fun validatePlan(plan: List<AgentAction>, maxSteps: Int): String? = when {
        plan.isEmpty() -> "The agent couldn't produce any steps for this goal."
        plan.size > maxSteps -> "The plan has ${plan.size} steps, which exceeds your limit of $maxSteps."
        else -> null
    }
}
