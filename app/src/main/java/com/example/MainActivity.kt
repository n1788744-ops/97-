package com.example

import android.Manifest
import android.app.Activity
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.navigation.SecurityRoute
import com.example.ui.navigation.ServersRoute
import com.example.ui.navigation.TunnelRoute
import com.example.ui.screens.SecurityScreen
import com.example.ui.screens.ServersScreen
import com.example.ui.screens.TunnelScreen
import com.example.ui.theme.CipherGuardTheme
import com.example.ui.viewmodel.VpnViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CipherGuardTheme {
                CipherGuardApp()
            }
        }
    }
}

@Composable
fun CipherGuardApp(viewModel: VpnViewModel = viewModel()) {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val servers by viewModel.servers.collectAsStateWithLifecycle()
    val selectedServer by viewModel.selectedServer.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val tunnelStatus by viewModel.tunnelStatus.collectAsStateWithLifecycle()
    val tunnelMetrics by viewModel.tunnelMetrics.collectAsStateWithLifecycle()
    val tunnelLogs by viewModel.tunnelLogs.collectAsStateWithLifecycle()
    val preVpnIp by viewModel.preVpnIpInfo.collectAsStateWithLifecycle()
    val currentIp by viewModel.currentIpInfo.collectAsStateWithLifecycle()
    val isCheckingIp by viewModel.isCheckingIp.collectAsStateWithLifecycle()
    val ipError by viewModel.ipError.collectAsStateWithLifecycle()
    val hasVpnPermission by viewModel.hasVpnPermission.collectAsStateWithLifecycle()
    val hasNotificationPermission by viewModel.hasNotificationPermission.collectAsStateWithLifecycle()
    val pendingVpnConsentIntent by viewModel.pendingVpnConsentIntent.collectAsStateWithLifecycle()
    val cryptoAudit by viewModel.cryptoAudit.collectAsStateWithLifecycle()
    val installedApps by viewModel.installedApps.collectAsStateWithLifecycle()
    val uiMessage by viewModel.uiMessage.collectAsStateWithLifecycle()

    // System VPN consent launcher (VpnService.prepare)
    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.onVpnConsentResult(result.resultCode == Activity.RESULT_OK)
    }

    // Android 13+ Foreground VPN Notification runtime permission launcher
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.onNotificationPermissionResult()
    }

    // Zero-permission SAF .conf file picker
    val openConfLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importConfigUri(uri) {
                navController.navigate(ServersRoute) {
                    launchSingleTop = true
                }
            }
        }
    }

    LaunchedEffect(pendingVpnConsentIntent) {
        pendingVpnConsentIntent?.let { intent ->
            vpnPermissionLauncher.launch(intent)
        }
    }

    LaunchedEffect(uiMessage) {
        uiMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearMessage()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    val navTunnelLabel = stringResource(R.string.nav_tunnel)
    val navServersLabel = stringResource(R.string.nav_servers)
    val navSecurityLabel = stringResource(R.string.nav_security)

    val navigateTo: (Any) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val requestNotifications: () -> Unit = {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            contentWindowInsets = WindowInsets.safeDrawing,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                if (!useRail) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        NavigationBarItem(
                            selected = currentDestination?.hierarchy?.any { it.hasRoute<TunnelRoute>() } == true,
                            onClick = { navigateTo(TunnelRoute) },
                            icon = {
                                val selected = currentDestination?.hierarchy?.any { it.hasRoute<TunnelRoute>() } == true
                                Icon(
                                    if (selected) Icons.Rounded.Shield else Icons.Outlined.Shield,
                                    contentDescription = navTunnelLabel
                                )
                            },
                            label = { Text(navTunnelLabel) },
                            modifier = Modifier.testTag("nav_tunnel")
                        )
                        NavigationBarItem(
                            selected = currentDestination?.hierarchy?.any { it.hasRoute<ServersRoute>() } == true,
                            onClick = { navigateTo(ServersRoute) },
                            icon = {
                                val selected = currentDestination?.hierarchy?.any { it.hasRoute<ServersRoute>() } == true
                                Icon(
                                    if (selected) Icons.Rounded.Dns else Icons.Outlined.Dns,
                                    contentDescription = navServersLabel
                                )
                            },
                            label = { Text(navServersLabel) },
                            modifier = Modifier.testTag("nav_servers")
                        )
                        NavigationBarItem(
                            selected = currentDestination?.hierarchy?.any { it.hasRoute<SecurityRoute>() } == true,
                            onClick = { navigateTo(SecurityRoute) },
                            icon = {
                                val selected = currentDestination?.hierarchy?.any { it.hasRoute<SecurityRoute>() } == true
                                Icon(
                                    if (selected) Icons.Rounded.Security else Icons.Outlined.Security,
                                    contentDescription = navSecurityLabel
                                )
                            },
                            label = { Text(navSecurityLabel) },
                            modifier = Modifier.testTag("nav_security")
                        )
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useRail) {
                    NavigationRail(
                        modifier = Modifier.fillMaxHeight(),
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        NavigationRailItem(
                            selected = currentDestination?.hierarchy?.any { it.hasRoute<TunnelRoute>() } == true,
                            onClick = { navigateTo(TunnelRoute) },
                            icon = { Icon(Icons.Rounded.Shield, contentDescription = navTunnelLabel) },
                            label = { Text(navTunnelLabel) },
                            modifier = Modifier.testTag("rail_tunnel")
                        )
                        NavigationRailItem(
                            selected = currentDestination?.hierarchy?.any { it.hasRoute<ServersRoute>() } == true,
                            onClick = { navigateTo(ServersRoute) },
                            icon = { Icon(Icons.Rounded.Dns, contentDescription = navServersLabel) },
                            label = { Text(navServersLabel) },
                            modifier = Modifier.testTag("rail_servers")
                        )
                        NavigationRailItem(
                            selected = currentDestination?.hierarchy?.any { it.hasRoute<SecurityRoute>() } == true,
                            onClick = { navigateTo(SecurityRoute) },
                            icon = { Icon(Icons.Rounded.Security, contentDescription = navSecurityLabel) },
                            label = { Text(navSecurityLabel) },
                            modifier = Modifier.testTag("rail_security")
                        )
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = TunnelRoute,
                    modifier = Modifier.weight(1f)
                ) {
                    composable<TunnelRoute> {
                        TunnelScreen(
                            tunnelStatus = tunnelStatus,
                            tunnelMetrics = tunnelMetrics,
                            selectedServer = selectedServer,
                            preVpnIp = preVpnIp,
                            currentIp = currentIp,
                            isCheckingIp = isCheckingIp,
                            ipError = ipError,
                            hasVpnPermission = hasVpnPermission,
                            onToggleConnect = {
                                if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    requestNotifications()
                                }
                                viewModel.toggleTunnelConnection()
                            },
                            onRequestVpnPermission = { viewModel.requestSystemVpnPermission() },
                            onRefreshIp = { viewModel.refreshPublicIp(isInitial = false) },
                            onNavigateToServers = { navigateTo(ServersRoute) },
                            onQuickImportConf = { openConfLauncher.launch(arrayOf("*/*")) }
                        )
                    }

                    composable<ServersRoute> {
                        ServersScreen(
                            servers = servers,
                            selectedServerId = selectedServer?.id,
                            tunnelStatus = tunnelStatus,
                            onSelectServer = { viewModel.selectServer(it) },
                            onConnectServer = { id ->
                                if (!hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    requestNotifications()
                                }
                                viewModel.connectToServer(id)
                                navigateTo(TunnelRoute)
                            },
                            onDeleteServer = { viewModel.deleteServer(it) },
                            onPingServer = { viewModel.pingServer(it) },
                            onSaveServer = { profile, onDone -> viewModel.saveServerProfile(profile, onDone) },
                            onImportFileClick = { openConfLauncher.launch(arrayOf("*/*")) },
                            onImportPaste = { raw, name, mode, onDone ->
                                viewModel.importConfigText(raw, name, mode, onDone)
                            },
                            onGenerateKeyPair = { viewModel.generateKeyPair() },
                            onShowMessage = { msg ->
                                scope.launch { snackbarHostState.showSnackbar(msg) }
                            }
                        )
                    }

                    composable<SecurityRoute> {
                        SecurityScreen(
                            hasVpnPermission = hasVpnPermission,
                            hasNotificationPermission = hasNotificationPermission,
                            settings = settings,
                            cryptoAudit = cryptoAudit,
                            installedApps = installedApps,
                            logs = tunnelLogs,
                            onRequestVpnPermission = { viewModel.requestSystemVpnPermission() },
                            onRequestNotificationPermission = requestNotifications,
                            onUpdateCustomDns = { enabled, dns -> viewModel.updateCustomDns(enabled, dns) },
                            onLoadInstalledApps = { viewModel.loadInstalledApps() },
                            onToggleDisallowedPackage = { pkg -> viewModel.toggleDisallowedPackage(pkg) },
                            onRunCryptoAudit = { viewModel.runCryptoAudit() },
                            onClearLogs = { viewModel.clearLogs() }
                        )
                    }
                }
            }
        }
    }
}
