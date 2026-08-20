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
import com.jossephus.chuchu.ui.screens.ServerList.ServerListScreen
import com.jossephus.chuchu.ui.screens.ServerList.ServerListViewModel
import com.jossephus.chuchu.ui.screens.Settings.SettingsBackupViewModel
import com.jossephus.chuchu.ui.screens.Settings.SettingsScreen
import com.jossephus.chuchu.ui.screens.Terminal.TerminalScreen
import com.jossephus.chuchu.ui.screens.Terminal.TerminalViewModel
import com.jossephus.chuchu.ui.security.VerificationResult
import com.jossephus.chuchu.ui.security.requireUserVerification

@Composable
fun ApplicationNavController() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val lifecycleOwner = LocalLifecycleOwner.current
    var appUnlocked by rememberSaveable { mutableStateOf(false) }
    var unlockPromptRequested by rememberSaveable { mutableStateOf(false) }
    var appLockBlockedUntilToggle by rememberSaveable { mutableStateOf(false) }

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
                onOpenQueue = { navController.navigate("queue") },
            )
        }
        composable("queue") {
            val vm: com.jossephus.chuchu.ui.screens.Queue.QueueViewModel =
                viewModel(factory = com.jossephus.chuchu.ui.screens.Queue.QueueViewModel.factory(application))
            val ui by vm.ui.collectAsStateWithLifecycle()
            val qUrl by vm.queueUrl.collectAsStateWithLifecycle()
            val qToken by vm.queueToken.collectAsStateWithLifecycle()
            // Poll chi chay khi man hinh dang hien: roi nen la ngung han, khong
            // de mot vong 2 giay chay ngam an pin va giu song WiFi.
            androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
                vm.startPolling()
                onPauseOrDispose { vm.stopPolling() }
            }
            com.jossephus.chuchu.ui.screens.Queue.QueueScreen(
                ui = ui,
                onAction = vm::runAction,
                onAdd = vm::addTask,
                onRefresh = vm::refreshNow,
                currentUrl = qUrl,
                currentToken = qToken,
                onSaveConfig = vm::saveConfig,
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
                    onOpenSettings = { navController.navigate("settings") },
                    onOpenWeb = { navController.navigate("web") },
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
                onOpenSettings = { navController.navigate("settings") },
                onOpenWeb = { navController.navigate("web") },
                onBack = { navController.popBackStack() },
            )
        }
    }

    val settingsRepo = SettingsRepository.getInstance(application)
    val appLockEnabled by settingsRepo.appLockEnabled.collectAsStateWithLifecycle()
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
                    appLockBlockedUntilToggle = true
                }
                unlockPromptRequested = false
            }
        }
    }
}
