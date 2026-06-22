package com.protctus.app.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import com.protctus.app.navigation.Route
import com.protctus.app.viewmodel.MainActivityViewModel
import com.protctus.app.viewmodel.SettingsViewModel
import kotlinx.coroutines.flow.StateFlow

@Composable
fun SettingsSideEffects(mainViewModel: MainActivityViewModel) {
    val context = LocalContext.current
    val isRealTimeProtectionEnabled by mainViewModel.isRealTimeProtectionEnabled.collectAsState()
    val isPhishingShieldEnabled by mainViewModel.isPhishingShieldEnabled.collectAsState()
    val isJuiceJackingEnabled by mainViewModel.isJuiceJackingEnabled.collectAsState()
    val isUsbProtectionEnabled by mainViewModel.isUsbProtectionEnabled.collectAsState()
    val isGhostAppDetectorEnabled by mainViewModel.isGhostAppDetectorEnabled.collectAsState()
    val isInvisibleWatcherEnabled by mainViewModel.isInvisibleWatcherEnabled.collectAsState()
    val appLanguage by mainViewModel.appLanguage.collectAsState()

    // Anti-Screen Share / Invisible Watcher
    LaunchedEffect(isInvisibleWatcherEnabled) {
        val window = (context as? android.app.Activity)?.window
        if (window != null) {
            if (isInvisibleWatcherEnabled) {
                window.setFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE, android.view.WindowManager.LayoutParams.FLAG_SECURE)
            } else {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
    }

    // Language sync
    LaunchedEffect(appLanguage) {
        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
            androidx.core.os.LocaleListCompat.forLanguageTags(appLanguage)
        )
    }

    // Ghost App Detector
    LaunchedEffect(isGhostAppDetectorEnabled) {
        if (isGhostAppDetectorEnabled) {
            mainViewModel.runStalkerwareScan(context)
        }
    }

    // USB/Juice Jacking engine toggle
    LaunchedEffect(isJuiceJackingEnabled, isUsbProtectionEnabled) {
        if (isJuiceJackingEnabled || isUsbProtectionEnabled) {
            mainViewModel.initializeUsbEngine(context)
        } else {
            mainViewModel.deinitializeUsbEngine(context)
        }
    }

    // Real-time protection service
    LaunchedEffect(isRealTimeProtectionEnabled) {
        if (isRealTimeProtectionEnabled) {
            mainViewModel.startRealTimeProtection(context)
        }
    }

    // Phishing VPN
    val vpnLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            mainViewModel.startPhishingVpn(context)
        }
    }

    LaunchedEffect(isPhishingShieldEnabled) {
        if (isPhishingShieldEnabled) {
            val vpnIntent = android.net.VpnService.prepare(context)
            if (vpnIntent == null) {
                mainViewModel.startPhishingVpn(context)
            } else {
                try { vpnLauncher.launch(vpnIntent) } catch (e: Exception) { e.printStackTrace() }
            }
        }
    }
}

@Composable
fun DeepLinkRouter(
    navController: NavHostController,
    pendingSharedUrl: StateFlow<String?>,
    onSharedUrlHandled: () -> Unit,
    pendingThreatRoute: StateFlow<Boolean>,
    onThreatRouteHandled: () -> Unit
) {
    val sharedUrl by pendingSharedUrl.collectAsState()
    LaunchedEffect(sharedUrl) {
        sharedUrl?.let { url ->
            val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
            navController.navigate(Route.UrlScanner(encodedUrl))
            onSharedUrlHandled()
        }
    }

    val goThreatFeed by pendingThreatRoute.collectAsState()
    LaunchedEffect(goThreatFeed) {
        if (goThreatFeed) {
            navController.navigate(Route.ThreatFeed)
            onThreatRouteHandled()
        }
    }
}

@Composable
fun FirestoreNotificationListener() {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val settings by settingsViewModel.settings.collectAsState()

    LaunchedEffect(settings.notificationsEnabled) {
        val messaging = com.google.firebase.messaging.FirebaseMessaging.getInstance()
        if (settings.notificationsEnabled) {
            messaging.subscribeToTopic("global_alerts")
                .addOnSuccessListener { android.util.Log.d("FCM", "Subscribed to global_alerts") }
        } else {
            messaging.unsubscribeFromTopic("global_alerts")
                .addOnSuccessListener { android.util.Log.d("FCM", "Unsubscribed from global_alerts") }
        }
    }
}
