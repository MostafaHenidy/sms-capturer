package com.medora.paymentsms.sms

/**
 * Heuristic sender allowlist for the pilot only. A match is NOT proof of
 * payment; it merely decides whether a message is worth classifying locally.
 *
 * Replace/extend with real, manually redacted sender IDs from the pilot
 * handset before distribution. Unknown senders are dropped without storage.
 */
class SenderAllowlist(
    private val rules: List<Rule> = DEFAULT_RULES,
) {
    data class Rule(
        val sourceId: String,
        val exact: Set<String> = emptySet(),
        val patterns: List<Regex> = emptyList(),
    )

    fun match(sender: String): String? {
        val value = sender.trim()
        if (value.isEmpty()) return null

        for (rule in rules) {
            if (value.uppercase() in rule.exact) return rule.sourceId
            if (rule.patterns.any { it.containsMatchIn(value) }) return rule.sourceId
        }
        return null
    }

    companion object {
        private val DEFAULT_RULES = listOf(
            Rule(
                sourceId = "vodafone_cash",
                exact = setOf(
                    "VODAFONE CASH", "VODAFONE", "VODAFONE CASH EGYPT",
                    "VF CASH", "8888", "900",
                ),
                patterns = listOf(
                    Regex("(?i)^vodafone[\\s_-]*cash"),
                    Regex("(?i)^vf[\\s_-]*cash"),
                    Regex("(?i)^vodafone\\s+cash\\s+egypt$"),
                ),
            ),
        )
    }
}
