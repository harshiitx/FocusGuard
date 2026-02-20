package com.focusguard.app

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.DoNotDisturb
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import com.google.accompanist.drawablepainter.rememberDrawablePainter

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FocusGuardTheme {
                FocusGuardApp()
            }
        }
    }
}

// ─── Top-Level App ───────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusGuardApp() {
    val context = LocalContext.current
    val tracker = remember { AppUsageTracker(context) }
    var selectedTab by remember { mutableStateOf(0) }

    var pendingAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showParagraphEditor by remember { mutableStateOf(false) }
    var challengeParagraph by remember { mutableStateOf("") }

    if (pendingAction != null) {
        if (challengeParagraph.isEmpty()) {
            challengeParagraph = tracker.generateRandomizedChallenge()
        }
        ParagraphChallengeDialog(
            paragraph = challengeParagraph,
            onSuccess = {
                val action = pendingAction
                pendingAction = null
                challengeParagraph = ""
                action?.invoke()
            },
            onDismiss = {
                pendingAction = null
                challengeParagraph = ""
            }
        )
    }

    if (showParagraphEditor) {
        ParagraphEditorDialog(
            currentParagraph = tracker.getChallengeParagraph(),
            onSave = { newParagraph ->
                tracker.setChallengeParagraph(newParagraph)
                showParagraphEditor = false
            },
            onDismiss = { showParagraphEditor = false }
        )
    }

    val requestProtectedAction = { action: () -> Unit ->
        pendingAction = action
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("FocusGuard", fontWeight = FontWeight.Bold)
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                actions = {
                    IconButton(onClick = {
                        pendingAction = {
                            AppUsageTracker(context).resetCounts()
                        }
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Reset counts")
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Filled.Shield, null) },
                    label = { Text("Setup") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Filled.Apps, null) },
                    label = { Text("Apps") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Filled.Language, null) },
                    label = { Text("Web") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Filled.BarChart, null) },
                    label = { Text("Stats") }
                )
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> SetupScreen(
                modifier = Modifier.padding(padding),
                requestProtectedAction = requestProtectedAction,
                onEditParagraph = {
                    pendingAction = { showParagraphEditor = true }
                }
            )
            1 -> AppSelectionScreen(
                modifier = Modifier.padding(padding),
                requestProtectedAction = requestProtectedAction
            )
            2 -> WebsiteScreen(
                modifier = Modifier.padding(padding),
                requestProtectedAction = requestProtectedAction
            )
            3 -> StatsScreen(Modifier.padding(padding))
        }
    }
}

// ─── Setup Screen ────────────────────────────────────────────

@Composable
fun SetupScreen(
    modifier: Modifier = Modifier,
    requestProtectedAction: (() -> Unit) -> Unit,
    onEditParagraph: () -> Unit
) {
    val context = LocalContext.current
    val tracker = remember { AppUsageTracker(context) }
    var refreshKey by remember { mutableStateOf(0) }

    val accessibilityEnabled = remember(refreshKey) {
        isAccessibilityServiceEnabled(context)
    }
    val deviceAdminEnabled = remember(refreshKey) {
        isDeviceAdminEnabled(context)
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Icon(
                        Icons.Filled.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "How FocusGuard Works",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Select apps to monitor and websites to " +
                            "block. Monitored apps get escalating " +
                            "timeouts. Blocked websites give you 5s " +
                            "to close the tab, then lock your screen.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(8.dp))
                    TimerInfoRow("1st open", "5 seconds")
                    TimerInfoRow("2nd open", "30 seconds")
                    TimerInfoRow("3rd open", "2 minutes")
                    TimerInfoRow("4th+ open", "3 minutes")
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Counts reset daily at midnight.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        item {
            Text(
                "Required Permissions",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }

        item {
            PermissionCard(
                title = "Accessibility Service",
                description = "Detects apps and browser URLs in real-time",
                enabled = accessibilityEnabled,
                onClick = {
                    context.startActivity(
                        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    )
                },
                onResume = { refreshKey++ }
            )
        }

        item {
            PermissionCard(
                title = "Device Admin",
                description = "Allows FocusGuard to lock your screen",
                enabled = deviceAdminEnabled,
                onClick = {
                    val intent = Intent(
                        DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN
                    ).apply {
                        putExtra(
                            DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                            ComponentName(context, ScreenLockAdmin::class.java)
                        )
                        putExtra(
                            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                            "FocusGuard needs this permission to " +
                                "lock your screen when timer expires."
                        )
                    }
                    context.startActivity(intent)
                },
                onResume = { refreshKey++ }
            )
        }

        item {
            val allReady = accessibilityEnabled && deviceAdminEnabled
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (allReady)
                        Color(0xFF1B5E20).copy(alpha = 0.15f)
                    else
                        Color(0xFFB71C1C).copy(alpha = 0.15f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (allReady) Icons.Filled.CheckCircle
                        else Icons.Filled.Warning,
                        contentDescription = null,
                        tint = if (allReady) Color(0xFF2E7D32)
                        else Color(0xFFC62828),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        if (allReady)
                            "All set! Go to Apps or Web tab to start."
                        else
                            "Please enable all permissions above.",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // ── Anti-Cheat Section ───────────────────────

        item {
            Text(
                "Anti-Cheat",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }

        item {
            var cheatPrevention by remember {
                mutableStateOf(tracker.isCheatPreventionEnabled())
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Cheating Prevention",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Blocks access to Accessibility and " +
                                "Device Admin settings so you can't " +
                                "disable FocusGuard. Disabling this " +
                                "requires the paragraph challenge.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = cheatPrevention,
                        onCheckedChange = { newValue ->
                            if (newValue) {
                                tracker.setCheatPreventionEnabled(true)
                                cheatPrevention = true
                            } else {
                                requestProtectedAction {
                                    tracker.setCheatPreventionEnabled(false)
                                    cheatPrevention = false
                                }
                            }
                        }
                    )
                }
            }
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Challenge Paragraph",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Protected actions require typing a " +
                            "paragraph. Sentences are shuffled " +
                            "randomly each time to prevent muscle " +
                            "memory. If no custom paragraph is set, " +
                            "one is picked from a pool of five.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme
                                .surfaceVariant
                        )
                    ) {
                        Text(
                            tracker.getChallengeParagraph(),
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(onClick = onEditParagraph) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Customize")
                    }
                }
            }
        }
    }
}

