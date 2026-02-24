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
    private val tracker by lazy { AppUsageTracker(this) }
    private val overlay by lazy { CountdownOverlay(this) }
    private var lockRunnable: Runnable? = null
    private var currentMonitoredPackage: String? = null
    private var currentBrowserPackage: String? = null
    private var monitorStartTime = 0L
    private var monitorDelay = 0L
    private var lastUrlCheckTime = 0L
    private var lastIncognitoCheckTime = 0L
    private var pendingWebsiteBlock: Runnable? = null
    private var pendingBlockSite: String? = null
    private var websiteBlockStartTime = 0L
    private var pendingBlockBrowserPkg: String? = null
    private var isClosingBrowser = false

    companion object {
        private const val URL_CHECK_INTERVAL = 1500L
        private const val INCOGNITO_CHECK_INTERVAL = 2000L
        private const val WEBSITE_GRACE_PERIOD = 5_000L
        private const val MAX_TREE_DEPTH = 25
    }

    // Transient overlays: notifications, status bar, our own app.
    // These should NOT cancel a running app-lock timer.
    private val transientPackages = setOf(
        "com.focusguard.app",
        "com.android.systemui"
    )

    // Home screens: the user deliberately left the monitored app.
    private val launcherPackages = setOf(
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

        // On every event, check if the monitored app timer has expired.
        // This is the primary enforcement — does not depend on Handler.
        if (currentMonitoredPackage != null && monitorStartTime > 0) {
            val elapsed = System.currentTimeMillis() - monitorStartTime
            if (elapsed >= monitorDelay) {
                FocusGuardLog.w(
                    "Event-driven lock for $currentMonitoredPackage " +
                        "(${elapsed / 1000}s elapsed)"
                )
                enforceMonitorLock()
                return
            }
        }

        when (eventType) {
            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                handleWindowChanged(packageName, className)
            }
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                if (isClosingBrowser) return
                if (currentBrowserPackage != null &&
                    packageName == currentBrowserPackage
                ) {
                    // Event-driven website grace period check.
                    if (checkWebsiteGracePeriodElapsed()) return

                    if (hasBlockedWebsites() && throttledIncognitoCheck()) {
                        closeBrowserAndLock(currentBrowserPackage)
                        return
                    }
                    throttledUrlCheck()
                }
            }
        }
    }

    private fun enforceMonitorLock() {
        lockRunnable?.let { handler.removeCallbacks(it) }
        lockRunnable = null
        val pkg = currentMonitoredPackage
        currentMonitoredPackage = null
        monitorStartTime = 0L
        monitorDelay = 0L
        overlay.hide()
        cancelPendingWebsiteBlock()
        FocusGuardLog.w("Locking screen for $pkg")
        lockScreenAndGoHome()
    }

    private fun handleWindowChanged(packageName: String, className: String) {
        if (shouldBlockSettingsPage(packageName, className)) {
            FocusGuardLog.w("Blocking settings page: $className")
            lockScreenAndGoHome()
            return
        }

        if (packageName in knownBrowsers) {
            currentBrowserPackage = packageName
            if (!isClosingBrowser &&
                isIncognitoMode(className) && hasBlockedWebsites()
            ) {
                FocusGuardLog.w("Incognito detected in $packageName")
                closeBrowserAndLock(packageName)
                return
            }
            if (checkBrowserUrlAndBlock()) return
        } else if (packageName !in transientPackages) {
            currentBrowserPackage = null
        }

        // Transient overlays (systemui, our own app): skip entirely,
        // keep timer and browser tracking alive.
        if (packageName in transientPackages) {
            FocusGuardLog.d("Transient overlay: $packageName, keeping timer")
            return
        }

        // Launcher / home screen: user left the app deliberately.
        if (packageName in launcherPackages ||
            packageName.contains("launcher", ignoreCase = true)
        ) {
            FocusGuardLog.d("Home screen: $packageName, cancelling timer")
            cancelTimer()
            return
        }

        if (!tracker.isMonitored(packageName)) {
            // Non-monitored app (keyboard, calculator, etc.):
            // keep the timer running so opening a monitored app
            // can't be dodged by briefly switching elsewhere.
            FocusGuardLog.d("Non-monitored app: $packageName, timer continues")
            return
        }

        if (packageName == currentMonitoredPackage) return

        cancelTimer()
        currentMonitoredPackage = packageName

        val openCount = tracker.incrementOpenCount(packageName)
        val delay = getDelayForOpenCount(openCount)
        monitorStartTime = System.currentTimeMillis()
        monitorDelay = delay
        FocusGuardLog.w(
            "Monitoring $packageName: open #$openCount, " +
                "lock in ${delay / 1000}s"
        )
        overlay.show(delay)

        // Handler as backup — event-driven check in onAccessibilityEvent
        // is the primary enforcement in case the OS suppresses the Handler.
        val runnable = Runnable {
            FocusGuardLog.w("Handler fired for $packageName, locking")
            currentMonitoredPackage = null
            monitorStartTime = 0L
            monitorDelay = 0L
            overlay.hide()
            lockScreenAndGoHome()
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
        return tracker.getMonitoredWebsites().isNotEmpty()
    }

    // ─── Website Blocking ────────────────────────────

    private fun throttledUrlCheck() {
        val now = System.currentTimeMillis()
        if (now - lastUrlCheckTime < URL_CHECK_INTERVAL) return
        lastUrlCheckTime = now
        checkBrowserUrlAndBlock()
    }

    private fun checkWebsiteGracePeriodElapsed(): Boolean {
        if (pendingBlockSite == null || websiteBlockStartTime == 0L) {
            return false
        }
        val elapsed = System.currentTimeMillis() - websiteBlockStartTime
        if (elapsed < WEBSITE_GRACE_PERIOD) return false

        val site = pendingBlockSite ?: return false
        val browserPkg = pendingBlockBrowserPkg

        val currentUrl = extractUrlFromBrowser()
        if (currentUrl != null && tracker.isMonitoredWebsite(currentUrl)) {
            FocusGuardLog.w(
                "Event-driven website block: $site (${elapsed / 1000}s)"
            )
            tracker.incrementWebsiteBlockCount(site)
            cancelPendingWebsiteBlock()
            closeBrowserAndLock(browserPkg)
            return true
        } else {
            FocusGuardLog.d("User navigated away during grace period")
            cancelPendingWebsiteBlock()
        }
        return false
    }

    private fun checkBrowserUrlAndBlock(): Boolean {
        val url = extractUrlFromBrowser() ?: return false
        val matchedSite = tracker.findMatchingWebsite(url)

        if (matchedSite != null) {
            if (pendingBlockSite == matchedSite) return true

            FocusGuardLog.w("Blocked site: $matchedSite (url: $url)")
            cancelPendingWebsiteBlock()
            pendingBlockSite = matchedSite
            pendingBlockBrowserPkg = currentBrowserPackage
            websiteBlockStartTime = System.currentTimeMillis()

            // Handler as backup for event-driven check above.
            val browserPkg = currentBrowserPackage
            val runnable = Runnable {
                val currentUrl = extractUrlFromBrowser()
                if (currentUrl != null && tracker.isMonitoredWebsite(currentUrl)) {
                    FocusGuardLog.w("Handler website block: $matchedSite")
                    tracker.incrementWebsiteBlockCount(matchedSite)
                    closeBrowserAndLock(browserPkg)
                } else {
                    FocusGuardLog.d("User navigated away, cancelling block")
                }
                pendingBlockSite = null
                pendingWebsiteBlock = null
                websiteBlockStartTime = 0L
                pendingBlockBrowserPkg = null
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
        websiteBlockStartTime = 0L
        pendingBlockBrowserPkg = null
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
        monitorStartTime = 0L
        monitorDelay = 0L
        isClosingBrowser = false
        overlay.hide()
        cancelPendingWebsiteBlock()
    }

    private fun lockScreenAndGoHome() {
        performGlobalAction(GLOBAL_ACTION_HOME)
        lockScreen()
    }

    /**
     * For website blocks: press Back (close tab / navigate away),
     * then go Home, kill the browser process so tabs don't
     * auto-restore, then lock the screen.
     */
    private fun closeBrowserAndLock(browserPackage: String?) {
        if (isClosingBrowser) return
        isClosingBrowser = true
        FocusGuardLog.w("closeBrowserAndLock: $browserPackage")

        performGlobalAction(GLOBAL_ACTION_BACK)

        handler.postDelayed({
            performGlobalAction(GLOBAL_ACTION_HOME)

            if (browserPackage != null) {
                try {
                    val am = getSystemService(
                        Context.ACTIVITY_SERVICE
                    ) as ActivityManager
                    am.killBackgroundProcesses(browserPackage)
                } catch (_: Exception) {
                }
            }

            lockScreen()
            isClosingBrowser = false
        }, 300)
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
        overlay.hide()
        cancelTimer()
    }
}
