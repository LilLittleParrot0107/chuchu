package com.jossephus.chuchu.ui.screens.Terminal

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imeAnimationTarget
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jossephus.chuchu.data.repository.SettingsRepository
import com.jossephus.chuchu.model.AuthMethod
import com.jossephus.chuchu.model.Transport
import com.jossephus.chuchu.service.terminal.SessionStatus
import com.jossephus.chuchu.service.terminal.TabSpec
import com.jossephus.chuchu.ui.components.ChuButton
import com.jossephus.chuchu.ui.components.ChuButtonVariant
import com.jossephus.chuchu.ui.components.ChuDialog
import com.jossephus.chuchu.ui.components.ChuText
import com.jossephus.chuchu.ui.components.ChuTextField
import com.jossephus.chuchu.ui.screens.Files.ConnectionTab
import com.jossephus.chuchu.ui.screens.Files.FileBrowserScreen
import com.jossephus.chuchu.ui.screens.Files.UploadProgress
import com.jossephus.chuchu.ui.screens.Files.formatFileSize
import com.jossephus.chuchu.ui.screens.Terminal.TerminalTabMode
import com.jossephus.chuchu.ui.terminal.AccessoryAction
import com.jossephus.chuchu.ui.terminal.BuiltinCommand
import com.jossephus.chuchu.ui.terminal.ChuchuKeyBindings
import com.jossephus.chuchu.ui.terminal.CustomActionModifier
import com.jossephus.chuchu.ui.terminal.GhosttyKey
import com.jossephus.chuchu.ui.terminal.GhosttyKeyAction
import com.jossephus.chuchu.ui.terminal.KeyboardAccessoryBar
import com.jossephus.chuchu.ui.terminal.ModifierState
import com.jossephus.chuchu.ui.terminal.TerminalAccessoryDispatcher
import com.jossephus.chuchu.ui.terminal.TerminalAccessoryLayoutStore
import com.jossephus.chuchu.ui.terminal.TerminalCanvas
import com.jossephus.chuchu.ui.terminal.TerminalCustomAction
import com.jossephus.chuchu.ui.terminal.TerminalCustomKeyGroup
import com.jossephus.chuchu.ui.terminal.TerminalInputView
import com.jossephus.chuchu.ui.terminal.TerminalSelection
import com.jossephus.chuchu.ui.terminal.TerminalSelectionHandle
import com.jossephus.chuchu.ui.terminal.TerminalSelectionState
import com.jossephus.chuchu.ui.terminal.TerminalSpecialKey
import com.jossephus.chuchu.ui.terminal.decodeCustomActionValue
import com.jossephus.chuchu.ui.terminal.modifierStateForCustomAction
import com.jossephus.chuchu.ui.terminal.toGhosttyKey
import com.jossephus.chuchu.ui.theme.ChuColors
import com.jossephus.chuchu.ui.theme.ChuTypography
import com.jossephus.chuchu.ui.theme.GhosttyThemeRegistry
import com.jossephus.chuchu.ui.theme.resolveActiveThemeName
import com.jossephus.chuchu.ui.theme.toRgbIntArray
import com.jossephus.chuchu.ui.theme.toTerminalPaletteBytes
import com.jossephus.chuchu.ui.security.requireUserVerification
import com.jossephus.chuchu.ui.security.VerificationResult
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun TerminalViewModel.dispatchTextWithModifierState(
    text: String,
    modifierState: ModifierState,
) {
    val mods = modifierState.terminalMods()
    for (char in text) {
        val ghosttyKey =
            when (char) {
                '\r',
                '\n' -> GhosttyKey.enter
                '\t' -> GhosttyKey.tab
                else -> char.toGhosttyKey()
            }
        if (ghosttyKey != null) {
            val codepoint =
                when (char) {
                    '\r',
                    '\n',
                    '\t' -> 0
                    else -> char.code
                }
            onHardwareKey(ghosttyKey, codepoint, mods, GhosttyKeyAction.Press)
            onHardwareKey(ghosttyKey, codepoint, mods, GhosttyKeyAction.Release)
        } else {
            onTextInput(modifierState.applyToText(char.toString()))
        }
    }
}

