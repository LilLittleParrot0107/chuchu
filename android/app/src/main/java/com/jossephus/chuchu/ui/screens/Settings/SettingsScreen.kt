package com.jossephus.chuchu.ui.screens.Settings

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.jossephus.chuchu.ui.components.KohiBackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.jossephus.chuchu.service.agents.AgentNotifier
import com.jossephus.chuchu.service.push.KohiFcm
import com.jossephus.chuchu.ui.components.ChuButton
import com.jossephus.chuchu.ui.components.ChuButtonVariant
import com.jossephus.chuchu.ui.components.ChuSwitch
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.components.ChuTextField
import com.jossephus.chuchu.ui.screens.Terminal.TerminalTabMode
import com.jossephus.chuchu.ui.components.KohiTab
import com.jossephus.chuchu.ui.terminal.TerminalCustomKeyGroup
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography
import com.jossephus.chuchu.ui.theme.ThemeMode

enum class SettingsCategory(val label: String) {
    General("general"),
    Terminal("terminal"),
}

@Composable
fun SettingsScreen(
    currentTheme: String,
    currentFont: String,
    appLockEnabled: Boolean,
    requireAuthOnConnect: Boolean,
    localShellEnabled: Boolean,
    keepScreenAwake: Boolean,
    hideScreenContents: Boolean,
    agentAlertsEnabled: Boolean = true,
    currentAccessoryLayoutIds: List<String>,
    accessoryBarSingleRow: Boolean,
    currentTerminalCustomKeyGroups: List<TerminalCustomKeyGroup>,
    showCustomActionsFab: Boolean,
    onShowCustomActionsFabChanged: (Boolean) -> Unit,
    builtinShortcuts: Map<String, String> = emptyMap(),
    onBuiltinShortcutsChanged: (Map<String, String>) -> Unit = {},
    currentTabMode: TerminalTabMode = TerminalTabMode.Classic,
    onTabModeChanged: (TerminalTabMode) -> Unit = {},
    /** Tab mở khi khởi động app (user 4/10) — áp dụng từ lần mở kế tiếp. */
    startTab: KohiTab = KohiTab.HOSTS,
    onStartTabChanged: (KohiTab) -> Unit = {},
    themeMode: ThemeMode,
    lightThemeName: String,
    onThemeSelected: (String) -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit,
    onLightThemeSelected: (String) -> Unit,
    onFontSelected: (String) -> Unit,
    onAppLockEnabledChanged: (Boolean) -> Unit,
    onRequireAuthOnConnectChanged: (Boolean) -> Unit,
    onLocalShellEnabledChanged: (Boolean) -> Unit,
    onKeepScreenAwakeChanged: (Boolean) -> Unit,
    onHideScreenContentsChanged: (Boolean) -> Unit,
    onAgentAlertsEnabledChanged: (Boolean) -> Unit = {},
    onAccessoryLayoutChanged: (List<String>) -> Unit,
    onAccessoryBarSingleRowChanged: (Boolean) -> Unit,
    currentTerminalFontSize: Float = 14f,
    onTerminalFontSizeChanged: (Float) -> Unit = {},
    onTerminalCustomActionsChanged: (List<TerminalCustomKeyGroup>) -> Unit,
    geminiApiKey: String = "",
    onGeminiApiKeyChanged: (String) -> Unit = {},
    backupViewModel: SettingsBackupViewModel? = null,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = ChuColors.current
    val typography = ChuTypography.current
    var selectedCategory by remember { mutableStateOf(SettingsCategory.General) }
    var showAccessoryEditor by remember { mutableStateOf(false) }
    var showCustomActionEditor by remember { mutableStateOf(false) }
    var showChuchuCommandsEditor by remember { mutableStateOf(false) }
    var showBackupSheet by remember { mutableStateOf(false) }

    KohiBackHandler(enabled = true) {
        when {
            showBackupSheet -> {
                backupViewModel?.dismissSheet()
                showBackupSheet = false
            }
            showCustomActionEditor -> showCustomActionEditor = false
            showChuchuCommandsEditor -> showChuchuCommandsEditor = false
            showAccessoryEditor -> showAccessoryEditor = false
            else -> onBack()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(16.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChuText("$ ", style = typography.headline, color = colors.textMuted)
                        ChuText("settings", style = typography.headline)
                    }
                    ChuButton(
                        onClick = onBack,
                        variant = ChuButtonVariant.Ghost,
                        bracketed = true,
                        borderColor = colors.textMuted,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        ChuText("x", style = typography.label, color = colors.textMuted)
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SettingsCategory.entries.forEach { category ->
                        val isSelected = category == selectedCategory
                        ChuButton(
                            onClick = { selectedCategory = category },
                            variant = ChuButtonVariant.Outlined,
                            borderColor = if (isSelected) colors.accent else colors.border,
                            backgroundColor = if (isSelected) colors.accent.copy(alpha = 0.12f) else null,
                            modifier = Modifier.weight(1f),
                        ) {
                            ChuText(
                                category.label,
                                style = typography.label,
                                color = if (isSelected) colors.accent else colors.textSecondary,
                            )
                        }
                    }
                }
            }

            item {
                when (selectedCategory) {
                    SettingsCategory.General -> GeneralSettings(
                        currentTheme = currentTheme,
                        currentFont = currentFont,
                        onThemeSelected = onThemeSelected,
                        onThemeModeChanged = onThemeModeChanged,
                        themeMode = themeMode,
                        lightThemeName = lightThemeName,
                        onLightThemeSelected = onLightThemeSelected,
                        onFontSelected = onFontSelected,
                        startTab = startTab,
                        onStartTabChanged = onStartTabChanged,
                        appLockEnabled = appLockEnabled,
                        requireAuthOnConnect = requireAuthOnConnect,
                        onAppLockEnabledChanged = onAppLockEnabledChanged,
                        onRequireAuthOnConnectChanged = onRequireAuthOnConnectChanged,
                        agentAlertsEnabled = agentAlertsEnabled,
                        onAgentAlertsEnabledChanged = onAgentAlertsEnabledChanged,
                        onOpenBackup = { showBackupSheet = true },
                        geminiApiKey = geminiApiKey,
                        onGeminiApiKeyChanged = onGeminiApiKeyChanged,
                    )
                    SettingsCategory.Terminal -> TerminalSettings(
                        currentAccessoryLayoutIds = currentAccessoryLayoutIds,
                        onEditAccessoryLayout = { showAccessoryEditor = true },
                        accessoryBarSingleRow = accessoryBarSingleRow,
                        onAccessoryBarSingleRowChanged = onAccessoryBarSingleRowChanged,
                        currentTerminalFontSize = currentTerminalFontSize,
                        onTerminalFontSizeChanged = onTerminalFontSizeChanged,
                        currentTerminalCustomKeyGroups = currentTerminalCustomKeyGroups,
                        onEditCustomActions = { showCustomActionEditor = true },
                        showCustomActionsFab = showCustomActionsFab,
                        onShowCustomActionsFabChanged = onShowCustomActionsFabChanged,
                        builtinShortcuts = builtinShortcuts,
                        onEditChuchuCommands = { showChuchuCommandsEditor = true },
                        currentTabMode = currentTabMode,
                        onTabModeChanged = onTabModeChanged,
                        localShellEnabled = localShellEnabled,
                        onLocalShellEnabledChanged = onLocalShellEnabledChanged,
                        keepScreenAwake = keepScreenAwake,
                        onKeepScreenAwakeChanged = onKeepScreenAwakeChanged,
                        hideScreenContents = hideScreenContents,
                        onHideScreenContentsChanged = onHideScreenContentsChanged,
                    )
                }
            }
        }

        AccessoryLayoutEditorSheet(
            visible = showAccessoryEditor,
            selectedIds = currentAccessoryLayoutIds,
            onSave = {
                onAccessoryLayoutChanged(it)
                showAccessoryEditor = false
            },
            onDismiss = { showAccessoryEditor = false },
        )

        TerminalCustomActionsEditorSheet(
            visible = showCustomActionEditor,
            initialGroups = currentTerminalCustomKeyGroups,
            onSave = onTerminalCustomActionsChanged,
            onDismiss = { showCustomActionEditor = false },
        )

        ChuchuCommandsEditorSheet(
            visible = showChuchuCommandsEditor,
            builtinShortcuts = builtinShortcuts,
            onSave = {
                onBuiltinShortcutsChanged(it)
                showChuchuCommandsEditor = false
            },
            onDismiss = { showChuchuCommandsEditor = false },
        )

        if (backupViewModel != null) {
            SshBackupSheet(
                visible = showBackupSheet,
                viewModel = backupViewModel,
                onDismiss = { showBackupSheet = false },
            )
        }
    }
}