@Composable
fun TimerInfoRow(label: String, duration: String) {
    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            "\u2022",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            "$label \u2192 $duration",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun PermissionCard(
    title: String,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit,
    onResume: () -> Unit
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                onResume()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !enabled) { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            if (enabled) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = "Enabled",
                    tint = Color(0xFF2E7D32),
                    modifier = Modifier.size(28.dp)
                )
            } else {
                FilledTonalButton(onClick = onClick) {
                    Text("Enable")
                }
            }
        }
    }
}

// ─── App Selection Screen ────────────────────────────────────

@Composable
fun AppSelectionScreen(
    modifier: Modifier = Modifier,
    requestProtectedAction: (() -> Unit) -> Unit
) {
    val context = LocalContext.current
    val tracker = remember { AppUsageTracker(context) }
    var searchQuery by remember { mutableStateOf("") }
    var monitoredApps by remember {
        mutableStateOf(tracker.getMonitoredApps())
    }

    val installedApps = remember {
        val pm = context.packageManager
        pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .filter { app ->
                pm.getLaunchIntentForPackage(app.packageName) != null &&
                    app.packageName != context.packageName
            }
            .map { app ->
                InstalledApp(
                    packageName = app.packageName,
                    name = app.loadLabel(pm).toString(),
                    icon = app.loadIcon(pm)
                )
            }
            .sortedBy { it.name.lowercase() }
    }

    val filteredApps = remember(searchQuery, installedApps, monitoredApps) {
        val sorted = installedApps.sortedWith(
            compareByDescending<InstalledApp> {
                it.packageName in monitoredApps
            }.thenBy { it.name.lowercase() }
        )
        if (searchQuery.isBlank()) sorted
        else sorted.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                it.packageName.contains(searchQuery, ignoreCase = true)
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            placeholder = { Text("Search apps...") },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(12.dp)
        )

        Text(
            "${monitoredApps.size} app(s) monitored",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            items(
                items = filteredApps,
                key = { it.packageName }
            ) { app ->
                val isMonitored = app.packageName in monitoredApps
                val bgColor by animateColorAsState(
                    if (isMonitored)
                        MaterialTheme.colorScheme.primaryContainer
                            .copy(alpha = 0.4f)
                    else Color.Transparent,
                    label = "bg"
                )

                ListItem(
                    modifier = Modifier
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(bgColor)
                        .clickable {
                            if (isMonitored) {
                                requestProtectedAction {
                                    val s = monitoredApps.toMutableSet()
                                    s.remove(app.packageName)
                                    monitoredApps = s
                                    tracker.setMonitoredApps(s)
                                }
                            } else {
                                val s = monitoredApps.toMutableSet()
                                s.add(app.packageName)
                                monitoredApps = s
                                tracker.setMonitoredApps(s)
                            }
                        },
                    headlineContent = {
                        Text(
                            app.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = if (isMonitored)
                                FontWeight.SemiBold else FontWeight.Normal
                        )
                    },
                    supportingContent = {
                        Text(
                            app.packageName,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodySmall
                        )
                    },
                    leadingContent = {
                        Image(
                            painter = rememberDrawablePainter(
                                drawable = app.icon
                            ),
                            contentDescription = app.name,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                    },
                    trailingContent = {
                        Checkbox(
                            checked = isMonitored,
                            onCheckedChange = null
                        )
                    }
                )
            }
        }
    }
}

