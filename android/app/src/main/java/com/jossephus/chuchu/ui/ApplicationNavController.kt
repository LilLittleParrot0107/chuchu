package com.jossephus.chuchu.ui

import android.app.Application
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.flow.firstOrNull
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.jossephus.chuchu.data.repository.SettingsRepository
import com.jossephus.chuchu.ui.screens.AddServer.AddServerScreen
import com.jossephus.chuchu.ui.screens.AddServer.AddServerViewModel
import com.jossephus.chuchu.ui.screens.Dbtop.DbtopScreen
import com.jossephus.chuchu.ui.screens.Queue.QueueScreen
import com.jossephus.chuchu.ui.screens.Queue.QueueViewModel
import com.jossephus.chuchu.ui.screens.ServerList.ServerListScreen
import com.jossephus.chuchu.ui.screens.ServerList.ServerListViewModel
import com.jossephus.chuchu.ui.screens.Settings.SettingsBackupViewModel
import com.jossephus.chuchu.ui.screens.Settings.SettingsScreen
import com.jossephus.chuchu.ui.screens.Terminal.TerminalScreen
import com.jossephus.chuchu.ui.screens.Terminal.TerminalViewModel
import com.jossephus.chuchu.ui.security.VerificationResult
import com.jossephus.chuchu.ui.security.requireUserVerification
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.ui.unit.dp

