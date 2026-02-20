package com.focusguard.app

import android.accessibilityservice.AccessibilityService
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class AppMonitorService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var lockRunnable: Runnable? = null
    private var currentMonitoredPackage: String? = null
    private var currentBrowserPackage: String? = null
    private var lastUrlCheckTime = 0L
    private var lastIncognitoCheckTime = 0L
    private var pendingWebsiteBlock: Runnable? = null
    private var pendingBlockSite: String? = null
    private var isClosingBrowser = false

    companion object {
        private const val URL_CHECK_INTERVAL = 1500L
        private const val INCOGNITO_CHECK_INTERVAL = 2000L
        private const val WEBSITE_GRACE_PERIOD = 5_000L
        private const val MAX_TREE_DEPTH = 25
    }

    private val ignoredPackages = setOf(
        "com.focusguard.app",
        "com.android.systemui",
        "com.android.launcher",
        "com.android.launcher3",
        "com.google.android.apps.nexuslauncher",
        "com.sec.android.app.launcher",
        "com.miui.home",
        "com.huawei.android.launcher",
        "com.oppo.launcher",
        "com.oneplus.launcher"
    )

    private val knownBrowsers = setOf(
        "com.android.chrome",
        "com.chrome.beta",
        "com.chrome.dev",
        "com.chrome.canary",
        "org.mozilla.firefox",
        "org.mozilla.firefox_beta",
        "org.mozilla.fenix",
        "com.opera.browser",
        "com.opera.mini.native",
        "com.brave.browser",
        "com.microsoft.emmx",
        "com.sec.android.app.sbrowser",
        "com.UCMobile.intl",
        "com.vivaldi.browser",
        "com.duckduckgo.mobile.android",
        "com.kiwibrowser.browser",
        "com.ecosia.android",
        "org.chromium.chrome"
    )

    private val urlBarPatterns = listOf(
        "url_bar", "url_field", "url_bar_title",
        "location_bar_edit_text", "mozac_browser_toolbar_url_view",
        "address_bar", "addressbar", "omnibox",
        "bro_omnibar_address_title_text", "omnibarTextInput"
    )

    private val incognitoSignals = listOf(
        "incognito", "incognitonewtab", "incognito_",
        "privatebrowsing", "private_browsing", "private_tab",
        "private_mode", "pbmode",
        "inprivate",
        "customtabsincognito"
    )

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val eventType = event?.eventType ?: return
        val packageName = event.packageName?.toString() ?: return
        val className = event.className?.toString() ?: ""

        if (className.contains("Toast") || className.contains("Popup")) return

        when (eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowChanged(packageName, className)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (isClosingBrowser) return
                if (currentBrowserPackage != null &&
                    packageName == currentBrowserPackage
                ) {
                    if (hasBlockedWebsites() && throttledIncognitoCheck()) {
                        closeBrowserAndLock(currentBrowserPackage)
                        return
                    }
                    throttledUrlCheck()
                }
            }
        }
    }

    private fun handleWindowChanged(packageName: String, className: String) {
        if (shouldBlockSettingsPage(packageName, className)) {
            lockScreenAndGoHome()
            return
        }

        if (packageName in knownBrowsers) {
            currentBrowserPackage = packageName
            if (!isClosingBrowser &&
                isIncognitoMode(className) && hasBlockedWebsites()
            ) {
                closeBrowserAndLock(packageName)
                return
            }
            if (checkBrowserUrlAndBlock()) return
        } else {
            currentBrowserPackage = null
        }

        if (packageName in ignoredPackages ||
            packageName.contains("launcher", ignoreCase = true)
        ) {
            cancelTimer()
            return
        }

        val tracker = AppUsageTracker(this)

        if (!tracker.isMonitored(packageName)) {
            cancelTimer()
            return
        }

        if (packageName == currentMonitoredPackage) return

        cancelTimer()
        currentMonitoredPackage = packageName

        val openCount = tracker.incrementOpenCount(packageName)
        val delay = getDelayForOpenCount(openCount)

        val runnable = Runnable {
            lockScreenAndGoHome()
            currentMonitoredPackage = null
        }
        lockRunnable = runnable
        handler.postDelayed(runnable, delay)
    }

    private fun getDelayForOpenCount(count: Int): Long = when (count) {
        1 -> 5_000L
        2 -> 30_000L
        3 -> 120_000L
        else -> 180_000L
    }

    // ─── Incognito Detection ─────────────────────────

    private fun isIncognitoMode(className: String): Boolean {
        val lowerClass = className.lowercase()
        if (incognitoSignals.any { lowerClass.contains(it) }) {
            return true
        }
        return checkWindowForIncognito()
    }

    private fun throttledIncognitoCheck(): Boolean {
        val now = System.currentTimeMillis()
        if (now - lastIncognitoCheckTime < INCOGNITO_CHECK_INTERVAL) {
            return false
        }
        lastIncognitoCheckTime = now
        return checkWindowForIncognito()
    }

    private fun checkWindowForIncognito(): Boolean {
        val root = rootInActiveWindow ?: return false
        return scanForIncognitoHints(root, 0)
    }

    private fun scanForIncognitoHints(
        node: AccessibilityNodeInfo,
        depth: Int
    ): Boolean {
        if (depth > MAX_TREE_DEPTH) return false

        val desc = node.contentDescription?.toString()?.lowercase() ?: ""
        val resId = node.viewIdResourceName?.lowercase() ?: ""

        if (incognitoSignals.any { signal ->
                desc.contains(signal) || resId.contains(signal)
            }
        ) return true

        val text = node.text?.toString()?.lowercase() ?: ""
        if (node.className?.toString()?.contains("TextView") == true &&
            (text == "incognito" || text == "inprivate" ||
                text.contains("private browsing") ||
                text.contains("private tab") ||
                text.contains("incognito mode"))
        ) return true

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            if (scanForIncognitoHints(child, depth + 1)) return true
        }
        return false
    }

    private fun hasBlockedWebsites(): Boolean {
        return AppUsageTracker(this).getMonitoredWebsites().isNotEmpty()
    }

    // ─── Website Blocking ────────────────────────────

    private fun throttledUrlCheck() {
        val now = System.currentTimeMillis()
        if (now - lastUrlCheckTime < URL_CHECK_INTERVAL) return
        lastUrlCheckTime = now
        checkBrowserUrlAndBlock()
    }

    private fun checkBrowserUrlAndBlock(): Boolean {
        val url = extractUrlFromBrowser() ?: return false
        val tracker = AppUsageTracker(this)
        val matchedSite = tracker.findMatchingWebsite(url)

        if (matchedSite != null) {
            if (pendingBlockSite == matchedSite) return true

            cancelPendingWebsiteBlock()
            pendingBlockSite = matchedSite
            val browserPkg = currentBrowserPackage

            val runnable = Runnable {
                val currentUrl = extractUrlFromBrowser()
                if (currentUrl != null && tracker.isMonitoredWebsite(currentUrl)) {
                    tracker.incrementWebsiteBlockCount(matchedSite)
                    closeBrowserAndLock(browserPkg)
                }
                pendingBlockSite = null
                pendingWebsiteBlock = null
            }
            pendingWebsiteBlock = runnable
            handler.postDelayed(runnable, WEBSITE_GRACE_PERIOD)
            return true
        }

        if (pendingBlockSite != null) {
            cancelPendingWebsiteBlock()
        }
        return false
    }

    private fun cancelPendingWebsiteBlock() {
        pendingWebsiteBlock?.let { handler.removeCallbacks(it) }
        pendingWebsiteBlock = null
        pendingBlockSite = null
    }

    private fun extractUrlFromBrowser(): String? {
        val root = rootInActiveWindow ?: return null
        return findUrlInNodeTree(root, 0)
    }

    private fun findUrlInNodeTree(
        node: AccessibilityNodeInfo,
        depth: Int
    ): String? {
        if (depth > MAX_TREE_DEPTH) return null

        val resourceId = node.viewIdResourceName?.lowercase() ?: ""

        if (urlBarPatterns.any { resourceId.contains(it) }) {
            val text = node.text?.toString()
            if (!text.isNullOrBlank()) return text
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val result = findUrlInNodeTree(child, depth + 1)
            if (result != null) return result
        }

        return null
    }

    // ─── Cheating Prevention ─────────────────────────

    private fun shouldBlockSettingsPage(
        packageName: String,
        className: String
    ): Boolean {
        val tracker = AppUsageTracker(this)
        if (!tracker.isCheatPreventionEnabled()) return false

        val isSettings = packageName == "com.android.settings" ||
            packageName.endsWith(".settings")

        if (!isSettings) return false

        val lowerClass = className.lowercase()
        return lowerClass.contains("accessibility") ||
            lowerClass.contains("deviceadmin")
    }

    // ─── Screen Lock ─────────────────────────────────

    private fun cancelTimer() {
        lockRunnable?.let { handler.removeCallbacks(it) }
        lockRunnable = null
        currentMonitoredPackage = null
        isClosingBrowser = false
        cancelPendingWebsiteBlock()
    }

    private fun lockScreenAndGoHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
        handler.postDelayed({ lockScreen() }, 300)
    }

    /**
     * For website blocks: press Back (close tab / navigate away),
     * then go Home, kill the browser process so tabs don't
     * auto-restore, then lock the screen.
     */
    private fun closeBrowserAndLock(browserPackage: String?) {
        if (isClosingBrowser) return
        isClosingBrowser = true

        performGlobalAction(GLOBAL_ACTION_BACK)

        handler.postDelayed({
            performGlobalAction(GLOBAL_ACTION_HOME)

            handler.postDelayed({
                if (browserPackage != null) {
                    try {
                        val am = getSystemService(
                            Context.ACTIVITY_SERVICE
                        ) as ActivityManager
                        am.killBackgroundProcesses(browserPackage)
                    } catch (_: Exception) {
                    }
                }
                handler.postDelayed({
                    lockScreen()
                    isClosingBrowser = false
                }, 200)
            }, 300)
        }, 200)
    }

    private fun lockScreen() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE)
            as DevicePolicyManager
        val admin = ComponentName(this, ScreenLockAdmin::class.java)
        if (dpm.isAdminActive(admin)) {
            dpm.lockNow()
        }
    }

    override fun onInterrupt() {
        cancelTimer()
    }

    override fun onDestroy() {
        super.onDestroy()
        cancelTimer()
    }
}