// ─── Website Screen ──────────────────────────────────────────

@Composable
fun WebsiteScreen(
    modifier: Modifier = Modifier,
    requestProtectedAction: (() -> Unit) -> Unit
) {
    val context = LocalContext.current
    val tracker = remember { AppUsageTracker(context) }
    var websiteInput by remember { mutableStateOf("") }
    var monitoredWebsites by remember {
        mutableStateOf(tracker.getMonitoredWebsites())
    }

    Column(modifier = modifier.fillMaxSize()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Icon(
                    Icons.Filled.Language,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Website Blocking",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Enter domains to block (e.g. youtube.com). " +
                        "When detected in any browser, you get 5 " +
                        "seconds to close the tab yourself. If " +
                        "still open, FocusGuard closes the browser " +
                        "and locks your screen.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = websiteInput,
                onValueChange = { websiteInput = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("e.g. youtube.com") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                leadingIcon = {
                    Icon(Icons.Filled.Public, contentDescription = null)
                }
            )
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                onClick = {
                    val site = websiteInput.trim().lowercase()
                        .removePrefix("https://")
                        .removePrefix("http://")
                        .removePrefix("www.")
                        .removeSuffix("/")
                    if (site.isNotBlank() && site.contains(".")) {
                        tracker.addMonitoredWebsite(site)
                        monitoredWebsites = tracker.getMonitoredWebsites()
                        websiteInput = ""
                    }
                },
                enabled = websiteInput.isNotBlank()
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Add")
                Spacer(Modifier.width(4.dp))
                Text("Add")
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            "${monitoredWebsites.size} website(s) blocked",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )

        if (monitoredWebsites.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.Public,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No websites blocked yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 8.dp)
            ) {
                items(
                    items = monitoredWebsites.toList().sorted(),
                    key = { it }
                ) { website ->
                    val blockCount = tracker.getWebsiteBlockCount(website)

                    ListItem(
                        modifier = Modifier
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        headlineContent = {
                            Text(website, fontWeight = FontWeight.Medium)
                        },
                        supportingContent = {
                            Text(
                                "Blocked $blockCount time(s) today",
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        leadingContent = {
                            Icon(
                                Icons.Filled.Block,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(28.dp)
                            )
                        },
                        trailingContent = {
                            IconButton(onClick = {
                                requestProtectedAction {
                                    tracker.removeMonitoredWebsite(website)
                                    monitoredWebsites =
                                        tracker.getMonitoredWebsites()
                                }
                            }) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

// ─── Stats Screen ────────────────────────────────────────────

@Composable
fun StatsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val tracker = remember { AppUsageTracker(context) }
    val pm = context.packageManager
    val monitoredApps = remember { tracker.getMonitoredApps() }
    val monitoredWebsites = remember { tracker.getMonitoredWebsites() }

    val hasContent = monitoredApps.isNotEmpty() ||
        monitoredWebsites.isNotEmpty()

    if (!hasContent) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.DoNotDisturb,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    "Nothing monitored yet",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Go to Apps or Web tab to get started",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        return
    }

    val appStats = remember {
        monitoredApps.mapNotNull { pkgName ->
            try {
                val appInfo = pm.getApplicationInfo(pkgName, 0)
                Triple(
                    appInfo.loadLabel(pm).toString(),
                    appInfo.loadIcon(pm),
                    tracker.getOpenCount(pkgName)
                )
            } catch (_: PackageManager.NameNotFoundException) {
                null
            }
        }.sortedByDescending { it.third }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (appStats.isNotEmpty()) {
            item {
                Text(
                    "App Usage Today",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(appStats.size) { index ->
                val (name, icon, count) = appStats[index]
                val nextTimeout = when (count) {
                    0 -> "5s"
                    1 -> "30s"
                    2 -> "2m"
                    else -> "3m"
                }

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 1.dp
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(
                            painter = rememberDrawablePainter(
                                drawable = icon
                            ),
                            contentDescription = name,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(8.dp))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                name,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Opened $count time(s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme
                                    .onSurfaceVariant
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                nextTimeout,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "next",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme
                                    .onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        if (monitoredWebsites.isNotEmpty()) {
            item {
                Spacer(Modifier.height(8.dp))
                Text(
                    "Website Blocks Today",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(monitoredWebsites.toList().sorted()) { website ->
                val blockCount = tracker.getWebsiteBlockCount(website)

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(
                        defaultElevation = 1.dp
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Block,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                website,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                "Blocked $blockCount time(s)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme
                                    .onSurfaceVariant
                            )
                        }
                        Text(
                            "5s",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
}

// ─── Paragraph Challenge Dialog ──────────────────────────────

@Composable
fun ParagraphChallengeDialog(
    paragraph: String,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    var typedText by remember { mutableStateOf("") }
    var pasteWarning by remember { mutableStateOf(false) }
    val isMatch = typedText == paragraph
    val progress = (typedText.length.toFloat() / paragraph.length.toFloat())
        .coerceIn(0f, 1f)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        "Type to Confirm",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    "Type the following paragraph exactly " +
                        "to proceed (copy-paste is disabled):",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(12.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme
                            .surfaceVariant
                    )
                ) {
                    Text(
                        paragraph,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(Modifier.height(12.dp))

                LinearProgressIndicator(
                    progress = progress,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = if (isMatch) Color(0xFF2E7D32)
                    else MaterialTheme.colorScheme.primary
                )

                Text(
                    "${typedText.length} / ${paragraph.length} characters",
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )

                if (pasteWarning) {
                    Text(
                        "Paste detected \u2014 you must type manually!",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = typedText,
                    onValueChange = { newValue ->
                        val added = newValue.length - typedText.length
                        if (added > 2) {
                            pasteWarning = true
                        } else {
                            pasteWarning = false
                            typedText = newValue
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    placeholder = { Text("Start typing here...") },
                    shape = RoundedCornerShape(12.dp)
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = onSuccess,
                        enabled = isMatch
                    ) {
                        Text("Confirm")
                    }
                }
            }
        }
    }
}

// ─── Paragraph Editor Dialog ─────────────────────────────────

@Composable
fun ParagraphEditorDialog(
    currentParagraph: String,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newParagraph by remember { mutableStateOf(currentParagraph) }
    val minLen = AppUsageTracker.MIN_PARAGRAPH_LENGTH
    val isValid = newParagraph.length >= minLen

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                Text(
                    "Customize Challenge Paragraph",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    "Minimum $minLen characters required. " +
                        "Make it long and unique so it's hard " +
                        "to type impulsively.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = newParagraph,
                    onValueChange = { newParagraph = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                Text(
                    "${newParagraph.length} / $minLen min characters" +
                        if (isValid) " \u2713" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isValid) Color(0xFF2E7D32)
                    else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = { onSave(newParagraph) },
                        enabled = isValid
                    ) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

// ─── Theme ───────────────────────────────────────────────────

@Composable
fun FocusGuardTheme(content: @Composable () -> Unit) {
    val colorScheme = if (Build.VERSION.SDK_INT >= 31) {
        dynamicLightColorScheme(LocalContext.current)
    } else {
        lightColorScheme(
            primary = Color(0xFF1565C0),
            primaryContainer = Color(0xFFD1E4FF),
            onPrimaryContainer = Color(0xFF001D36),
            secondary = Color(0xFF00695C),
            secondaryContainer = Color(0xFFB2DFDB),
            surface = Color(0xFFFFFBFE),
            surfaceVariant = Color(0xFFE7E0EC),
            background = Color(0xFFFFFBFE)
        )
    }
    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}

// ─── Permission Helpers ──────────────────────────────────────

private fun isAccessibilityServiceEnabled(context: Context): Boolean {
    val expectedId = "${context.packageName}/" +
        AppMonitorService::class.java.canonicalName
    val raw = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false
    val splitter = TextUtils.SimpleStringSplitter(':')
    splitter.setString(raw)
    while (splitter.hasNext()) {
        if (splitter.next().equals(expectedId, ignoreCase = true)) {
            return true
        }
    }
    return false
}

private fun isDeviceAdminEnabled(context: Context): Boolean {
    val dpm = context.getSystemService(Context.DEVICE_POLICY_SERVICE)
        as DevicePolicyManager
    return dpm.isAdminActive(
        ComponentName(context, ScreenLockAdmin::class.java)
    )
}