@Composable
private fun TerminalCustomActionsFab(
    groups: List<TerminalCustomKeyGroup>,
    onActionClick: (TerminalCustomAction) -> Unit,
    modifier: Modifier = Modifier,
    filteredActions: List<TerminalCustomAction>? = null,
    onClearFilter: () -> Unit = {},
) {
    val colors = ChuColors.current
    val typography = ChuTypography.current
    var expanded by remember { mutableStateOf(filteredActions != null) }
    var selectedGroupKey by remember { mutableStateOf<String?>(null) }
    val selectedGroup =
        remember(selectedGroupKey, groups) {
            groups.firstOrNull { it.keyLabel == selectedGroupKey }
        }

    LaunchedEffect(filteredActions) {
        if (filteredActions != null) {
            expanded = true
            selectedGroupKey = null
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (filteredActions != null) {
                    filteredActions.forEach { action ->
                        ChuButton(
                            onClick = {
                                onActionClick(action)
                                expanded = false
                                onClearFilter()
                            },
                            variant = ChuButtonVariant.Outlined,
                            bracketed = true,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            ChuText(action.label, style = typography.label)
                        }
                    }
                    ChuButton(
                        onClick = {
                            expanded = false
                            onClearFilter()
                        },
                        variant = ChuButtonVariant.Ghost,
                        bracketed = true,
                        borderColor = colors.textMuted,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        ChuText("<", style = typography.label, color = colors.textMuted)
                    }
                } else if (selectedGroup == null) {
                    groups.forEach { group ->
                        ChuButton(
                            onClick = {
                                if (group.actions.size == 1) {
                                    onActionClick(group.actions.first())
                                    expanded = false
                                    selectedGroupKey = null
                                } else {
                                    selectedGroupKey = group.keyLabel
                                }
                            },
                            variant = ChuButtonVariant.Outlined,
                            bracketed = true,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            ChuText(group.keyLabel, style = typography.label)
                        }
                    }
                } else {
                    selectedGroup.actions.forEach { action ->
                        ChuButton(
                            onClick = {
                                onActionClick(action)
                                expanded = false
                                selectedGroupKey = null
                            },
                            variant = ChuButtonVariant.Outlined,
                            bracketed = true,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            ChuText(action.label, style = typography.label)
                        }
                    }
                    ChuButton(
                        onClick = { selectedGroupKey = null },
                        variant = ChuButtonVariant.Ghost,
                        bracketed = true,
                        borderColor = colors.textMuted,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        ChuText("<", style = typography.label, color = colors.textMuted)
                    }
                }
            }
        }

        // TUI-style action toggle — square bracketed button instead of a
        // circular Material FAB. Reads like a macro/quick-key trigger.
        ChuButton(
            onClick = {
                expanded = !expanded
                if (!expanded) {
                    selectedGroupKey = null
                    onClearFilter()
                }
            },
            variant = if (expanded) ChuButtonVariant.Ghost else ChuButtonVariant.Filled,
            bracketed = true,
            borderColor = if (expanded) colors.textMuted else null,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        ) {
            ChuText(
                if (expanded) "x" else "+",
                style = typography.label,
                color = if (expanded) colors.textMuted else colors.onAccent,
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TerminalScreen(
    vm: TerminalViewModel,
    hostId: Long?,
    onOpenSettings: () -> Unit,
    onOpenWeb: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    openLocalShell: Boolean = false,
) {
    val sessionState by vm.sessionState.collectAsStateWithLifecycle()
    val tabs by vm.tabs.collectAsStateWithLifecycle()
    val activeTabId by vm.activeTabId.collectAsStateWithLifecycle()
    val activeTab by vm.activeTab.collectAsStateWithLifecycle()
    val activeTabForHost =
        remember(activeTab, hostId, openLocalShell) {
            if (openLocalShell) {
                activeTab?.takeIf {
                    it.spec.hostId == null && it.spec.transport == Transport.LocalShell
                }
            } else {
                activeTab?.takeIf { it.spec.hostId == hostId }
            }
        }
    val selectedTab by vm.selectedTab.collectAsStateWithLifecycle()
    val filesSupported = activeTab?.spec?.transport != Transport.LocalShell
    val fileBrowserState by vm.fileBrowserState.collectAsStateWithLifecycle()
    val hostKeyPrompt by vm.hostKeyPrompt.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val colors = ChuColors.current
    val typography = ChuTypography.current
    val screenInsetsModifier = modifier.windowInsetsPadding(WindowInsets.safeDrawing)
    var lastSessionStatus by remember { mutableStateOf<SessionStatus?>(null) }
    val settingsRepo = remember(context) { SettingsRepository.getInstance(context) }
    val currentTheme by settingsRepo.themeName.collectAsStateWithLifecycle()
    val themeMode by settingsRepo.themeMode.collectAsStateWithLifecycle()
    val lightThemeName by settingsRepo.lightThemeName.collectAsStateWithLifecycle()
    val resolvedThemeName = resolveActiveThemeName(
        themeMode = themeMode,
        darkThemeName = currentTheme,
        lightThemeName = lightThemeName,
    )
    val tabMode by settingsRepo.terminalTabMode.collectAsStateWithLifecycle()
    val currentAccessoryLayoutIds by settingsRepo.accessoryLayoutIds.collectAsStateWithLifecycle()
    val useSingleRowAccessoryBar by settingsRepo.accessoryBarSingleRow.collectAsStateWithLifecycle()
    val currentTerminalCustomKeyGroups by
        settingsRepo.terminalCustomKeyGroups.collectAsStateWithLifecycle()
    val settingsFontSize by settingsRepo.terminalFontSize.collectAsStateWithLifecycle()
    val keepScreenAwake by settingsRepo.keepScreenAwake.collectAsStateWithLifecycle()
    val keepAwakeView = LocalView.current
    DisposableEffect(keepScreenAwake, keepAwakeView) {
        val window = (keepAwakeView.context as? Activity)?.window
        if (keepScreenAwake) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val accessoryLayout =
        remember(currentAccessoryLayoutIds) {
            TerminalAccessoryLayoutStore.resolveSelectedLayout(currentAccessoryLayoutIds)
        }
    val ghosttyTheme =
        remember(context, resolvedThemeName) { GhosttyThemeRegistry.getTheme(context, resolvedThemeName) }
    val isDarkTheme = (ghosttyTheme?.background ?: colors.background).luminance() < 0.5f
    var selection by remember { mutableStateOf<TerminalSelection?>(null) }
    var selectionState by remember { mutableStateOf<TerminalSelectionState?>(null) }
    var showPassphrasePrompt by remember { mutableStateOf(false) }
    var passphraseInput by remember { mutableStateOf("") }
    var pendingTabSpec by remember { mutableStateOf<TabSpec?>(null) }
    var passphraseFromPicker by remember { mutableStateOf(false) }
    var showTabSheet by remember { mutableStateOf(false) }
    // Compose-box: type Vietnamese in a real text field, ship it to the
    // terminal in one paste. Sidesteps terminal-IME composition entirely.
    var showComposeBox by remember { mutableStateOf(false) }
    var composeBoxText by remember { mutableStateOf("") }


    // Predictive PTY resize: the layout keeps the smooth imePadding slide,
    // but the moment the IME animation's DESTINATION changes we compute the
    // final viewport from the keyboard-hidden baseline and resize the PTY
    // right away — the remote repaint overlaps the slide instead of waiting
    // for it. [cols, rows, cellW, cellH, widthPx, heightPx]; heightPx == 0
    // means no baseline captured yet.
    val fullCanvasArgs = remember { IntArray(6) }
    val imeVisibleNow = WindowInsets.isImeVisible
    val imeTargetBottomPx = WindowInsets.imeAnimationTarget.getBottom(density)
    LaunchedEffect(imeTargetBottomPx) {
        val ch = fullCanvasArgs[3]
        if (ch <= 0 || fullCanvasArgs[5] <= 0) return@LaunchedEffect
        val predictedH = fullCanvasArgs[5] - imeTargetBottomPx
        if (predictedH <= 0) return@LaunchedEffect
        // DISMISS returns to exactly the keyboard-hidden baseline, so reuse
        // its recorded row count. Deriving it from predictedH / ch was off by
        // one whenever the true cell height wasn't a whole px (the canvas
        // divides by the fractional height, ch here is ceiled) — the early
        // resize then sent the wrong grid and the settle resize corrected it:
        // a one-row jump-and-back right after the keyboard finished hiding.
        val predictedRows =
            if (imeTargetBottomPx == 0) fullCanvasArgs[1]
            else maxOf(1, predictedH / ch)
        vm.onPredictedViewport(
            fullCanvasArgs[0],
            predictedRows,
            fullCanvasArgs[2],
            ch,
            fullCanvasArgs[4],
            predictedH,
            // Early resize only on DISMISS (grow): the remote repaint then
            // overlaps the slide. On OPEN it caused a visible flash.
            resizeNow = imeTargetBottomPx == 0,
        )
    }
    var showGlobalTabManager by remember { mutableStateOf(false) }
    var hasSeenTabsForHost by remember(hostId, openLocalShell) { mutableStateOf(false) }
    var focusedTabIndex by remember { mutableStateOf(0) }
    var localShellFilesMessage by remember { mutableStateOf<String?>(null) }
    var terminalFontSizeSp by remember {
        mutableStateOf(
            settingsFontSize.coerceIn(
                SettingsRepository.MIN_TERMINAL_FONT_SIZE,
                SettingsRepository.MAX_TERMINAL_FONT_SIZE,
            ),
        )
    }
    LaunchedEffect(settingsFontSize) {
        terminalFontSizeSp =
            settingsFontSize.coerceIn(
                SettingsRepository.MIN_TERMINAL_FONT_SIZE,
                SettingsRepository.MAX_TERMINAL_FONT_SIZE,
            )
    }
    val showCustomActionsFab by settingsRepo.showCustomActionsFab.collectAsStateWithLifecycle()
    val builtinShortcuts by settingsRepo.builtinShortcuts.collectAsStateWithLifecycle()
    var fabFilteredActions by remember { mutableStateOf<List<TerminalCustomAction>?>(null) }
    val chuchuKeys =
        remember(vm, tabMode, currentTerminalCustomKeyGroups, builtinShortcuts, showCustomActionsFab) {
            val isStrip = tabMode == TerminalTabMode.Strip
            val builtinCommandHandlers: Map<BuiltinCommand, () -> Unit> = mapOf(
                BuiltinCommand.Tabs to {
                    if (isStrip) {
                        showGlobalTabManager = true
                    } else {
                        showTabSheet = true
                    }
                },
                BuiltinCommand.NewTab to {
                    vm.duplicateActiveTab()
                    vm.selectConnectionTab(ConnectionTab.Terminal)
                    showTabSheet = false
                },
                BuiltinCommand.Close to {
                    val activeId = vm.activeTabId.value
                    if (activeId != null) vm.closeTab(activeId)
                },
                BuiltinCommand.Actions to { settingsRepo.setShowCustomActionsFab(!showCustomActionsFab) },
                BuiltinCommand.Settings to { onOpenSettings() },
                BuiltinCommand.Web to { onOpenWeb() },
            )
            ChuchuKeyBindings.build(
                builtinShortcuts = builtinShortcuts,
                builtinCommandHandlers = builtinCommandHandlers,
                customGroups = currentTerminalCustomKeyGroups,
                onDispatchAction = { action ->
                    val decoded = decodeCustomActionValue(action.payload)
                    val unescaped = com.jossephus.chuchu.ui.terminal
                        .unescapeCustomActionText(decoded.text)
                    val rawText = unescaped +
                        if (CustomActionModifier.Enter in decoded.modifiers) "\n" else ""
                    if (unescaped.any { it.code < 0x20 || it.code == 0x7F }) {
                        // Escaped control bytes (\cb, \e, \xNN): ship the whole
                        // sequence raw so prefix combos like Ctrl+B,A survive.
                        vm.onTextInput(rawText.replace('\n', '\r'))
                    } else {
                        val actionModifierState = modifierStateForCustomAction(decoded.modifiers)
                        vm.dispatchTextWithModifierState(rawText, actionModifierState)
                    }
                },
                onSelectAmongActions = { actions -> fabFilteredActions = actions },
            )
        }
    val multiplexerState by vm.multiplexerState.collectAsStateWithLifecycle()

    LaunchedEffect(terminalFontSizeSp) {
        settingsRepo.setTerminalFontSize(terminalFontSizeSp)
    }

    LaunchedEffect(openLocalShell) {
        if (!openLocalShell) return@LaunchedEffect
        showPassphrasePrompt = false
        passphraseInput = ""
        pendingTabSpec = null
        passphraseFromPicker = false
        val existing = vm.selectLocalShellTab()
        if (existing != null) {
            return@LaunchedEffect
        }
        vm.openTab(
            TabSpec(
                hostId = null,
                displayName = "local shell",
                host = "localhost",
                username = "android",
                authMethod = AuthMethod.None,
                transport = Transport.LocalShell,
            )
        )
    }


    val hasTabsForHost =
        remember(tabs, hostId, openLocalShell) {
            when {
                openLocalShell ->
                    tabs.any { it.spec.hostId == null && it.spec.transport == Transport.LocalShell }
                hostId != null -> tabs.any { it.spec.hostId == hostId }
                else -> false
            }
        }
    val tabsForHost =
        remember(tabs, hostId, openLocalShell) {
            when {
                openLocalShell ->
                    tabs.filter { it.spec.hostId == null && it.spec.transport == Transport.LocalShell }
                hostId != null -> tabs.filter { it.spec.hostId == hostId }
                else -> emptyList()
            }
        }
    val activeHostCount =
        remember(tabs) {
            tabs.map { it.spec.hostId ?: it.spec.sessionKey }
                .distinct()
                .size
        }
    val currentHostName = activeTab?.spec?.displayName?.takeIf { it.isNotBlank() }
        ?: activeTab?.spec?.host?.takeIf { it.isNotBlank() }
    val pickerScope = rememberCoroutineScope()

    val openPreparedTab: (TabSpec, Boolean, Boolean) -> Unit = { spec, requiresVerification, fromPicker ->
        val openOrPrompt: (TabSpec) -> Unit = { preparedSpec ->
            if (
                preparedSpec.authMethod == AuthMethod.KeyWithPassphrase &&
                    preparedSpec.keyPassphrase.isBlank()
            ) {
                passphraseFromPicker = fromPicker
                pendingTabSpec = preparedSpec
                showPassphrasePrompt = true
            } else if (preparedSpec.usesRuntimeMultiplexer) {
                vm.initiateMultiplexerOpen(preparedSpec)
            } else {
                vm.openTab(preparedSpec)
            }
        }

        if (requiresVerification) {
            requireUserVerification(
                context = context,
                title = "Verify to connect",
                subtitle = "Authenticate to open this server session",
            ) { result ->
                if (
                    result == VerificationResult.Success &&
                        lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                ) {
                    openOrPrompt(spec)
                }
            }
        } else {
            openOrPrompt(spec)
        }
    }

    val openAnotherSessionForCurrentHost: () -> Unit = {
        val currentHostId = activeTab?.spec?.hostId ?: hostId
        when {
            activeTab != null -> {
                vm.duplicateActiveTab()
                vm.selectConnectionTab(ConnectionTab.Terminal)
            }

            currentHostId != null -> {
                pickerScope.launch(Dispatchers.IO) {
                    val prepared = vm.prepareTabOpenForHost(currentHostId) ?: return@launch
                    withContext(Dispatchers.Main) {
                        openPreparedTab(prepared.spec, prepared.requiresVerification, false)
                    }
                }
            }

        }
    }

    LaunchedEffect(hostId, openLocalShell) {
        showPassphrasePrompt = false
        passphraseInput = ""
        pendingTabSpec = null
        passphraseFromPicker = false
        if (hostId == null || openLocalShell) return@LaunchedEffect
        val existing = vm.selectTabForHost(hostId)
        if (existing != null) {
            return@LaunchedEffect
        }
        val prepared = vm.prepareTabOpenForHost(hostId) ?: return@LaunchedEffect
        vm.refreshTailscaleStatus()
        // KHONG duoc bo qua xac thuc. Gia dinh cu ("da xac thuc o
        // ApplicationNavController roi") SAI voi duong deep link: mot app khac,
        // hay mot trang web bat ky qua `intent://open?host=...#Intent;scheme=kohi;end`,
        // dieu huong thang toi day. Bo qua nghia la ho mo duoc SSH bang mat khau
        // da luu VA chay postConnectCommand tren may remote, ke ca khi host do
        // da bat "doi xac thuc truoc khi ket noi".
        openPreparedTab(prepared.spec, prepared.requiresVerification, false)
    }

    // Strip mode: never auto-back from normal host-scoped empty state.
    // Local-shell routes still close when their local tab is gone.
    LaunchedEffect(hostId, hasTabsForHost, openLocalShell, tabMode) {
        if (openLocalShell) {
            if (hasTabsForHost) {
                hasSeenTabsForHost = true
            } else if (hasSeenTabsForHost) {
                onBack()
            }
            return@LaunchedEffect
        }
        if (tabMode == TerminalTabMode.Strip) return@LaunchedEffect
        if (hostId == null) return@LaunchedEffect
        if (hasTabsForHost) {
            hasSeenTabsForHost = true
        } else if (hasSeenTabsForHost) {
            onBack()
        }
    }

    LaunchedEffect(showTabSheet, tabsForHost, activeTabId) {
        if (!showTabSheet || tabsForHost.isEmpty()) return@LaunchedEffect
        val activeIndex = tabsForHost.indexOfFirst { it.id == activeTabId }
        focusedTabIndex =
            if (activeIndex >= 0) activeIndex else focusedTabIndex.coerceIn(0, tabsForHost.lastIndex)
    }

    LaunchedEffect(showTabSheet, activeTab?.id) {
        if (showTabSheet && activeTab?.spec?.usesRuntimeMultiplexer == true) {
            vm.listMultiplexerSessionsForCurrentHost()
        }
    }

    LaunchedEffect(showGlobalTabManager, activeTab?.id) {
        if (showGlobalTabManager && activeTab?.spec?.usesRuntimeMultiplexer == true) {
            vm.listMultiplexerSessionsForCurrentHost()
        }
    }

    if (showPassphrasePrompt) {
        ChuDialog(
            title = "Key passphrase",
            confirmLabel = "Connect",
            onConfirm = {
                val spec = pendingTabSpec
                showPassphrasePrompt = false
                if (spec != null) {
                    val preparedSpec = spec.copy(keyPassphrase = passphraseInput)
                    if (preparedSpec.usesRuntimeMultiplexer) {
                        vm.initiateMultiplexerOpen(preparedSpec)
                    } else {
                        vm.openTab(preparedSpec)
                    }
                }
                passphraseInput = ""
                pendingTabSpec = null
                passphraseFromPicker = false
            },
            onDismiss = {
                showPassphrasePrompt = false
                passphraseInput = ""
                pendingTabSpec = null
                if (!passphraseFromPicker) {
                    onBack()
                }
                passphraseFromPicker = false
            },
        ) {
            ChuTextField(
                value = passphraseInput,
                onValueChange = { passphraseInput = it },
                label = "Passphrase",
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    fun showLocalShellFilesUnsupported() {
        val message = "Files are not supported for local shell"
        localShellFilesMessage = message
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    LaunchedEffect(filesSupported, selectedTab) {
        if (filesSupported) {
            localShellFilesMessage = null
        }
        if (!filesSupported && selectedTab == ConnectionTab.Files) {
            vm.selectConnectionTab(ConnectionTab.Terminal)
        }
    }

    LaunchedEffect(sessionState.status, sessionState.error) {
        val previous = lastSessionStatus
        lastSessionStatus = sessionState.status
        if (sessionState.status == SessionStatus.Error && previous != SessionStatus.Error) {
            val message = sessionState.error ?: "Connection failed"
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    if (hostKeyPrompt != null) {
        val prompt = hostKeyPrompt
        ChuDialog(
            onDismiss = { vm.onHostKeyDecision(false) },
            title = "Verify host key",
            confirmLabel = "Accept",
            dismissLabel = "Reject",
            onConfirm = { vm.onHostKeyDecision(true) },
            properties = DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
            ),
        ) {
            val previous = prompt?.previousFingerprint
            val message = buildString {
                append("Host: ${prompt?.host}:${prompt?.port}\n")
                append("Algorithm: ${prompt?.algorithm}\n")
                if (previous != null) {
                    append("WARNING: host key changed!\n")
                    append("Old: $previous\n")
                }
                append("New: ${prompt?.fingerprint}")
            }
            ChuText(message, style = typography.body)
        }
    }

    val preflightError = multiplexerState.preflightError
    LaunchedEffect(preflightError) {
        if (preflightError != null) {
            Toast.makeText(context, preflightError, Toast.LENGTH_LONG).show()
        }
    }
    when (sessionState.status) {
        SessionStatus.Disconnected,
        SessionStatus.Error -> {
            if (tabMode == TerminalTabMode.Strip) {
                Column(modifier = screenInsetsModifier.fillMaxSize()) {
                    TerminalTabStrip(
                        tabs = tabs,
                        activeTabId = activeTabId,
                        onTabSelected = { id -> vm.selectTab(id) },
                        onAddTab = openAnotherSessionForCurrentHost,
                        onOpenManager = { showGlobalTabManager = true },
                    )
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        val errorMessage = preflightError ?: sessionState.error
                        if (errorMessage != null) {
                            TerminalRecoveryActions(
                                message = errorMessage,
                                isMultiplexerPreflight = preflightError != null,
                                dismissMultiplexerLabel = if (multiplexerState.reconnectRecovery) "dismiss" else "back",
                                onRetryMultiplexer = vm::retryPendingMultiplexerOpen,
                                onConnectWithoutMultiplexer = {
                                    if (vm.connectPendingWithoutMultiplexer()) {
                                        vm.selectConnectionTab(ConnectionTab.Terminal)
                                    }
                                },
                                onBack = { vm.dismissMultiplexerRecovery(onBack) },
                                onReconnect = vm::reconnect,
                            )
                        } else if (tabs.isEmpty()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                ChuText(
                                    "no terminal sessions",
                                    style = typography.body,
                                    color = colors.textMuted,
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                ChuButton(
                                    onClick = openAnotherSessionForCurrentHost,
                                    modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                                    variant = ChuButtonVariant.Outlined,
                                    bracketed = true,
                                ) {
                                    ChuText(
                                        "+ new connection",
                                        style = typography.label,
                                        color = colors.accent,
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                Column(
                    modifier = screenInsetsModifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val errorMessage = preflightError ?: sessionState.error
                    if (errorMessage != null) {
                        TerminalRecoveryActions(
                            message = errorMessage,
                            isMultiplexerPreflight = preflightError != null,
                            dismissMultiplexerLabel = if (multiplexerState.reconnectRecovery) "dismiss" else "back",
                            onRetryMultiplexer = vm::retryPendingMultiplexerOpen,
                            onConnectWithoutMultiplexer = {
                                if (vm.connectPendingWithoutMultiplexer()) {
                                    vm.selectConnectionTab(ConnectionTab.Terminal)
                                }
                            },
                            onBack = { vm.dismissMultiplexerRecovery(onBack) },
                            onReconnect = vm::reconnect,
                        )
                    }
                }
            }
        }

        SessionStatus.Connecting -> {
            val hostLabel = if (tabMode == TerminalTabMode.Strip) {
                activeTab?.spec?.tabLabel?.let { "$it..." } ?: "..."
            } else {
                activeTabForHost?.spec?.host?.let { "$it..." } ?: "..."
            }
            if (tabMode == TerminalTabMode.Strip) {
                Column(modifier = screenInsetsModifier.fillMaxSize()) {
                    TerminalTabStrip(
                        tabs = tabs,
                        activeTabId = activeTabId,
                        onTabSelected = { id -> vm.selectTab(id) },
                        onAddTab = openAnotherSessionForCurrentHost,
                        onOpenManager = { showGlobalTabManager = true },
                    )
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        ChuText(
                            "Connecting to $hostLabel",
                            style = typography.body,
                        )
                    }
                }
            } else {
                Column(
                    modifier = screenInsetsModifier.fillMaxSize().padding(16.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    ChuText(
                        "Connecting to $hostLabel",
                        style = typography.body,
                    )
                }
            }
        }

        SessionStatus.Connected,
        SessionStatus.Reconnecting -> {
            val isReconnecting = sessionState.status == SessionStatus.Reconnecting
            val snapshot = sessionState.snapshot
            if (snapshot != null) {
                var modifierState by remember { mutableStateOf(ModifierState()) }
                val inputViewRef = remember { mutableStateOf<TerminalInputView?>(null) }
                var menuSize by remember { mutableStateOf(IntSize.Zero) }
                val clipboard = remember {
                    context.getSystemService(ClipboardManager::class.java)
                }
                LaunchedEffect(sessionState.handle, activeTabId) {
                    selection = null
                    selectionState = null
                    menuSize = IntSize.Zero
                }

                fun pasteClipboard(): Boolean {
                    val clip = clipboard?.primaryClip
                    if (clip == null || clip.itemCount == 0) {
                        return false
                    }
                    val text = clip.getItemAt(0).coerceToText(context).toString()
                    if (text.isNotEmpty()) {
                        vm.onPasteText(modifierState.applyToText(text))
                        selection = null
                        selectionState = null
                        return true
                    }
                    return false
                }

                Box(modifier = screenInsetsModifier.fillMaxSize()) {
                    LaunchedEffect(ghosttyTheme, colors, isDarkTheme) {
                        vm.onColorSchemeChanged(isDarkTheme)
                        vm.onDefaultColorsChanged(
                            fg =
                                ghosttyTheme?.foreground?.toRgbIntArray()
                                    ?: colors.textPrimary.toRgbIntArray(),
                            bg =
                                ghosttyTheme?.background?.toRgbIntArray()
                                    ?: colors.background.toRgbIntArray(),
                            cursor =
                                ghosttyTheme?.cursorColor?.toRgbIntArray()
                                    ?: colors.accent.toRgbIntArray(),
                            palette = ghosttyTheme?.toTerminalPaletteBytes(),
                        )
                    }

                    LaunchedEffect(sessionState.bellCount) {
                        if (sessionState.bellCount > 0) {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        }
                    }

                    val titleText = sessionState.title?.takeIf { it.isNotBlank() }
                    val pwdText = sessionState.pwd?.takeIf { it.isNotBlank() }
                    val inputMethodManager = remember {
                        context.getSystemService(InputMethodManager::class.java)
                    }
                    val requestInputFocus: () -> Unit = {
                        inputViewRef.value?.let { view -> view.showKeyboard(inputMethodManager) }
                    }
                    // Double-tap-to-type: a single terminal tap only takes
                    // focus and forwards the mouse click — full-screen TUIs
                    // (herdr tabs/sidebar) are tapped constantly to NAVIGATE,
                    // and popping the IME on every one of those taps was
                    // unwanted. The canvas detects the double tap itself (its
                    // own detector used to swallow second taps for word
                    // selection, so a screen-level timer never saw them) and
                    // reports it via onDoubleTap.
                    val onTerminalTapped: () -> Unit = {
                        inputViewRef.value?.takeFocusSilently(inputMethodManager)
                    }
                    val hideSoftKeyboard: () -> Unit = {
                        val view = inputViewRef.value
                        if (view != null) {
                            inputMethodManager?.hideSoftInputFromWindow(view.windowToken, 0)
                        }
                    }
                    var hasClipboardText by remember { mutableStateOf(false) }
                    val scope = rememberCoroutineScope()
                    LaunchedEffect(clipboard) {
                        fun check() {
                            hasClipboardText =
                                try {
                                    clipboard?.hasPrimaryClip() == true &&
                                        clipboard!!.primaryClip?.getItemAt(0)?.text?.isNotEmpty() ==
                                            true
                                } catch (_: SecurityException) {
                                    false
                                }
                        }
                        check()
                        val listener = ClipboardManager.OnPrimaryClipChangedListener { check() }
                        clipboard?.addPrimaryClipChangedListener(listener)
                        try {
                            awaitCancellation()
                        } finally {
                            clipboard?.removePrimaryClipChangedListener(listener)
                        }
                    }


                    fun dispatchAccessoryAction(action: AccessoryAction) {
                        if (
                            action is AccessoryAction.SendText && chuchuKeys.handleText(action.text)
                        ) {
                            return
                        }
                        if (chuchuKeys.isPrefixActive) {
                            chuchuKeys.reset()
                        }
                        val currentModifierState = modifierState
                        val result =
                            TerminalAccessoryDispatcher.dispatch(action, currentModifierState)
                        modifierState = result.modifierState

                        if (result.suppressImeInput) {
                            inputViewRef.value?.armInputSuppression(action.toString())
                        }

                        result.specialKey?.let { key ->
                            vm.onSpecialKeyInput(key, currentModifierState.terminalMods())
                        }

                        result.text?.let { text -> vm.onTextInput(text) }

                        if (result.shouldPaste) {
                            pasteClipboard()
                        }
                    }

                    fun putOnClipboard(text: String, note: String) {
                        clipboard?.setPrimaryClip(ClipData.newPlainText("terminal selection", text))
                        Toast.makeText(context, note, Toast.LENGTH_SHORT).show()
                        selection = null
                        selectionState = null
                    }

                    fun copySelection() {
                        putOnClipboard(selectionState?.text ?: return, "Đã copy")
                    }

                    /**
                     * Copy nhưng nối các dòng lại thành một.
                     *
                     * Vì sao cần: terminal chỉ rộng ~45 cột, mà thứ hay copy nhất là
                     * một dòng lệnh dài. Ứng dụng vẽ ra màn hình (Claude Code chẳng
                     * hạn) tự bẻ dòng bằng ký tự xuống dòng THẬT kèm thụt lề, nên
                     * trong bộ nhớ terminal nó đã là hai dòng — ghostty không có cách
                     * nào biết nó vốn là một. Dán ra là lệnh gãy đôi, chạy không được.
                     *
                     * Đây là hành động RIÊNG, không phải sửa ngầm nút copy: nối dòng
                     * đúng cho lệnh nhưng sai cho đoạn code nhiều dòng, nên phải để
                     * người dùng chọn chứ không tự đoán.
                     */
                    fun copySelectionJoined() {
                        val raw = selectionState?.text ?: return
                        val joined = raw.split('\n')
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .joinToString(" ")
                        putOnClipboard(joined, "Đã copy (nối dòng)")
                    }

                    val importFileLauncher =
                        rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.GetMultipleContents()
                        ) { uris: List<Uri> ->
                            if (uris.isEmpty()) return@rememberLauncherForActivityResult
                            scope.launch(Dispatchers.IO) {
                                var success = 0
                                var failed = 0
                                var lastError: String? = null
                                val total = uris.size
                                uris.forEachIndexed { index, uri ->
                                    val fileName =
                                        context.contentResolver
                                            .query(uri, null, null, null, null)
                                            ?.use { cursor ->
                                                val idx =
                                                    cursor.getColumnIndex(
                                                        OpenableColumns.DISPLAY_NAME
                                                    )
                                                if (cursor.moveToFirst() && idx >= 0)
                                                    cursor.getString(idx)
                                                else null
                                            } ?: uri.lastPathSegment ?: "uploaded_${index}"
                                    val fileSize =
                                        context.contentResolver
                                            .query(uri, null, null, null, null)
                                            ?.use { cursor ->
                                                val idx =
                                                    cursor.getColumnIndex(OpenableColumns.SIZE)
                                                if (cursor.moveToFirst() && idx >= 0)
                                                    cursor.getLong(idx)
                                                else 0L
                                            } ?: 0L
                                    try {
                                        val stream =
                                            context.contentResolver.openInputStream(uri)
                                                ?: throw IllegalStateException("Cannot open file")
                                        stream.use { input ->
                                            vm.beginUpload(fileName)
                                            vm.setUploadProgress(
                                                UploadProgress(
                                                    fileName = fileName,
                                                    bytesWritten = 0,
                                                    totalBytes = fileSize,
                                                    fileIndex = index,
                                                    totalFiles = total,
                                                )
                                            )
                                            val buffer = ByteArray(65536)
                                            var bytesWritten = 0L
                                            var lastProgressBytes = 0L
                                            var read: Int
                                            while (input.read(buffer).also { read = it } != -1) {
                                                vm.writeUploadChunk(buffer.copyOf(read))
                                                bytesWritten += read
                                                if (
                                                    bytesWritten - lastProgressBytes >= 262144 ||
                                                        bytesWritten == fileSize
                                                ) {
                                                    lastProgressBytes = bytesWritten
                                                    vm.setUploadProgress(
                                                        UploadProgress(
                                                            fileName = fileName,
                                                            bytesWritten = bytesWritten,
                                                            totalBytes = fileSize,
                                                            fileIndex = index,
                                                            totalFiles = total,
                                                        )
                                                    )
                                                }
                                            }
                                            vm.finishUpload()
                                        }
                                        success++
                                    } catch (e: Exception) {
                                        failed++
                                        lastError = e.message ?: e.javaClass.simpleName
                                        runCatching { vm.finishUpload() }
                                    } finally {
                                        vm.setUploadProgress(null)
                                    }
                                }
                                withContext(Dispatchers.Main) {
                                    val msg =
                                        when {
                                            failed == 0 -> "Uploaded $success file(s)"
                                            lastError != null ->
                                                "Uploaded $success, $failed failed: $lastError"
                                            else -> "Uploaded $success, $failed failed"
                                        }
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                            }
                        }

                    var pendingDownloadBytes by remember { mutableStateOf<ByteArray?>(null) }
                    var pendingDownloadName by remember { mutableStateOf("download.bin") }
                    var pendingDeleteEntry by remember {
                        mutableStateOf<com.jossephus.chuchu.ui.screens.Files.FileBrowserEntry?>(
                            null
                        )
                    }

                    val downloadLauncher =
                        rememberLauncherForActivityResult(
                            contract =
                                ActivityResultContracts.CreateDocument("application/octet-stream")
                        ) { uri: Uri? ->
                            val bytes = pendingDownloadBytes
                            if (uri == null || bytes == null)
                                return@rememberLauncherForActivityResult
                            scope.launch(Dispatchers.IO) {
                                runCatching {
                                        context.contentResolver.openOutputStream(uri)?.use {
                                            it.write(bytes)
                                        } ?: error("Cannot open destination")
                                    }
                                    .onSuccess {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(
                                                    context,
                                                    "Downloaded ${pendingDownloadName}",
                                                    Toast.LENGTH_SHORT,
                                                )
                                                .show()
                                        }
                                    }
                                    .onFailure {
                                        withContext(Dispatchers.Main) {
                                            Toast.makeText(
                                                    context,
                                                    "Download failed: ${it.message}",
                                                    Toast.LENGTH_LONG,
                                                )
                                                .show()
                                        }
                                    }
                            }
                        }

                    LaunchedEffect(Unit) {
                        // Take focus so keys route here, but do NOT summon the
                        // soft keyboard: this effect re-fires on every tab
                        // switch, and auto-popping the IME on each switch was
                        // unwanted. The keyboard still appears when the user
                        // taps the terminal (onTap -> requestInputFocus).
                        inputViewRef.value?.takeFocusSilently(inputMethodManager)
                        vm.onFocusChanged(true)
                    }
                    Column(
                        modifier =
                            Modifier.fillMaxSize()
                                .blur(
                                    if (showTabSheet || showGlobalTabManager) 10.dp
                                    else 0.dp
                                )
                                // Smooth slide (v9's snap-to-target padding
                                // read as jank; the snap+GPU-translation
                                // rework broke keyboard dismissal on some
                                // IMEs — content stayed up). The EARLY PTY
                                // resize still happens: a LaunchedEffect on
                                // imeAnimationTarget below predicts the final
                                // viewport at animation START and dispatches
                                // the resize concurrently with the slide.
                                .imePadding()
                    ) {
                        // Tab strip (strip mode only — always visible even with zero tabs)
                        if (tabMode == TerminalTabMode.Strip) {
                            TerminalTabStrip(
                                tabs = tabs,
                                activeTabId = activeTabId,
                                onTabSelected = { id ->
                                    vm.selectTab(id)
                                },
                                onAddTab = openAnotherSessionForCurrentHost,
                                onOpenManager = {
                                    showGlobalTabManager = true
                                },
                            )
                        }

                        // Empty state in strip mode when all tabs are closed
                        if (tabMode == TerminalTabMode.Strip && tabs.isEmpty()) {
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    ChuText(
                                        "no terminal sessions",
                                        style = typography.body,
                                        color = colors.textMuted,
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    ChuButton(
                                        onClick = openAnotherSessionForCurrentHost,
                                        modifier = Modifier.defaultMinSize(minWidth = 48.dp, minHeight = 48.dp),
                                        variant = ChuButtonVariant.Outlined,
                                        bracketed = true,
                                    ) {
                                        ChuText(
                                            "+ new connection",
                                            style = typography.label,
                                            color = colors.accent,
                                        )
                                    }
                                }
                            }
                        } else if (selectedTab == ConnectionTab.Files && filesSupported) {
                            FileBrowserScreen(
                                state = fileBrowserState,
                                onGoUp = vm::goUpDirectory,
                                onRefresh = vm::refreshFileBrowser,
                                onSelectSort = vm::selectFileSort,
                                onOpenPath = vm::openPath,
                                onBackToTerminal = {
                                    vm.selectConnectionTab(ConnectionTab.Terminal)
                                },
                                onCopyPath = { path ->
                                    clipboard?.setPrimaryClip(ClipData.newPlainText("path", path))
                                    Toast.makeText(context, "Copied path", Toast.LENGTH_SHORT)
                                        .show()
                                },
                                onImportFile = { importFileLauncher.launch("*/*") },
                                onOpenFile = { entry ->
                                    val tabId = activeTabId ?: return@FileBrowserScreen
                                    scope.launch(Dispatchers.IO) {
                                        runCatching {
                                                val bytes = vm.readFile(tabId, entry, 32 * 1024 * 1024)
                                                val safeName = entry.name.ifBlank { "remote_file" }
                                                val outFile = File(context.cacheDir, safeName)
                                                outFile.writeBytes(bytes)
                                                val uri =
                                                    FileProvider.getUriForFile(
                                                        context,
                                                        "${context.packageName}.fileprovider",
                                                        outFile,
                                                    )
                                                val ext =
                                                    safeName.substringAfterLast('.', "").lowercase()
                                                val mime =
                                                    MimeTypeMap.getSingleton()
                                                        .getMimeTypeFromExtension(ext)
                                                        ?: "application/octet-stream"
                                                val viewIntent =
                                                    Intent(Intent.ACTION_VIEW).apply {
                                                        setDataAndType(uri, mime)
                                                        addFlags(
                                                            Intent.FLAG_GRANT_READ_URI_PERMISSION
                                                        )
                                                    }
                                                withContext(Dispatchers.Main) {
                                                    context.startActivity(
                                                        Intent.createChooser(
                                                            viewIntent,
                                                            "Open with",
                                                        )
                                                    )
                                                }
                                            }
                                            .onFailure {
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(
                                                            context,
                                                            "Open failed: ${it.message}",
                                                            Toast.LENGTH_LONG,
                                                        )
                                                        .show()
                                                }
                                            }
                                    }
                                },
                                onDeleteFile = { entry -> pendingDeleteEntry = entry },
                                onDownloadFile = { entry ->
                                    val tabId = activeTabId ?: return@FileBrowserScreen
                                    scope.launch(Dispatchers.IO) {
                                        runCatching { vm.readFile(tabId, entry, 16 * 1024 * 1024) }
                                            .onSuccess { bytes ->
                                                pendingDownloadBytes = bytes
                                                pendingDownloadName = entry.name
                                                withContext(Dispatchers.Main) {
                                                    downloadLauncher.launch(entry.name)
                                                }
                                            }
                                            .onFailure {
                                                withContext(Dispatchers.Main) {
                                                    Toast.makeText(
                                                            context,
                                                            "Download failed: ${it.message}",
                                                            Toast.LENGTH_LONG,
                                                        )
                                                        .show()
                                                }
                                            }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                            )
                            pendingDeleteEntry?.let { entry ->
                                ChuDialog(
                                    title = "Delete ${entry.name}?",
                                    confirmLabel = "Delete",
                                    dismissLabel = "Cancel",
                                    onConfirm = {
                                        val tabId = activeTabId ?: run {
                                            pendingDeleteEntry = null
                                            return@ChuDialog
                                        }
                                        pendingDeleteEntry = null
                                        scope.launch(Dispatchers.IO) {
                                            runCatching { vm.deleteFile(tabId, entry) }
                                                .onSuccess {
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(
                                                                context,
                                                                "Deleted ${entry.name}",
                                                                Toast.LENGTH_SHORT,
                                                            )
                                                            .show()
                                                    }
                                                }
                                                .onFailure {
                                                    withContext(Dispatchers.Main) {
                                                        Toast.makeText(
                                                                context,
                                                                "Delete failed: ${it.message}",
                                                                Toast.LENGTH_LONG,
                                                            )
                                                            .show()
                                                    }
                                                }
                                        }
                                    },
                                    onDismiss = { pendingDeleteEntry = null },
                                ) {
                                    ChuText(
                                        "This action cannot be undone.",
                                        style = typography.body,
                                    )
                                }
                            }
                        } else {
                            Box(modifier = Modifier.weight(1f)) {
                                TerminalCanvas(
                                    snapshot = snapshot,
                                    fontSizeSp = terminalFontSizeSp,
                                    minFontSizeSp = SettingsRepository.MIN_TERMINAL_FONT_SIZE,
                                    maxFontSizeSp = SettingsRepository.MAX_TERMINAL_FONT_SIZE,
                                    cursorColor =
                                        ghosttyTheme?.cursorColor
                                            ?: Color.White.copy(alpha = 0.28f),
                                    cursorTextColor = ghosttyTheme?.cursorText,
                                    selectionBackgroundColor =
                                        ghosttyTheme?.selectionBackground
                                            ?: colors.accent.copy(alpha = 0.45f),
                                    selectionForegroundColor =
                                        ghosttyTheme?.selectionForeground ?: colors.onAccent,
                                    selection = selection,
                                    onSelectionChange = { selection = it },
                                    terminalHandle = sessionState.handle,
                                    modifier = Modifier.fillMaxSize(),
                                    onResize = { cols, rows, cw, ch, w, h ->
                                        // Remember the keyboard-hidden canvas
                                        // geometry — the prediction baseline
                                        // for the imeAnimationTarget effect.
                                        if (!imeVisibleNow) {
                                            fullCanvasArgs[0] = cols
                                            fullCanvasArgs[1] = rows
                                            fullCanvasArgs[2] = cw
                                            fullCanvasArgs[3] = ch
                                            fullCanvasArgs[4] = w
                                            fullCanvasArgs[5] = h
                                        }
                                        vm.onCanvasSizeChanged(cols, rows, cw, ch, w, h)
                                    },
                                    onTap = onTerminalTapped,
                                    // Double tap = compose box (Vietnamese
                                    // input, the most-used action; its [×]
                                    // hands off to the plain keyboard).
                                    // Triple tap deliberately does nothing —
                                    // the triple-tap-Enter experiment was
                                    // removed at the user's request.
                                    onDoubleTap = { showComposeBox = true },
                                    onTripleTap = {},
                                    onArrowKey = { key, count ->
                                        vm.onArrowKeyRepeat(key, 0, count)
                                    },
                                    onPrimaryClick = vm::onPrimaryMouseClick,
                                    onAppSelectionDrag = vm::onAppSelectionDrag,
                                    onScroll = vm::onScroll,
                                    onFontSizeChange = { sizeSp -> terminalFontSizeSp = sizeSp },
                                    onSelectionChanged = { state -> selectionState = state },
                                )

                                Row(
                                    modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    if (isReconnecting) {
                                        ChuText(
                                            text =
                                                "Reconnecting${sessionState.reconnectAttempt.takeIf { it > 0 }?.let { " ($it)" } ?: ""}",
                                            style = typography.labelSmall,
                                            color = colors.error,
                                        )
                                    }
                                    if (pwdText != null) {
                                        ChuText(
                                            text = pwdText,
                                            style = typography.labelSmall,
                                            color = colors.textPrimary.copy(alpha = 0.7f),
                                        )
                                    }
                                }

                                val selState = selectionState
                                if (selState != null) {
                                    val sel = selection
                                    val isAnchorStart = sel != null && sel.anchorIndex <= sel.focusIndex
                                    TerminalSelectionHandle(
                                        tipX = selState.startOffset.x,
                                        tipY = selState.startOffset.y + selState.cellHeightPx / 2f,
                                        color = colors.accent,
                                        borderColor = colors.background,
                                        cellWidthPx = selState.cellWidthPx,
                                        cellHeightPx = selState.cellHeightPx,
                                        cols = selState.cols,
                                        startCellProvider = {
                                            val s = selection
                                            if (s != null) minOf(s.anchorIndex, s.focusIndex) else 0
                                        },
                                        onDragToCell = { newCell ->
                                            selection = selection?.withStart(newCell, updateAnchor = isAnchorStart)
                                        },
                                    )
                                    TerminalSelectionHandle(
                                        tipX = selState.endOffset.x,
                                        tipY = selState.endOffset.y - selState.cellHeightPx / 2f,
                                        color = colors.accent,
                                        borderColor = colors.background,
                                        cellWidthPx = selState.cellWidthPx,
                                        cellHeightPx = selState.cellHeightPx,
                                        cols = selState.cols,
                                        startCellProvider = {
                                            val s = selection
                                            if (s != null) maxOf(s.anchorIndex, s.focusIndex) else 0
                                        },
                                        onDragToCell = { newCell ->
                                            selection = selection?.withEnd(newCell, updateAnchor = !isAnchorStart)
                                        },
                                    )

                                    val menuGapPx = with(density) { 8.dp.toPx() }
                                    val selLeft = selState.boundsLeft
                                    val selRight = selState.boundsRight
                                    val selTop = selState.boundsTop
                                    val selBottom = selState.boundsBottom
                                    val centerX = (selLeft + selRight) / 2f
                                    val menuWidth = menuSize.width.coerceAtLeast(1)
                                    val menuHeight = menuSize.height.coerceAtLeast(1)
                                    val maxMenuX = (selState.canvasWidthPx - menuWidth).coerceAtLeast(0)
                                    val menuX = (centerX - menuWidth / 2f).toInt().coerceIn(0, maxMenuX)
                                    val aboveY = (selTop - menuHeight - menuGapPx).toInt()
                                    val belowY = (selBottom + menuGapPx).toInt()
                                    val maxMenuY = (selState.canvasHeightPx - menuHeight).coerceAtLeast(0)
                                    val menuY = if (aboveY >= 0) aboveY else belowY.coerceIn(0, maxMenuY)
                                    Row(
                                        modifier =
                                            Modifier.offset { IntOffset(menuX, menuY) }
                                                .onGloballyPositioned { menuSize = it.size }
                                                .background(colors.background)
                                                .border(1.dp, colors.border)
                                                .padding(horizontal = 4.dp, vertical = 2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (!selState.text.isNullOrEmpty()) {
                                            ChuButton(
                                                onClick = ::copySelection,
                                                variant = ChuButtonVariant.Ghost,
                                                bracketed = true,
                                                borderColor = colors.textMuted,
                                                contentPadding =
                                                    PaddingValues(
                                                        horizontal = 12.dp,
                                                        vertical = 6.dp,
                                                    ),
                                            ) {
                                                ChuText(
                                                    "copy",
                                                    style = typography.label,
                                                    color = colors.textMuted,
                                                )
                                            }
                                            // Chi hien khi vung chon co NHIEU dong —
                                            // mot dong thi nut nay khong lam gi khac
                                            // nut copy, bay ra chi to chat cho.
                                            if (selState.text.orEmpty().contains('\n')) {
                                                ChuButton(
                                                    onClick = ::copySelectionJoined,
                                                    variant = ChuButtonVariant.Ghost,
                                                    bracketed = true,
                                                    borderColor = colors.textMuted,
                                                    contentPadding =
                                                        PaddingValues(
                                                            horizontal = 12.dp,
                                                            vertical = 6.dp,
                                                        ),
                                                ) {
                                                    ChuText(
                                                        "copy 1 dòng",
                                                        style = typography.label,
                                                        color = colors.textMuted,
                                                    )
                                                }
                                            }
                                        }
                                        if (hasClipboardText) {
                                            ChuButton(
                                                onClick = { pasteClipboard() },
                                                variant = ChuButtonVariant.Ghost,
                                                bracketed = true,
                                                borderColor = colors.textMuted,
                                                contentPadding =
                                                    PaddingValues(
                                                        horizontal = 12.dp,
                                                        vertical = 6.dp,
                                                    ),
                                            ) {
                                                ChuText(
                                                    "paste",
                                                    style = typography.label,
                                                    color = colors.textMuted,
                                                )
                                            }
                                        }
                                    }
                                }

                                AndroidView(
                                    modifier =
                                        Modifier.align(Alignment.BottomStart).size(1.dp).alpha(0f),
                                    factory = { viewContext ->
                                        TerminalInputView(viewContext)
                                            .apply {
                                                onTerminalText = { text ->
                                                    if (!chuchuKeys.handleText(text)) {
                                                        vm.dispatchTextWithModifierState(
                                                            text,
                                                            modifierState,
                                                        )
                                                    }
                                                }
                                                onTerminalKey = { key, codepoint, mods, action, charCode ->
                                                    var shouldForwardToTerminal = true
                                                    val overlayOpen = showTabSheet || showGlobalTabManager
                                                    val overlayTabs = if (showGlobalTabManager) tabs else tabsForHost
                                                    if (
                                                        overlayOpen &&
                                                            overlayTabs.isEmpty() &&
                                                            action == GhosttyKeyAction.Press &&
                                                            key == TerminalSpecialKey.Escape.engineKey
                                                    ) {
                                                        showTabSheet = false
                                                        showGlobalTabManager = false
                                                        shouldForwardToTerminal = false
                                                    } else if (overlayOpen && overlayTabs.isNotEmpty()) {
                                                        var consumedByTabSwitcher = true
                                                        val isPress =
                                                            action == GhosttyKeyAction.Press
                                                        if (isPress && chuchuKeys.isPrefixActive) {
                                                            when (
                                                                codepoint.toChar().lowercaseChar()
                                                            ) {
                                                                'n' -> {
                                                                    vm.duplicateActiveTab()
                                                                    vm.selectConnectionTab(
                                                                        ConnectionTab.Terminal
                                                                    )
                                                                    showTabSheet = false
                                                                    showGlobalTabManager = false
                                                                }
                                                                't' -> {
                                                                    if (tabMode == TerminalTabMode.Strip) {
                                                                        showGlobalTabManager = true
                                                                    } else {
                                                                        showTabSheet = true
                                                                    }
                                                                }
                                                                else -> {}
                                                            }
                                                            chuchuKeys.reset()
                                                            shouldForwardToTerminal = false
                                                            consumedByTabSwitcher = true
                                                        }
                                                        if (isPress) {
                                                            when (key) {
                                                                TerminalSpecialKey.Left.engineKey,
                                                                TerminalSpecialKey.Up.engineKey ->
                                                                    focusedTabIndex =
                                                                        (focusedTabIndex - 1).mod(
                                                                            overlayTabs.size
                                                                        )

                                                                TerminalSpecialKey.Right.engineKey,
                                                                TerminalSpecialKey.Down.engineKey ->
                                                                    focusedTabIndex =
                                                                        (focusedTabIndex + 1).mod(
                                                                            overlayTabs.size
                                                                        )

                                                                TerminalSpecialKey.Enter
                                                                    .engineKey -> {
                                                                    overlayTabs
                                                                        .getOrNull(focusedTabIndex)
                                                                        ?.let {
                                                                            vm.selectTab(it.id)
                                                                            showTabSheet = false
                                                                            showGlobalTabManager = false
                                                                        }
                                                                }

                                                                TerminalSpecialKey.Escape
                                                                    .engineKey -> {
                                                                    showTabSheet = false
                                                                    showGlobalTabManager = false
                                                                }

                                                                else ->
                                                                    consumedByTabSwitcher = false
                                                            }
                                                        }
                                                        if (consumedByTabSwitcher) {
                                                            shouldForwardToTerminal = false
                                                        }
                                                    }
                                                    if (shouldForwardToTerminal) {
                                                        val mergedMods =
                                                            mods or modifierState.terminalMods()
                                                        vm.onHardwareKey(
                                                            key,
                                                            codepoint,
                                                            mergedMods,
                                                            action,
                                                            charCode,
                                                        )
                                                    }
                                                }
                                                setOnFocusChangeListener { _, hasFocus ->
                                                    vm.onFocusChanged(hasFocus)
                                                    // No showKeyboard on focus gain: tab
                                                    // switches grant focus silently. Explicit
                                                    // taps go through requestInputFocus ->
                                                    // showKeyboard instead.
                                                }
                                            }
                                            .also { view -> inputViewRef.value = view }
                                    },
                                    update = { view ->
                                        if (inputViewRef.value == null) {
                                            inputViewRef.value = view
                                        }
                                    },
                                )

                                if (currentTerminalCustomKeyGroups.isNotEmpty() &&
                                    (showCustomActionsFab || fabFilteredActions != null)
                                ) {
                                    TerminalCustomActionsFab(
                                        groups = currentTerminalCustomKeyGroups,
                                        onActionClick = { action ->
                                            val decoded = decodeCustomActionValue(action.payload)
                                            val unescaped =
                                                com.jossephus.chuchu.ui.terminal
                                                    .unescapeCustomActionText(decoded.text)
                                            val rawText =
                                                unescaped +
                                                    if (
                                                        CustomActionModifier.Enter in
                                                            decoded.modifiers
                                                    )
                                                        "\n"
                                                    else ""
                                            if (unescaped.any { it.code < 0x20 || it.code == 0x7F }) {
                                                // Escaped control bytes (\cb, \e,
                                                // \xNN): ship raw so prefix combos
                                                // like Ctrl+B,A survive.
                                                vm.onTextInput(rawText.replace('\n', '\r'))
                                            } else {
                                                val actionModifierState =
                                                    modifierStateForCustomAction(decoded.modifiers)
                                                vm.dispatchTextWithModifierState(
                                                    rawText,
                                                    actionModifierState,
                                                )
                                            }
                                            requestInputFocus()
                                        },
                                        modifier =
                                            Modifier.align(Alignment.BottomEnd)
                                                .padding(end = 14.dp, bottom = 12.dp),
                                        filteredActions = fabFilteredActions,
                                        onClearFilter = { fabFilteredActions = null },
                                    )
                                }

                                // Compose-box trigger lives in the accessory bar
                                // ([✎] next to Esc/Tab). Keyboard summon is
                                // double-tap on the terminal (canvas onDoubleTap).
                                if (showComposeBox) {
                                    // Shared by the [Gửi ↵] button and the IME
                                    // Send key: paste + Enter + close.
                                    val sendComposeBox = {
                                        if (composeBoxText.isNotEmpty()) {
                                            vm.onPasteText(composeBoxText)
                                            vm.dispatchTextWithModifierState(
                                                "\n",
                                                ModifierState(),
                                            )
                                        }
                                        composeBoxText = ""
                                        showComposeBox = false
                                    }
                                    Column(
                                        modifier =
                                            Modifier.align(Alignment.BottomCenter)
                                                .fillMaxWidth()
                                                .background(colors.background.copy(alpha = 0.96f))
                                                .padding(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            ChuTextField(
                                                value = composeBoxText,
                                                onValueChange = { composeBoxText = it },
                                                label = "",
                                                showLabel = false,
                                                autoFocus = true,
                                                singleLine = true,
                                                modifier = Modifier.weight(1f),
                                                keyboardOptions =
                                                    androidx.compose.foundation.text.KeyboardOptions(
                                                        capitalization =
                                                            androidx.compose.ui.text.input
                                                                .KeyboardCapitalization.None,
                                                        autoCorrectEnabled = false,
                                                        imeAction =
                                                            androidx.compose.ui.text.input
                                                                .ImeAction.Send,
                                                    ),
                                                keyboardActions =
                                                    androidx.compose.foundation.text.KeyboardActions(
                                                        onSend = { sendComposeBox() },
                                                    ),
                                                verticalPadding = 7.dp,
                                            )
                                            // [×]: dismiss the compose box and
                                            // leave the keyboard AS IT IS.
                                            // Keyboard up: retarget it onto the
                                            // terminal BEFORE the box closes
                                            // (closing first made it dip and
                                            // re-raise). Keyboard already down
                                            // (e.g. user swiped back): only move
                                            // focus silently — summoning it
                                            // here was unwanted.
                                            ChuButton(
                                                onClick = {
                                                    if (imeVisibleNow) {
                                                        requestInputFocus()
                                                    } else {
                                                        inputViewRef.value
                                                            ?.takeFocusSilently(inputMethodManager)
                                                    }
                                                    showComposeBox = false
                                                },
                                                variant = ChuButtonVariant.Ghost,
                                                bracketed = true,
                                                borderColor = colors.textMuted,
                                                contentPadding =
                                                    PaddingValues(
                                                        horizontal = 9.dp,
                                                        vertical = 6.dp,
                                                    ),
                                            ) {
                                                ChuText("×", style = typography.label)
                                            }
                                        }
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            ChuButton(
                                                onClick = {
                                                    if (composeBoxText.isNotEmpty()) {
                                                        vm.onPasteText(composeBoxText)
                                                    }
                                                    composeBoxText = ""
                                                    showComposeBox = false
                                                },
                                                variant = ChuButtonVariant.Outlined,
                                                bracketed = true,
                                                contentPadding =
                                                    PaddingValues(
                                                        horizontal = 10.dp,
                                                        vertical = 6.dp,
                                                    ),
                                            ) {
                                                ChuText("insert", style = typography.label)
                                            }
                                            ChuButton(
                                                onClick = sendComposeBox,
                                                variant = ChuButtonVariant.Outlined,
                                                bracketed = true,
                                                borderColor = colors.accent,
                                                contentPadding =
                                                    PaddingValues(
                                                        horizontal = 10.dp,
                                                        vertical = 6.dp,
                                                    ),
                                            ) {
                                                ChuText("send ↵", style = typography.label)
                                            }
                                        }
                                    }
                                }

                                androidx.compose.animation.AnimatedVisibility(
                                    visible = chuchuKeys.isPrefixActive,
                                    enter = fadeIn(),
                                    exit = fadeOut(),
                                    modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth(),
                                ) {
                                    Row(
                                        modifier =
                                            Modifier.fillMaxWidth()
                                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                                .background(colors.surface)
                                                .border(1.dp, colors.border)
                                                .padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        ChuText(
                                            "⌘",
                                            style = typography.label,
                                            color = colors.accent,
                                            modifier = Modifier.width(24.dp),
                                        )
                                        FlowRow(
                                            modifier = Modifier.weight(1f),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                                            verticalArrangement = Arrangement.spacedBy(2.dp),
                                        ) {
                                            chuchuKeys.hints().forEach { hint ->
                                                ChuText(
                                                    "${hint.key}: ${hint.description}",
                                                    style = typography.labelSmall,
                                                    color = colors.textSecondary,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // No spacer here: the accessory bar sits on a distinct
                        // surface tone now, and any gap between it and the
                        // canvas/compose-box shows as a background-colored slit.
                        if (selectedTab == ConnectionTab.Terminal) {
                            if (activeHostCount > 1 && currentHostName != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                                    horizontalArrangement = Arrangement.End,
                                ) {
                                    ChuText(
                                        text = currentHostName,
                                        style = typography.labelSmall,
                                        color = colors.textMuted.copy(alpha = 0.7f),
                                    )
                                }
                            }
                            localShellFilesMessage?.let { message ->
                                ChuText(
                                    text = message,
                                    style = typography.labelSmall,
                                    color = colors.textSecondary,
                                    modifier =
                                        Modifier.fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 4.dp)
                                            .semantics { liveRegion = LiveRegionMode.Polite },
                                )
                            }
                            KeyboardAccessoryBar(
                                entries = accessoryLayout,
                                modifierState = modifierState,
                                onAction = ::dispatchAccessoryAction,
                                onSettings = onOpenSettings,
                                onChuchuKey = {
                                    chuchuKeys.togglePrefix()
                                    requestInputFocus()
                                },
                                chuchuKeyActive = chuchuKeys.isPrefixActive,
                                onOpenFiles = {
                                    if (filesSupported) {
                                        vm.selectConnectionTab(ConnectionTab.Files)
                                    } else {
                                        showLocalShellFilesUnsupported()
                                    }
                                },
                                onComposeBox = { showComposeBox = true },
                                onSummonKeyboard = requestInputFocus,
                                useSingleRow = useSingleRowAccessoryBar,
                            )
                        }
                    }
                }

                val uploadProgress = fileBrowserState.uploadProgress
                if (uploadProgress != null) {
                    UploadProgressDialog(progress = uploadProgress)
                }
                if (showTabSheet) {
                    val paletteAccessoryAction: (AccessoryAction) -> Unit = { action ->
                        if (
                            !(action is AccessoryAction.SendText &&
                                chuchuKeys.handleText(action.text))
                        ) {
                            if (chuchuKeys.isPrefixActive) {
                                chuchuKeys.reset()
                            }
                            val preDispatchModifierState = modifierState
                            val result =
                                TerminalAccessoryDispatcher.dispatch(action, preDispatchModifierState)
                            modifierState = result.modifierState

                            // Mirror main-handler IME suppression
                            if (result.suppressImeInput) {
                                inputViewRef.value?.armInputSuppression(action.toString())
                            }

                            when (result.specialKey) {
                                TerminalSpecialKey.Left,
                                TerminalSpecialKey.Up -> {
                                    if (tabsForHost.isNotEmpty())
                                        focusedTabIndex =
                                            (focusedTabIndex - 1).mod(tabsForHost.size)
                                }
                                TerminalSpecialKey.Right,
                                TerminalSpecialKey.Down -> {
                                    if (tabsForHost.isNotEmpty())
                                        focusedTabIndex =
                                            (focusedTabIndex + 1).mod(tabsForHost.size)
                                }
                                TerminalSpecialKey.Enter -> {
                                    tabsForHost.getOrNull(focusedTabIndex)?.let {
                                        vm.selectTab(it.id)
                                        showTabSheet = false
                                    }
                                }
                                TerminalSpecialKey.Escape -> {
                                    showTabSheet = false
                                }
                                else -> {
                                    result.specialKey?.let { key ->
                                        vm.onSpecialKeyInput(
                                            key,
                                            preDispatchModifierState.terminalMods(),
                                        )
                                    }
                                    result.text?.let { text ->
                                        if (!chuchuKeys.handleText(text)) {
                                            vm.onTextInput(text)
                                        }
                                    }
                                }
                            }

                            // Preserve sticky modifiers: paste applies active modifiers
                            // but does not clear them.
                            if (result.shouldPaste) {
                                pasteClipboard()
                            }
                        }
                    }
                    CommandPalette(
                        tabs = tabsForHost,
                        activeTabId = activeTabId,
                        focusedTabIndex = focusedTabIndex,
                        onFocusedTabIndexChange = { focusedTabIndex = it },
                        accessoryEntries = accessoryLayout,
                        accessoryModifierState = modifierState,
                        onAccessoryAction = paletteAccessoryAction,
                        onChuchuKey = { chuchuKeys.togglePrefix() },
                        chuchuKeyActive = chuchuKeys.isPrefixActive,
                        onOpenFiles = {
                            if (filesSupported) {
                                vm.selectConnectionTab(ConnectionTab.Files)
                                showTabSheet = false
                            } else {
                                showLocalShellFilesUnsupported()
                            }
                        },
                        onOpenSettings = onOpenSettings,
                        useSingleRowAccessoryBar = useSingleRowAccessoryBar,
                        onSelectTab = {
                            vm.selectTab(it)
                            showTabSheet = false
                        },
                        onCloseTab = vm::closeTab,
                        onAddTab = {
                            vm.duplicateActiveTab()
                            showTabSheet = false
                        },
                        onDismiss = { showTabSheet = false },
                        multiplexerEnabled = activeTab?.spec?.usesRuntimeMultiplexer == true,
                        multiplexerSessions = multiplexerState.sessions,
                        multiplexerSessionsLoading = multiplexerState.sessionsLoading,
                        multiplexerSessionsError = multiplexerState.sessionsError,
                        onMultiplexerRefresh = vm::listMultiplexerSessionsForCurrentHost,
                        onMultiplexerNew = vm::createNextMultiplexerSession,
                        onMultiplexerAttach = { name ->
                            vm.switchToMultiplexerSession(name, multiplexerState.sessionsSourceTabId)
                        },
                    )
                }

            } else {
                if (tabMode == TerminalTabMode.Strip) {
                    Column(modifier = screenInsetsModifier.fillMaxSize()) {
                        TerminalTabStrip(
                            tabs = tabs,
                            activeTabId = activeTabId,
                            onTabSelected = { id -> vm.selectTab(id) },
                            onAddTab = openAnotherSessionForCurrentHost,
                            onOpenManager = { showGlobalTabManager = true },
                        )
                        Box(
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            contentAlignment = Alignment.Center,
                        ) {
                            val hostForMessage = activeTab?.spec?.tabLabel?.let { " $it" } ?: ""
                            val message =
                                if (isReconnecting) {
                                    "Reconnecting to${hostForMessage}..."
                                } else {
                                    "Preparing terminal..."
                                }
                            ChuText(message, style = typography.body)
                        }
                    }
                } else {
                    Column(
                        modifier = screenInsetsModifier.fillMaxSize().padding(16.dp),
                        verticalArrangement = Arrangement.Center,
                    ) {
                        val hostForMessage = activeTabForHost?.spec?.host?.let { " $it" } ?: ""
                        val message =
                            if (isReconnecting) {
                                "Reconnecting to${hostForMessage}..."
                            } else {
                                "Preparing terminal..."
                            }
                        ChuText(message, style = typography.body)
                    }
                }
            }
        }
    }

    BackHandler(enabled = showGlobalTabManager) { showGlobalTabManager = false }

    // Strip mode overlays — hoisted outside the when block so they are
    // available from disconnected, error, connecting, and connected states.
    if (tabMode == TerminalTabMode.Strip) {
        TerminalTabManager(
            visible = showGlobalTabManager,
            tabs = tabs,
            activeTabId = activeTabId,
            focusedTabIndex = focusedTabIndex,
            onFocusedTabIndexChange = { focusedTabIndex = it },
            onSelectTab = { id ->
                vm.selectTab(id)
                showGlobalTabManager = false
            },
            onCloseTab = { id ->
                vm.closeTab(id)
            },
            onDuplicateTab = { id ->
                vm.duplicateTab(id)
            },
            onAddTab = openAnotherSessionForCurrentHost,
            onDismiss = { showGlobalTabManager = false },
            multiplexerEnabled = activeTab?.spec?.usesRuntimeMultiplexer == true,
            multiplexerSessions = multiplexerState.sessions,
            multiplexerSessionsLoading = multiplexerState.sessionsLoading,
            multiplexerSessionsError = multiplexerState.sessionsError,
            onMultiplexerRefresh = vm::listMultiplexerSessionsForCurrentHost,
            onMultiplexerNew = vm::createNextMultiplexerSession,
            onMultiplexerAttach = { name ->
                vm.switchToMultiplexerSession(name, multiplexerState.sessionsSourceTabId)
            },
        )
    }
}

@Composable
private fun TerminalRecoveryActions(
    message: String,
    isMultiplexerPreflight: Boolean,
    dismissMultiplexerLabel: String = "back",
    onRetryMultiplexer: () -> Unit,
    onConnectWithoutMultiplexer: () -> Unit,
    onBack: () -> Unit,
    onReconnect: () -> Unit,
) {
    val colors = ChuColors.current
    val typography = ChuTypography.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        ChuText(message, color = colors.error, style = typography.body)
        Spacer(modifier = Modifier.height(16.dp))
        if (isMultiplexerPreflight) {
            ChuButton(
                onClick = onRetryMultiplexer,
                modifier = Modifier.fillMaxWidth(),
                variant = ChuButtonVariant.Filled,
            ) {
                ChuText("retry multiplexer", style = typography.label, color = colors.onAccent)
            }
            Spacer(modifier = Modifier.height(8.dp))
            ChuButton(
                onClick = onConnectWithoutMultiplexer,
                modifier = Modifier.fillMaxWidth(),
                variant = ChuButtonVariant.Outlined,
                bracketed = true,
            ) {
                ChuText("connect without multiplexer", style = typography.label)
            }
            Spacer(modifier = Modifier.height(8.dp))
            ChuButton(
                onClick = onBack,
                modifier = Modifier.fillMaxWidth(),
                variant = ChuButtonVariant.Ghost,
                bracketed = true,
                borderColor = colors.textMuted,
            ) {
                ChuText(dismissMultiplexerLabel, style = typography.label, color = colors.textMuted)
            }
        } else {
            ChuButton(
                onClick = onReconnect,
                modifier = Modifier.fillMaxWidth(),
                variant = ChuButtonVariant.Filled,
            ) {
                ChuText("Retry", style = typography.label, color = colors.onAccent)
            }
        }
    }
}

@Composable
private fun UploadProgressDialog(progress: UploadProgress) {
    val colors = ChuColors.current
    val typography = ChuTypography.current
    val barWidthDp = 240.dp
    val barHeightDp = 12.dp
    val filledWidth =
        if (progress.totalBytes > 0) {
            barWidthDp * progress.percent.coerceAtMost(100) / 100f
        } else {
            // Unknown total: show a thin sliver as indeterminate indicator
            barWidthDp * 0.15f
        }

    Box(
        modifier =
            Modifier.fillMaxSize().background(colors.background.copy(alpha = 0.7f)).clickable(
                enabled = false
            ) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier =
                Modifier.background(colors.surfaceVariant)
                    .border(1.dp, colors.border)
                    .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ChuText(
                "\u2500\u2500\u25B2\u2500\u2500",
                style = typography.title,
                color = colors.accent,
            )

            ChuText(text = progress.fileName, style = typography.body, color = colors.textPrimary)

            if (progress.totalFiles > 1) {
                ChuText(
                    text = "file ${progress.fileIndex + 1} of ${progress.totalFiles}",
                    style = typography.labelSmall,
                    color = colors.textMuted,
                )
            }

            Box(
                modifier =
                    Modifier.width(barWidthDp)
                        .height(barHeightDp)
                        .background(colors.surface)
                        .border(1.dp, colors.border),
                contentAlignment = Alignment.CenterStart,
            ) {
                Box(
                    modifier = Modifier.width(filledWidth).fillMaxHeight().background(colors.accent)
                )
            }

            ChuText(
                text =
                    if (progress.totalBytes > 0) {
                        val written = formatFileSize(progress.bytesWritten)
                        val total = formatFileSize(progress.totalBytes)
                        "$written / $total  ${progress.percent}%"
                    } else {
                        formatFileSize(progress.bytesWritten)
                    },
                style = typography.labelSmall,
                color = colors.textMuted,
            )
        }
    }
}