@Composable
private fun GeneralSettings(
    currentTheme: String,
    currentFont: String,
    onThemeSelected: (String) -> Unit,
    onThemeModeChanged: (ThemeMode) -> Unit,
    themeMode: ThemeMode,
    lightThemeName: String,
    onLightThemeSelected: (String) -> Unit,
    onFontSelected: (String) -> Unit,
    startTab: KohiTab,
    onStartTabChanged: (KohiTab) -> Unit,
    appLockEnabled: Boolean,
    requireAuthOnConnect: Boolean,
    onAppLockEnabledChanged: (Boolean) -> Unit,
    onRequireAuthOnConnectChanged: (Boolean) -> Unit,
    agentAlertsEnabled: Boolean = true,
    onAgentAlertsEnabledChanged: (Boolean) -> Unit = {},
    onOpenBackup: () -> Unit = {},
    geminiApiKey: String = "",
    onGeminiApiKeyChanged: (String) -> Unit = {},
) {
    val typography = ChuTypography.current
    val colors = ChuColors.current
    // 13/10: kiểm tra miễn tối ưu pin — về lại Settings (từ dialog hệ thống) là đọc lại.
    val context = LocalContext.current
    var batteryExempt by remember { mutableStateOf(isIgnoringBatteryOptimizations(context)) }
    var alarmsOk by remember { mutableStateOf(canScheduleExactAlarms(context)) }
    var bubblesOn by remember { mutableStateOf(AgentNotifier.bubblesAllowed(context)) }
    var pushToken by remember { mutableStateOf(KohiFcm.token(context)) }
    // Android 13+: bật watch mà chưa cấp POST_NOTIFICATIONS thì AgentWatch.start()
    // return im lặng. Xin quyền ngay tại công tắc, và chỉ bật khi được cấp.
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> onAgentAlertsEnabledChanged(granted) }
    val toggleAlerts: (Boolean) -> Unit = { enabled ->
        val needsPermission = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (enabled && needsPermission) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            onAgentAlertsEnabledChanged(enabled)
        }
    }
    LifecycleResumeEffect(Unit) {
        batteryExempt = isIgnoringBatteryOptimizations(context)
        alarmsOk = canScheduleExactAlarms(context)
        bubblesOn = AgentNotifier.bubblesAllowed(context)
        pushToken = KohiFcm.token(context)
        onPauseOrDispose {}
    }
    ThemeSelectorSection(
        darkThemeName = currentTheme,
        onDarkThemeSelected = onThemeSelected,
        lightThemeName = lightThemeName,
        onLightThemeSelected = onLightThemeSelected,
        themeMode = themeMode,
        onThemeModeChanged = onThemeModeChanged,
    )
    Spacer(modifier = Modifier.height(16.dp))
    FontSelectorSection(
        currentFont = currentFont,
        onFontSelected = onFontSelected,
    )
    Spacer(modifier = Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        ChuText("── ", style = typography.labelSmall, color = colors.textMuted)
        ChuText("STARTUP", style = typography.labelSmall, color = colors.textMuted)
        ChuText(" ", style = typography.labelSmall, color = colors.textMuted)
        Box(modifier = Modifier.height(1.dp).background(colors.textMuted).fillMaxWidth())
    }
    Spacer(modifier = Modifier.height(12.dp))
    ChuText("tab on launch", style = typography.label)
    Spacer(modifier = Modifier.height(6.dp))
    StartTabPills(startTab = startTab, onStartTabChanged = onStartTabChanged)
    Spacer(modifier = Modifier.height(4.dp))
    ChuText("applies next time kohi opens.", style = typography.bodySmall, color = colors.textMuted)
    Spacer(modifier = Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        ChuText("── ", style = typography.labelSmall, color = colors.textMuted)
        ChuText("SECURITY", style = typography.labelSmall, color = colors.textMuted)
        ChuText(" ", style = typography.labelSmall, color = colors.textMuted)
        Box(modifier = Modifier.height(1.dp).background(colors.textMuted).fillMaxWidth())
    }
    Spacer(modifier = Modifier.height(12.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChuText("lock app on open", style = typography.label)
        ChuSwitch(checked = appLockEnabled, onCheckedChange = onAppLockEnabledChanged)
    }
    Spacer(modifier = Modifier.height(20.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChuText("verify before server connect", style = typography.label)
        ChuSwitch(checked = requireAuthOnConnect, onCheckedChange = onRequireAuthOnConnectChanged)
    }
    Spacer(modifier = Modifier.height(4.dp))
    ChuText("use biometrics or device PIN/pattern.", style = typography.bodySmall, color = colors.textMuted)
    Spacer(modifier = Modifier.height(16.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChuText("ssh hosts & keys backup", style = typography.label)
        ChuButton(
            onClick = onOpenBackup,
            variant = ChuButtonVariant.Outlined,
            bracketed = true,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        ) {
            ChuText("manage", style = typography.label)
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
    ChuText("encrypted export/import.", style = typography.bodySmall, color = colors.textMuted)
    Spacer(modifier = Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        ChuText("── ", style = typography.labelSmall, color = colors.textMuted)
        ChuText("AGENT ALERTS", style = typography.labelSmall, color = colors.textMuted)
        ChuText(" ", style = typography.labelSmall, color = colors.textMuted)
        Box(modifier = Modifier.height(1.dp).background(colors.textMuted).fillMaxWidth())
    }
    Spacer(modifier = Modifier.height(12.dp))
    // 13/10: user chốt switch — tắt = hành vi app cũ (không trực nền, không thông báo).
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ChuText("watch in background", style = typography.label)
        ChuSwitch(checked = agentAlertsEnabled, onCheckedChange = toggleAlerts)
    }
    Spacer(modifier = Modifier.height(4.dp))
    ChuText(
        "needs-you & finished bubbles while kohi is not on screen.",
        style = typography.bodySmall,
        color = colors.textMuted,
    )
    if (agentAlertsEnabled && !NotificationManagerCompat.from(context).areNotificationsEnabled()) {
        Spacer(modifier = Modifier.height(6.dp))
        ChuText(
            "notifications are blocked — tap to open system settings",
            style = typography.bodySmall,
            color = colors.warning,
            modifier = Modifier.clickable { openNotificationSettings(context) },
        )
    }
    // FCM wakeup: notification/bubble vẫn do kohi tự dựng.
    if (agentAlertsEnabled) {
        Spacer(modifier = Modifier.height(8.dp))
        ChuText("push · FCM", style = typography.label)
        ChuText(
            if (pushToken != null) "registered via Google Play Services"
            else "waiting for FCM token",
            style = typography.bodySmall,
            color = if (pushToken != null) colors.success else colors.textMuted,
        )
    }
    // 13/10 (user: "thông báo đã xong chỉ đến khi vào app"): OriginOS/doze bóp mạng nền khi
    // ngủ sâu → vòng trực 25s treo, alert nổ trễ lúc mở app. Miễn tối ưu pin = app được dùng
    // mạng trong doze → thông báo nổ đúng lúc agent xong, không phải lúc cầm máy lên.
    if (agentAlertsEnabled && !batteryExempt) {
        Spacer(modifier = Modifier.height(8.dp))
        ChuButton(
            onClick = { requestIgnoreBatteryOptimizations(context) },
            variant = ChuButtonVariant.Outlined,
            bracketed = true,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        ) {
            ChuText("allow background", style = typography.label)
        }
        Spacer(modifier = Modifier.height(4.dp))
        ChuText(
            "without this, OriginOS can hold alerts back until you open the app.",
            style = typography.bodySmall,
            color = colors.textMuted,
        )
    }
    // 13/10: nhịp trực nền bằng ALARM — chưa cấp quyền exact thì máy idle có thể trễ vài
    // phút; nút này mở màn cấp quyền "Alarms & reminders" của hệ thống.
    if (agentAlertsEnabled && !alarmsOk) {
        Spacer(modifier = Modifier.height(8.dp))
        ChuButton(
            onClick = { requestExactAlarms(context) },
            variant = ChuButtonVariant.Outlined,
            bracketed = true,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
        ) {
            ChuText("allow alarms", style = typography.label)
        }
        Spacer(modifier = Modifier.height(4.dp))
        ChuText(
            "keeps the background check on time while the phone sleeps.",
            style = typography.bodySmall,
            color = colors.textMuted,
        )
    }
    // 14/10 (user: "bubble vẫn chưa được — tạo switch allow bubble như Messenger"):
    // quyền bong bóng là cài đặt CỦA HỆ THỐNG, app không tự bật được (Messenger cũng vậy)
    // — switch này hiện trạng thái thật (channel.canBubble) và mở màn Bubbles của hệ thống.
    if (agentAlertsEnabled) {
        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                ChuText("bubbles", style = typography.label)
                ChuText(
                    if (bubblesOn) "on — alerts pop as bubbles"
                    else "off — opens Android's bubble settings for kohi",
                    style = typography.bodySmall,
                    color = colors.textMuted,
                )
            }
            ChuSwitch(checked = bubblesOn, onCheckedChange = { openBubbleSettings(context) })
        }
        Spacer(modifier = Modifier.height(6.dp))
        ChuButton(
            onClick = { AgentNotifier.postTestAlert(context) },
            variant = ChuButtonVariant.Ghost,
            bracketed = true,
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        ) {
            ChuText("send test bubble", style = typography.label, color = colors.textMuted)
        }
    }
    Spacer(modifier = Modifier.height(16.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        ChuText("── ", style = typography.labelSmall, color = colors.textMuted)
        ChuText("GEMINI", style = typography.labelSmall, color = colors.textMuted)
        ChuText(" ", style = typography.labelSmall, color = colors.textMuted)
        Box(modifier = Modifier.height(1.dp).background(colors.textMuted).fillMaxWidth())
    }
    Spacer(modifier = Modifier.height(12.dp))
    // 27/9: nút DỊCH của buzz gọi Gemini bằng khoá này — dán khoá miễn phí từ
    // aistudio.google.com (không phải gói Gemini Advanced). autoFocus=false để
    // mở Settings không bật bàn phím.
    ChuTextField(
        value = geminiApiKey,
        onValueChange = onGeminiApiKeyChanged,
        label = "api key",
        placeholder = "AIza…",
        singleLine = true,
        autoFocus = false,
        supportingText = "dịch buzz trong dashboard · aistudio.google.com",
    )
}

/**
 * Chọn tab mở khi khởi động (user 4/10: xài QUEUE nhiều hơn HOSTS) — cùng grammar
 * pill với ThemeModePills; đổi có hiệu lực từ lần mở app kế tiếp.
 */
@Composable
private fun StartTabPills(
    startTab: KohiTab,
    onStartTabChanged: (KohiTab) -> Unit,
) {
    val colors = ChuColors.current
    val typography = ChuTypography.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(colors.surface),
    ) {
        KohiTab.entries.forEach { tab ->
            val isSelected = tab == startTab
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(color = if (isSelected) colors.accent else Color.Transparent)
                    .clickable { onStartTabChanged(tab) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                ChuText(
                    tab.contentDescription.lowercase(),
                    style = typography.label,
                    color = if (isSelected) colors.background else colors.textSecondary,
                )
            }
        }
    }
}

/**
 * Miễn tối ưu pin (battery optimization exemption): điều kiện để app còn được dùng mạng
 * khi máy ngủ sâu (doze) — không có nó, vòng trực nền bị treo và alert nổ trễ lúc mở app.
 */
private fun isIgnoringBatteryOptimizations(context: Context): Boolean {
    val pm = context.getSystemService(PowerManager::class.java) ?: return true
    return pm.isIgnoringBatteryOptimizations(context.packageName)
}

/** Mở màn notification settings của app khi user chặn quyền/thông báo. */
private fun openNotificationSettings(context: Context) {
    val intent = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
    runCatching { context.startActivity(intent) }
}

/** Mở dialog hệ thống xin miễn tối ưu pin cho kohi (app sideload, không qua Play). */
private fun requestIgnoreBatteryOptimizations(context: Context) {
    val intent = Intent(
        AndroidSettings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
        Uri.parse("package:${context.packageName}"),
    )
    runCatching { context.startActivity(intent) }
}

/** Exact-alarm (API 31+): nhịp trực nền đúng giờ khi máy idle; <31 luôn sẵn. */
private fun canScheduleExactAlarms(context: Context): Boolean {
    if (android.os.Build.VERSION.SDK_INT < 31) return true
    val am = context.getSystemService(android.app.AlarmManager::class.java) ?: return true
    return am.canScheduleExactAlarms()
}

/** Mở màn "Alarms & reminders" của hệ thống để cấp quyền exact-alarm cho kohi. */
private fun requestExactAlarms(context: Context) {
    if (android.os.Build.VERSION.SDK_INT < 31) return
    val intent = Intent(
        AndroidSettings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
        Uri.parse("package:${context.packageName}"),
    )
    runCatching { context.startActivity(intent) }
}

/**
 * Mở màn "Bubbles" của kohi trong cài đặt HỆ THỐNG (cùng cách Messenger làm — app không
 * tự bật được bubble, chỉ mở được màn cài đặt). ROM không có màn riêng thì lùi về màn
 * notification settings của app.
 */
private fun openBubbleSettings(context: Context) {
    if (android.os.Build.VERSION.SDK_INT >= 29) {
        val bubble = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_BUBBLE_SETTINGS)
            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
        if (runCatching { context.startActivity(bubble) }.isSuccess) return
    }
    val fallback = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, context.packageName)
    runCatching { context.startActivity(fallback) }
}
