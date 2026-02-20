package com.focusguard.app

import android.content.Context
import java.time.LocalDate

class AppUsageTracker(context: Context) {

    private val prefs = context.getSharedPreferences(
        "focus_guard_prefs", Context.MODE_PRIVATE
    )

    companion object {
        private const val KEY_MONITORED_APPS = "monitored_apps"
        private const val KEY_MONITORED_WEBSITES = "monitored_websites"
        private const val KEY_CHALLENGE_PARAGRAPH = "challenge_paragraph"
        private const val KEY_CHEAT_PREVENTION = "cheat_prevention_enabled"

        const val MIN_PARAGRAPH_LENGTH = 200

        val DEFAULT_PARAGRAPH =
            "I am choosing to override my own focus restrictions. " +
                "I understand that these limits exist to help me stay " +
                "productive and mindful of my screen time. Doing this " +
                "will weaken my prefrontal cortex, reducing my ability " +
                "to focus and resist distractions in the future. By " +
                "typing this entire paragraph, I am making a deliberate " +
                "and conscious decision to change my settings, and I " +
                "accept full responsibility for this choice."

        val PARAGRAPH_POOL = listOf(
            "I acknowledge that I am about to weaken my own " +
                "defenses against digital distraction. Every time " +
                "I give in to a craving for my phone, I am " +
                "weakening my prefrontal cortex and its ability " +
                "to exercise self control. The time I spend on " +
                "these apps is time I could invest in reading, " +
                "exercising, learning, or connecting with people " +
                "I care about. I am typing this because I " +
                "genuinely want to make a change, not because I " +
                "am giving in to a momentary urge.",

            "Right now I feel the pull of distraction, and I " +
                "recognize that feeling for what it is. Science " +
                "shows that surrendering to these impulses " +
                "gradually erodes my prefrontal cortex, the part " +
                "of my brain responsible for focus and willpower. " +
                "My future self will thank me for resisting, or " +
                "hold me accountable for giving in. Every minute " +
                "I reclaim from mindless scrolling is a minute I " +
                "can spend on something meaningful.",

            "This moment is a test of my self discipline. I set " +
                "these restrictions because I know how easy it is " +
                "to lose hours to screens without realizing it. " +
                "Each time I override my own limits, I am " +
                "training my prefrontal cortex to be weaker, " +
                "making it harder to resist next time. If I truly " +
                "need to change these settings, then typing this " +
                "paragraph is a small price to pay. If I am just " +
                "bored or restless, I should close this and find " +
                "something better to do with my time.",

            "I am making a conscious choice right now. The apps " +
                "and websites I blocked are designed to hijack my " +
                "attention and keep me scrolling for as long as " +
                "possible. They profit from my distraction while " +
                "my prefrontal cortex pays the price, growing " +
                "weaker with every mindless session. By typing " +
                "this paragraph I am acknowledging that I " +
                "understand the trade off and I am choosing to " +
                "proceed anyway, fully aware of the consequences.",

            "Before I continue, I want to ask myself an honest " +
                "question. Is this something I truly need to do, " +
                "or am I simply looking for an escape from boredom " +
                "or discomfort? Research confirms that giving in " +
                "to digital impulses degrades the prefrontal " +
                "cortex over time, reducing my capacity for deep " +
                "work and long term thinking. My focus restrictions " +
                "exist because past me knew that present me would " +
                "try exactly this. Typing this paragraph is my " +
                "way of proving that I am not acting on impulse."
        )
    }

    // ─── App Monitoring ──────────────────────────────

    fun isMonitored(packageName: String): Boolean {
        return packageName in getMonitoredApps()
    }

    fun getMonitoredApps(): Set<String> {
        return prefs.getStringSet(KEY_MONITORED_APPS, emptySet()) ?: emptySet()
    }

    fun setMonitoredApps(apps: Set<String>) {
        prefs.edit().putStringSet(KEY_MONITORED_APPS, apps).apply()
    }

