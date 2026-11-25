package cz.helicode.hondaescanner.ui.screens

import android.app.Activity
import android.content.Intent
import android.view.WindowManager
import androidx.core.net.toUri
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cz.helicode.hondaescanner.R
import cz.helicode.hondaescanner.model.AppIconColor
import cz.helicode.hondaescanner.model.AppLanguage
import cz.helicode.hondaescanner.model.AppThemeMode
import cz.helicode.hondaescanner.model.ConnectionStatus
import cz.helicode.hondaescanner.model.StatusMessage
import cz.helicode.hondaescanner.ui.components.CellVoltagesDialog
import cz.helicode.hondaescanner.ui.components.ModuleTempsDialog
import cz.helicode.hondaescanner.ui.theme.HondaTheme
import cz.helicode.hondaescanner.viewmodel.DashboardTab
import cz.helicode.hondaescanner.viewmodel.ScannerUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    uiState: ScannerUiState,
    themeMode: AppThemeMode,
    selectedIcon: AppIconColor,
    onNavigateBack: () -> Unit,
    onSohTestBackBlocked: () -> Unit,
    onConnectActionClick: () -> Unit,
    onSelectTab: (DashboardTab) -> Unit,
    onShowCellsClick: () -> Unit,
    onDismissCellsDialog: () -> Unit,
    onShowModuleTempsClick: () -> Unit,
    onDismissModuleTempsDialog: () -> Unit,
    onToggleSohTest: () -> Unit,
    onToggleDemo: () -> Unit,
    onShareLog: () -> Unit,
    onSelectLanguage: (AppLanguage) -> Unit,
    onSelectTheme: (AppThemeMode) -> Unit,
    onSelectAppIcon: (AppIconColor) -> Unit
) {
    val colors = HondaTheme.colors
    val context = LocalContext.current

    // Keep screen awake while monitoring vehicle telemetry
    DisposableEffect(Unit) {
        val window = (context as? Activity)?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    val handleBack: () -> Unit = {
        if (uiState.sohTest.isRunning) {
            onSohTestBackBlocked()
        } else {
            onNavigateBack()
        }
    }

    BackHandler(onBack = handleBack)

    Scaffold(
        containerColor = colors.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.device_name_format, uiState.deviceName),
                        color = colors.textPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = handleBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = null,
                            tint = colors.textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.navBg
                )
            )
        },
        bottomBar = {
            Column {
                HorizontalDivider(thickness = 0.5.dp, color = colors.navDivider)
                NavigationBar(
                    containerColor = colors.navBg,
                    tonalElevation = 0.dp
                ) {
                    val itemColors = NavigationBarItemDefaults.colors(
                        selectedIconColor = colors.accent,
                        selectedTextColor = colors.accent,
                        indicatorColor = colors.highlight,
                        unselectedIconColor = colors.textSecondary,
                        unselectedTextColor = colors.textSecondary
                    )

                    NavigationBarItem(
                        selected = uiState.selectedTab == DashboardTab.OVERVIEW,
                        onClick = { onSelectTab(DashboardTab.OVERVIEW) },
                        icon = { Icon(Icons.Rounded.Dashboard, contentDescription = null) },
                        label = {
                            Text(
                                text = stringResource(R.string.tab_overview),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = itemColors
                    )
                    NavigationBarItem(
                        selected = uiState.selectedTab == DashboardTab.SOH_TEST,
                        onClick = { onSelectTab(DashboardTab.SOH_TEST) },
                        icon = { Icon(Icons.Rounded.BatteryChargingFull, contentDescription = null) },
                        label = {
                            Text(
                                text = stringResource(R.string.tab_soh_test),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = itemColors
                    )
                    NavigationBarItem(
                        selected = uiState.selectedTab == DashboardTab.SETTINGS,
                        onClick = { onSelectTab(DashboardTab.SETTINGS) },
                        icon = { Icon(Icons.Rounded.Settings, contentDescription = null) },
                        label = {
                            Text(
                                text = stringResource(R.string.tab_settings),
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        colors = itemColors
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Sticky Connection Header Card
            ConnectionStatusHeader(
                uiState = uiState,
                onConnectActionClick = onConnectActionClick
            )

            // Active Tab Content
            Box(modifier = Modifier.weight(1f)) {
                when (uiState.selectedTab) {
                    DashboardTab.OVERVIEW -> OverviewTab(
                        uiState = uiState,
                        onShowCellsClick = onShowCellsClick,
                        onShowModuleTempsClick = onShowModuleTempsClick
                    )
                    DashboardTab.SOH_TEST -> SohTestTab(
                        uiState = uiState,
                        onToggleSohTest = onToggleSohTest
                    )
                    DashboardTab.SETTINGS -> SettingsTab(
                        uiState = uiState,
                        themeMode = themeMode,
                        selectedIcon = selectedIcon,
                        onToggleDemo = onToggleDemo,
                        onShareLog = onShareLog,
                        onSelectLanguage = onSelectLanguage,
                        onSelectTheme = onSelectTheme,
                        onSelectAppIcon = onSelectAppIcon,
                        onOpenWebsite = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, "https://helicode.cz".toUri()))
                            }
                        },
                        onOpenGitHub = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, "https://github.com/Heshtor/honda-e-scanner".toUri())
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    if (uiState.showCellsDialog) {
        CellVoltagesDialog(
            telemetry = uiState.telemetry,
            onDismiss = onDismissCellsDialog
        )
    }

    if (uiState.showModuleTempsDialog) {
        ModuleTempsDialog(
            telemetry = uiState.telemetry,
            onDismiss = onDismissModuleTempsDialog
        )
    }
}

@Composable
private fun ConnectionStatusHeader(
    uiState: ScannerUiState,
    onConnectActionClick: () -> Unit
) {
    val colors = HondaTheme.colors
    val statusText = when {
        uiState.isDemoMode -> stringResource(R.string.status_demo)
        uiState.connectionStatus == ConnectionStatus.CONNECTED -> stringResource(R.string.status_connected)
        uiState.connectionStatus == ConnectionStatus.CONNECTING -> stringResource(R.string.status_connecting)
        else -> stringResource(R.string.status_disconnected)
    }

    val buttonText = when (uiState.connectionStatus) {
        ConnectionStatus.CONNECTED -> stringResource(R.string.disconnect)
        ConnectionStatus.CONNECTING -> stringResource(R.string.stop)
        ConnectionStatus.DISCONNECTED -> stringResource(R.string.connect)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = colors.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = statusText,
                    color = if (uiState.carConnected || uiState.isDemoMode) colors.accent else colors.textPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Button(
                    onClick = onConnectActionClick,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.onAccent
                    )
                ) {
                    Text(
                        text = buttonText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            val messageText = when (val msg = uiState.statusMessage) {
                StatusMessage.Idle -> stringResource(R.string.messages)
                StatusMessage.CanConnecting -> stringResource(R.string.can_connecting)
                is StatusMessage.CanWaiting -> stringResource(R.string.can_waiting, msg.intervalMs)
                is StatusMessage.Updated -> stringResource(R.string.update_time_format, msg.timeStr)
                is StatusMessage.UpdatedDemo -> stringResource(R.string.update_time_demo_format, msg.timeStr)
                is StatusMessage.CanError -> stringResource(
                    R.string.can_error,
                    msg.errorDetail ?: stringResource(R.string.unknown_error)
                )
            }

            Text(
                text = messageText,
                color = colors.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
