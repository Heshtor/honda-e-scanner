package cz.helicode.hondaescanner

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import cz.helicode.hondaescanner.data.prefs.AppIconManager
import cz.helicode.hondaescanner.data.prefs.SettingsRepository
import cz.helicode.hondaescanner.model.AppThemeMode
import cz.helicode.hondaescanner.model.ToastEvent
import cz.helicode.hondaescanner.ui.components.BrandedToastOverlay
import cz.helicode.hondaescanner.ui.screens.DashboardScreen
import cz.helicode.hondaescanner.ui.screens.DeviceSelectionScreen
import cz.helicode.hondaescanner.ui.theme.HondaEScannerTheme
import cz.helicode.hondaescanner.viewmodel.DeviceSelectionViewModel
import cz.helicode.hondaescanner.viewmodel.ScannerViewModel
import kotlinx.coroutines.flow.merge

class MainActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(SettingsRepository.wrapContextWithLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as HondaEApplication
        AppIconManager.applyTaskDescription(this, app.settingsRepository.loadSelectedIcon())

        setContent {
            val themeMode by app.settingsRepository.themeMode.collectAsStateWithLifecycle()
            val selectedIcon by app.settingsRepository.selectedIcon.collectAsStateWithLifecycle()

            val isDark = when (themeMode) {
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            // Sync system status/navigation bar icon colors with active theme
            LaunchedEffect(isDark) {
                val controller = WindowCompat.getInsetsController(window, window.decorView)
                controller.isAppearanceLightStatusBars = !isDark
                controller.isAppearanceLightNavigationBars = !isDark
            }

            // Keep recent apps task description icon synced
            LaunchedEffect(selectedIcon) {
                AppIconManager.applyTaskDescription(this@MainActivity, selectedIcon)
            }

            HondaEScannerTheme(themeMode = themeMode) {
                HondaEScannerApp(
                    themeMode = themeMode,
                    selectedIcon = selectedIcon,
                    onShareLogUri = { uri -> shareDiagnosticLog(uri) }
                )
            }
        }
    }

    private fun shareDiagnosticLog(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.share_chooser_title)))
    }

    private fun requiredPermissions(): Array<String> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            add(Manifest.permission.BLUETOOTH_CONNECT)
            add(Manifest.permission.BLUETOOTH_SCAN)
        }
        add(Manifest.permission.ACCESS_FINE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
    }.filter {
        ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
    }.toTypedArray()

    @Composable
    private fun HondaEScannerApp(
        themeMode: AppThemeMode,
        selectedIcon: cz.helicode.hondaescanner.model.AppIconColor,
        onShareLogUri: (Uri) -> Unit
    ) {
        val navController = rememberNavController()
        val deviceVm: DeviceSelectionViewModel = viewModel()
        val scannerVm: ScannerViewModel = viewModel()

        var activeToast by remember { mutableStateOf<ToastEvent?>(null) }

        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) {
            deviceVm.refreshPairedDevices()
            scannerVm.startLocationTracking()
        }

        LaunchedEffect(Unit) {
            val missing = requiredPermissions()
            if (missing.isNotEmpty()) {
                permissionLauncher.launch(missing)
            }
        }

        LaunchedEffect(deviceVm, scannerVm) {
            merge(deviceVm.toastEvents, scannerVm.toastEvents).collect { event ->
                activeToast = event
            }
        }

        val navigateToDashboard: (String, String, Boolean) -> Unit = { name, mac, isDemo ->
            val encName = Uri.encode(name)
            val encMac = Uri.encode(mac)
            navController.navigate("dashboard/$encName/$encMac/$isDemo") {
                launchSingleTop = true
            }
        }

        LaunchedEffect(deviceVm) {
            deviceVm.autoConnectEvent.collect { lastDevice ->
                if (navController.currentDestination?.route == "devices") {
                    navigateToDashboard(lastDevice.name, lastDevice.macAddress, false)
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            NavHost(
                navController = navController,
                startDestination = "devices"
            ) {
                composable("devices") {
                    val uiState by deviceVm.uiState.collectAsStateWithLifecycle()

                    DeviceSelectionScreen(
                        uiState = uiState,
                        onRefresh = {
                            val missing = requiredPermissions()
                            if (missing.isNotEmpty()) {
                                permissionLauncher.launch(missing)
                            }
                            deviceVm.refreshPairedDevices()
                        },
                        onDeviceClick = { device ->
                            deviceVm.onDeviceSelected(device)
                            navigateToDashboard(device.name, device.macAddress, false)
                        },
                        onStartDemoClick = {
                            navigateToDashboard("Honda e (Demo)", "DEMO_MODE", true)
                        }
                    )
                }

                composable(
                    route = "dashboard/{name}/{mac}/{isDemo}",
                    arguments = listOf(
                        navArgument("name") { type = NavType.StringType },
                        navArgument("mac") { type = NavType.StringType },
                        navArgument("isDemo") { type = NavType.BoolType }
                    )
                ) { backStackEntry ->
                    val name = backStackEntry.arguments?.getString("name").orEmpty()
                    val mac = backStackEntry.arguments?.getString("mac").orEmpty()
                    val isDemo = backStackEntry.arguments?.getBoolean("isDemo") ?: false

                    LaunchedEffect(name, mac, isDemo) {
                        scannerVm.initSession(deviceName = name, deviceMac = mac, startInDemo = isDemo)
                    }

                    val uiState by scannerVm.uiState.collectAsStateWithLifecycle()

                    DashboardScreen(
                        uiState = uiState,
                        themeMode = themeMode,
                        selectedIcon = selectedIcon,
                        onNavigateBack = {
                            scannerVm.cleanupSession()
                            navController.popBackStack()
                        },
                        onSohTestBackBlocked = scannerVm::notifySohTestBackBlocked,
                        onConnectActionClick = scannerVm::onConnectActionClicked,
                        onSelectTab = scannerVm::selectTab,
                        onShowCellsClick = scannerVm::onShowCellsClicked,
                        onDismissCellsDialog = scannerVm::dismissCellsDialog,
                        onShowModuleTempsClick = scannerVm::onShowModuleTempsClicked,
                        onDismissModuleTempsDialog = scannerVm::dismissModuleTempsDialog,
                        onToggleSohTest = scannerVm::onToggleSohTestClicked,
                        onToggleDemo = scannerVm::toggleDemoMode,
                        onShareLog = {
                            try {
                                val uri = scannerVm.prepareShareLogUri()
                                if (uri != null) {
                                    onShareLogUri(uri)
                                }
                            } catch (e: Exception) {
                                scannerVm.notifyShareLogError(e.message ?: "")
                            }
                        },
                        onSelectLanguage = { lang -> scannerVm.onLanguageSelected(lang, this@MainActivity) },
                        onSelectTheme = scannerVm::onThemeModeSelected,
                        onSelectAppIcon = scannerVm::onAppIconSelected
                    )
                }
            }

            BrandedToastOverlay(
                toastEvent = activeToast,
                selectedIcon = selectedIcon
            )
        }
    }
}