    fun incrementOpenCount(packageName: String): Int {
        cleanOldEntries()
        val key = countKey(packageName)
        val count = prefs.getInt(key, 0) + 1
        prefs.edit().putInt(key, count).apply()
        return count
    }

    fun getOpenCount(packageName: String): Int {
        return prefs.getInt(countKey(packageName), 0)
    }

    fun resetCounts() {
        val editor = prefs.edit()
        prefs.all.keys
            .filter { it.startsWith("count_") || it.startsWith("wblock_") }
            .forEach { editor.remove(it) }
        editor.apply()
    }

    // ─── Website Monitoring ──────────────────────────

    fun getMonitoredWebsites(): Set<String> {
        return prefs.getStringSet(KEY_MONITORED_WEBSITES, emptySet())
            ?: emptySet()
    }

    fun setMonitoredWebsites(websites: Set<String>) {
        prefs.edit().putStringSet(KEY_MONITORED_WEBSITES, websites).apply()
    }

    fun addMonitoredWebsite(website: String) {
        val current = getMonitoredWebsites().toMutableSet()
        current.add(
            website.trim().lowercase()
                .removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("www.")
                .removeSuffix("/")
        )
        setMonitoredWebsites(current)
    }

    fun removeMonitoredWebsite(website: String) {
        val current = getMonitoredWebsites().toMutableSet()
        current.remove(website)
        setMonitoredWebsites(current)
    }

    fun isMonitoredWebsite(url: String): Boolean {
        val lowerUrl = url.lowercase()
        return getMonitoredWebsites().any { website ->
            lowerUrl.contains(website)
        }
    }

    fun findMatchingWebsite(url: String): String? {
        val lowerUrl = url.lowercase()
        return getMonitoredWebsites().firstOrNull { website ->
            lowerUrl.contains(website)
        }
    }

    fun incrementWebsiteBlockCount(website: String): Int {
        val key = "wblock_${website}_${LocalDate.now()}"
        val count = prefs.getInt(key, 0) + 1
        prefs.edit().putInt(key, count).apply()
        return count
    }

    fun getWebsiteBlockCount(website: String): Int {
        return prefs.getInt("wblock_${website}_${LocalDate.now()}", 0)
    }

    // ─── Challenge Paragraph ─────────────────────────

    fun getChallengeParagraph(): String {
        return prefs.getString(KEY_CHALLENGE_PARAGRAPH, null)
            ?: DEFAULT_PARAGRAPH
    }

    fun setChallengeParagraph(paragraph: String): Boolean {
        if (paragraph.length < MIN_PARAGRAPH_LENGTH) return false
        prefs.edit().putString(KEY_CHALLENGE_PARAGRAPH, paragraph).apply()
        return true
    }

    fun generateRandomizedChallenge(): String {
        val customParagraph = prefs.getString(KEY_CHALLENGE_PARAGRAPH, null)
        val baseParagraph = if (customParagraph != null) {
            customParagraph
        } else {
            PARAGRAPH_POOL.random()
        }

        val sentences = baseParagraph.split(". ")
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        if (sentences.size <= 2) return baseParagraph

        val shuffled = sentences.toMutableList().apply { shuffle() }
        return shuffled.joinToString(". ") { sent ->
            if (sent.endsWith(".")) sent.dropLast(1) else sent
        } + "."
    }

    // ─── Cheating Prevention ─────────────────────────

    fun isCheatPreventionEnabled(): Boolean {
        return prefs.getBoolean(KEY_CHEAT_PREVENTION, false)
    }

    fun setCheatPreventionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CHEAT_PREVENTION, enabled).apply()
    }

    // ─── Internal ────────────────────────────────────

    private fun countKey(packageName: String): String {
        return "count_${packageName}_${LocalDate.now()}"
    }

    private fun cleanOldEntries() {
        val today = LocalDate.now().toString()
        val editor = prefs.edit()
        prefs.all.keys
            .filter {
                (it.startsWith("count_") || it.startsWith("wblock_")) &&
                    !it.endsWith(today)
            }
            .forEach { editor.remove(it) }
        editor.apply()
    }
}