@Composable
fun ApplicationNavController() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val lifecycleOwner = LocalLifecycleOwner.current
    var appUnlocked by rememberSaveable { mutableStateOf(false) }
    var unlockPromptRequested by rememberSaveable { mutableStateOf(false) }
    var appLockBlockedUntilToggle by rememberSaveable { mutableStateOf(false) }
    val settingsRepo = SettingsRepository.getInstance(application)
    val appLockEnabled by settingsRepo.appLockEnabled.collectAsStateWithLifecycle()

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { source, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                val isConfigChange =
                    (source as? android.app.Activity)?.isChangingConfigurations == true
                if (!isConfigChange) {
                    appUnlocked = false
                    unlockPromptRequested = false
                    appLockBlockedUntilToggle = false
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Telegram deep link consumer: when a /kohi-open?host=<name> link armed
    // the bus, look the profile up by name and jump straight to its terminal.
    val deepLinkHost by com.jossephus.chuchu.DeepLinkBus.pendingHostName.collectAsStateWithLifecycle()
    LaunchedEffect(deepLinkHost) {
        val wanted = deepLinkHost ?: return@LaunchedEffect
        val db = com.jossephus.chuchu.data.db.AppDatabase.getInstance(application)
        val list = com.jossephus.chuchu.data.repository.HostRepository(db.hostProfileDao())
            .observeAll()
            .firstOrNull()
        com.jossephus.chuchu.DeepLinkBus.pendingHostName.value = null
        if (list != null) {
            val match = list.firstOrNull { it.name.equals(wanted, ignoreCase = true) }
                ?: list.firstOrNull { it.host.equals(wanted, ignoreCase = true) }
            if (match != null) {
                navController.navigate("terminal/${match.id}")
            }
        }
    }

    // Khoa app phai CHE noi dung. Truoc day NavHost ve vo dieu kien va khoa chi
    // la BiometricPrompt noi len tren: bam Cancel -> appLockBlockedUntilToggle =
    // true -> prompt khong bao gio hien lai -> huy prompt LA DUNG DUOC app day du
    // (danh sach server, terminal, export khoa SSH).
    if (appLockEnabled && !appUnlocked) {
        LockedGate(
            blocked = appLockBlockedUntilToggle,
            onRetry = { appLockBlockedUntilToggle = false },
        )
        return
    }

    val sharedQueueVm: QueueViewModel = viewModel(factory = QueueViewModel.factory(application))
    val queueAmbientSummary by sharedQueueVm.ambientSummary.collectAsStateWithLifecycle()

    LifecycleResumeEffect(Unit) {
        sharedQueueVm.setAppActive(true)
        onPauseOrDispose { sharedQueueVm.setAppActive(false) }
    }

    NavHost(navController = navController, startDestination = "servers") {
        composable("servers") {
            val vm: ServerListViewModel = viewModel(factory = ServerListViewModel.factory(application))
            val settingsRepo = SettingsRepository.getInstance(application)
            val requireAuthOnConnect by settingsRepo.requireAuthOnConnect.collectAsStateWithLifecycle()
            val localShellEnabled by settingsRepo.localShellEnabled.collectAsStateWithLifecycle()
            val hosts by vm.hosts.collectAsStateWithLifecycle()
            val searchQuery by vm.search.collectAsStateWithLifecycle()
            ServerListScreen(
                hosts = hosts,
                searchQuery = searchQuery,
                onSearchChange = vm::updateSearchQuery,
                onAddServer = { navController.navigate("servers/add") },
                localShellEnabled = localShellEnabled,
                onOpenLocalShell = localShell@{
                    if (!localShellEnabled) return@localShell
                    if (!requireAuthOnConnect) {
                        navController.navigate("terminal/local")
                    } else {
                        requireUserVerification(
                            context = context,
                            title = "Verify to open local shell",
                            subtitle = "Authenticate to open this device shell",
                        ) { result ->
                            if (result == VerificationResult.Success) {
                                navController.navigate("terminal/local")
                            }
                        }
                    }
                },
                onEditServer = { id -> navController.navigate("servers/edit/$id") },
                onConnectServer = { id ->
                    val host = hosts.firstOrNull { it.id == id }
                    val hostRequiresAuth = host?.requireAuthOnConnect == true
                    val mustVerify = requireAuthOnConnect || hostRequiresAuth
                    if (!mustVerify) {
                        navController.navigate("terminal/$id")
                    } else {
                        requireUserVerification(
                            context = context,
                            title = "Verify to connect",
                            subtitle = "Authenticate to open this server session",
                        ) { result ->
                            if (result == VerificationResult.Success) {
                                navController.navigate("terminal/$id")
                            }
                        }
                    }
                },
                onDeleteServer = vm::deleteServer,
                onOpenSettings = { navController.navigate("settings") },
                onOpenWeb = { navController.navigate("web") },
                onOpenDashboard = { navController.navigate("dashboard") },
                onOpenQueue = { pane ->
                    navController.navigate(if (pane != null) "queue?pane=$pane" else "queue")
                },
                queueSummary = queueAmbientSummary,
            )
        }
        composable("dashboard") {
            DbtopScreen(
                onClose = { navController.popBackStack() },
            )
        }
        composable(
            route = "queue?pane={pane}",
            arguments = listOf(navArgument("pane") {
                type = NavType.StringType
                nullable = true
                defaultValue = null
            }),
        ) { backStackEntry ->
            val initialPane = backStackEntry.arguments?.getString("pane")
            val ui by sharedQueueVm.ui.collectAsStateWithLifecycle()
            val qUrl by sharedQueueVm.queueUrl.collectAsStateWithLifecycle()
            val qToken by sharedQueueVm.queueToken.collectAsStateWithLifecycle()
            DisposableEffect(sharedQueueVm) {
                sharedQueueVm.setQueueVisible(true)
                onDispose { sharedQueueVm.setQueueVisible(false) }
            }
            QueueScreen(
                ui = ui,
                initialPane = initialPane,
                onAction = sharedQueueVm::runAction,
                onAdd = sharedQueueVm::addTask,
                onClearDone = sharedQueueVm::clearDoneTasks,
                onRefresh = sharedQueueVm::refreshNow,
                onFetchLogs = sharedQueueVm::fetchLogs,
                onShowFeedback = sharedQueueVm::showFeedback,
                onConsumeFeedback = sharedQueueVm::consumeFeedback,
                currentUrl = qUrl,
                currentToken = qToken,
                onSaveConfig = sharedQueueVm::saveConfig,
                onFetchResponse = sharedQueueVm::loadTaskResponse,
                onBack = { navController.popBackStack() },
            )
        }

        composable("web") {
            val settingsRepo = SettingsRepository.getInstance(application)
            val webUrl by settingsRepo.webPortalUrl.collectAsStateWithLifecycle()
            com.jossephus.chuchu.ui.screens.Web.WebPortalScreen(
                url = webUrl,
                onClose = { navController.popBackStack() },
            )
        }
        composable("settings") {
            val settingsRepo = SettingsRepository.getInstance(application)
            val backupViewModel: SettingsBackupViewModel = viewModel(
                factory = SettingsBackupViewModel.factory(application),
            )
            val themeName by settingsRepo.themeName.collectAsStateWithLifecycle()
            val fontName by settingsRepo.fontName.collectAsStateWithLifecycle()
            val appLockEnabled by settingsRepo.appLockEnabled.collectAsStateWithLifecycle()
            val requireAuthOnConnect by settingsRepo.requireAuthOnConnect.collectAsStateWithLifecycle()
            val accessoryLayoutIds by settingsRepo.accessoryLayoutIds.collectAsStateWithLifecycle()
            val accessoryBarSingleRow by settingsRepo.accessoryBarSingleRow.collectAsStateWithLifecycle()
            val customKeyGroups by settingsRepo.terminalCustomKeyGroups.collectAsStateWithLifecycle()
            val showCustomActionsFab by settingsRepo.showCustomActionsFab.collectAsStateWithLifecycle()
            val showQueueFab by settingsRepo.showQueueFab.collectAsStateWithLifecycle()
            val builtinShortcuts by settingsRepo.builtinShortcuts.collectAsStateWithLifecycle()
            val tabMode by settingsRepo.terminalTabMode.collectAsStateWithLifecycle()
            val localShellEnabled by settingsRepo.localShellEnabled.collectAsStateWithLifecycle()
            val keepScreenAwake by settingsRepo.keepScreenAwake.collectAsStateWithLifecycle()
            val hideScreenContents by settingsRepo.hideScreenContents.collectAsStateWithLifecycle()
            val themeMode by settingsRepo.themeMode.collectAsStateWithLifecycle()
            val terminalFontSize by settingsRepo.terminalFontSize.collectAsStateWithLifecycle()
            val lightThemeName by settingsRepo.lightThemeName.collectAsStateWithLifecycle()
            SettingsScreen(
                currentTheme = themeName,
                currentFont = fontName,
                appLockEnabled = appLockEnabled,
                requireAuthOnConnect = requireAuthOnConnect,
                localShellEnabled = localShellEnabled,
                keepScreenAwake = keepScreenAwake,
                hideScreenContents = hideScreenContents,
                currentAccessoryLayoutIds = accessoryLayoutIds,
                accessoryBarSingleRow = accessoryBarSingleRow,
                currentTerminalCustomKeyGroups = customKeyGroups,
                showCustomActionsFab = showCustomActionsFab,
                onShowCustomActionsFabChanged = settingsRepo::setShowCustomActionsFab,
                showQueueFab = showQueueFab,
                onShowQueueFabChanged = settingsRepo::setShowQueueFab,
                builtinShortcuts = builtinShortcuts,
                onBuiltinShortcutsChanged = settingsRepo::setBuiltinShortcuts,
                currentTabMode = tabMode,
                onTabModeChanged = settingsRepo::setTerminalTabMode,
                themeMode = themeMode,
                lightThemeName = lightThemeName,
                onThemeSelected = settingsRepo::setTheme,
                onThemeModeChanged = settingsRepo::setThemeMode,
                onLightThemeSelected = settingsRepo::setLightTheme,
                onFontSelected = settingsRepo::setFont,
                onAppLockEnabledChanged = settingsRepo::setAppLockEnabled,
                onRequireAuthOnConnectChanged = settingsRepo::setRequireAuthOnConnect,
                onLocalShellEnabledChanged = settingsRepo::setLocalShellEnabled,
                onKeepScreenAwakeChanged = settingsRepo::setKeepScreenAwake,
                onHideScreenContentsChanged = settingsRepo::setHideScreenContents,
                onAccessoryLayoutChanged = settingsRepo::setAccessoryLayoutIds,
                onAccessoryBarSingleRowChanged = settingsRepo::setAccessoryBarSingleRow,
                currentTerminalFontSize = terminalFontSize,
                onTerminalFontSizeChanged = settingsRepo::setTerminalFontSize,
                onTerminalCustomActionsChanged = settingsRepo::setTerminalCustomKeyGroups,
                backupViewModel = backupViewModel,
                onBack = {
                    val currentRoute = navController.currentBackStackEntry?.destination?.route
                    if (currentRoute == "settings") {
                        navController.popBackStack()
                    }
                },
            )
        }
        composable("servers/add") {
            val vm: AddServerViewModel = viewModel(factory = AddServerViewModel.factory(application, null))
            AddServerScreen(
                vm = vm,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "servers/edit/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("id")
            val vm: AddServerViewModel = viewModel(factory = AddServerViewModel.factory(application, id))
            AddServerScreen(
                vm = vm,
                onBack = { navController.popBackStack() },
            )
        }
        composable("terminal/local") {
            val settingsRepo = SettingsRepository.getInstance(application)
            val localShellEnabled by settingsRepo.localShellEnabled.collectAsStateWithLifecycle()
            if (localShellEnabled) {
                val vm: TerminalViewModel = viewModel(factory = TerminalViewModel.factory(application))
                TerminalScreen(
                    vm = vm,
                    hostId = null,
                    openLocalShell = true,
                    queueSummary = queueAmbientSummary,
                    onQueueAction = sharedQueueVm::runAction,
                    onOpenSettings = { navController.navigate("settings") },
                    onOpenWeb = { navController.navigate("web") },
                    onOpenDashboard = { navController.navigate("dashboard") },
                    onOpenQueue = { pane ->
                        navController.navigate(if (pane != null) "queue?pane=$pane" else "queue")
                    },
                    onBack = { navController.popBackStack() },
                )
            } else {
                LaunchedEffect(Unit) {
                    val popped = navController.popBackStack("servers", inclusive = false)
                    if (!popped) {
                        navController.navigate("servers") { launchSingleTop = true }
                    }
                }
            }
        }
        composable(
            route = "terminal/{id}",
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong("id")
            val vm: TerminalViewModel = viewModel(factory = TerminalViewModel.factory(application))
            TerminalScreen(
                vm = vm,
                hostId = id,
                queueSummary = queueAmbientSummary,
                onQueueAction = sharedQueueVm::runAction,
                onOpenSettings = { navController.navigate("settings") },
                onOpenWeb = { navController.navigate("web") },
                onOpenDashboard = { navController.navigate("dashboard") },
                onOpenQueue = { pane ->
                    navController.navigate(if (pane != null) "queue?pane=$pane" else "queue")
                },
                onBack = { navController.popBackStack() },
            )
        }

    }

    if (!appLockEnabled) {
        appUnlocked = false
        unlockPromptRequested = false
        appLockBlockedUntilToggle = false
    }
    LaunchedEffect(appLockEnabled, appUnlocked, unlockPromptRequested, appLockBlockedUntilToggle) {
        if (appLockEnabled && !appUnlocked && !unlockPromptRequested && !appLockBlockedUntilToggle) {
            unlockPromptRequested = true
            requireUserVerification(
                context = context,
                title = "Unlock Chuchu",
                subtitle = "Authenticate to continue",
            ) { result ->
                appUnlocked = result == VerificationResult.Success
                if (result != VerificationResult.Success) {
                    // Chan tu dong hien lai (khong spam prompt), nhung nguoi dung
                    // van bam nut "Mo khoa" duoc. Truoc day co day khong co loi ra:
                    // huy mot lan la co khoa vinh vien... ma NavHost van ve, tuc la
                    // "khoa vinh vien" that ra = mo khoa vinh vien.
                    appLockBlockedUntilToggle = true
                }
                unlockPromptRequested = false
            }
        }
    }
}

@Composable
private fun LockedGate(blocked: Boolean, onRetry: () -> Unit) {
    val colors = com.jossephus.chuchu.ui.theme.ChuColors.current
    androidx.compose.foundation.layout.Box(
        modifier = androidx.compose.ui.Modifier
            .fillMaxSize()
            .background(colors.background),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
        ) {
            com.jossephus.chuchu.ui.components.ChuText("kohi đang khoá")
            if (blocked) {
                androidx.compose.foundation.layout.Spacer(
                    androidx.compose.ui.Modifier.height(12.dp),
                )
                com.jossephus.chuchu.ui.components.ChuButton(
                    onClick = onRetry,
                    bracketed = true,
                ) {
                    com.jossephus.chuchu.ui.components.ChuText(
                        "Mở khoá",
                        color = colors.onAccent,
                    )
                }
            }
        }
    }
}
