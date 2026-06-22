package com.protctus.app

import android.os.Bundle
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import android.content.Intent
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import com.protctus.app.navigation.Route
import com.protctus.app.ui.screens.auth.*
import com.protctus.app.ui.screens.dashboard.HomeScreen
import com.protctus.app.ui.screens.dashboard.RewardsScreen
import com.protctus.app.ui.screens.features.*
import com.protctus.app.ui.screens.settings.*
import com.protctus.app.ui.screens.scanner.*
import com.protctus.app.ui.theme.ProtctusTheme
import com.protctus.app.viewmodel.AuthViewModel
import com.protctus.app.viewmodel.MainActivityViewModel
import com.protctus.app.viewmodel.QuestIntegrationViewModel
import com.protctus.app.viewmodel.ScannerViewModel
import com.protctus.app.viewmodel.PremiumViewModel
import com.protctus.app.utils.UpdateManager
import com.protctus.app.utils.AppVersionResponse
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), com.razorpay.PaymentResultWithDataListener {

    private val pendingSharedUrl = MutableStateFlow<String?>(null)
    private val pendingThreatRoute = MutableStateFlow<Boolean>(false)
    private val vpnBlockedUrl = MutableStateFlow<String?>(null)
    private val triggerUpdateCheck = MutableStateFlow<Boolean>(false)

    // Activity-scoped PremiumViewModel so Razorpay callbacks can reach it
    private val premiumViewModel: PremiumViewModel by viewModels()

    // Injected EntitlementManager — the single source of truth for tier
    @javax.inject.Inject
    lateinit var entitlementManager: com.protctus.app.data.repository.EntitlementManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        if (BuildConfig.DEBUG) {
            android.os.StrictMode.setThreadPolicy(
                android.os.StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build() // Do not use penaltyDeath initially to avoid immediate dev crashes, just log
            )
        }
        
        // ── 1. UNLOCK DEVICE IF BACKGROUND BLOCK TRIGGERED ──
        if (intent?.action == "com.protctus.app.ACTION_SHOW_BLOCK_SCREEN") {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                setShowWhenLocked(true)
                setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION")
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        android.view.WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            }
        }
        
        handleIntent(intent)

        // ── One-time initialization ──
        com.razorpay.Checkout.preload(applicationContext)
        registerWorkers()
        createNotificationChannel()
        com.protctus.app.engine.SignatureDatabase.initialize(this)

        enableEdgeToEdge()

        setContent {
            ProtctusTheme {
                val navController = rememberNavController()
                val mainViewModel: MainActivityViewModel = hiltViewModel()
                val updateManager = remember { UpdateManager(this@MainActivity) }
                val updateInfo = remember { mutableStateOf<AppVersionResponse?>(null) }
                val forceCheck by triggerUpdateCheck.collectAsState()

                // Observe manual check from Settings
                LaunchedEffect(Unit) {
                    mainViewModel.manualUpdateCheck.collect {
                        updateManager.checkForUpdates(showToastIfUpToDate = true) { result ->
                            if (result is UpdateManager.UpdateCheckResult.Available) {
                                updateInfo.value = result.info
                            }
                        }
                    }
                }

                // Check for updates on launch OR when notification clicked
                LaunchedEffect(Unit, forceCheck) {
                    updateManager.checkForUpdates(showToastIfUpToDate = false) { result ->
                        if (result is UpdateManager.UpdateCheckResult.Available) {
                            updateInfo.value = result.info
                        }
                    }
                    if (forceCheck) triggerUpdateCheck.value = false
                }

                // Update Screen (Premium UI)
                updateInfo.value?.let { info ->
                    com.protctus.app.ui.screens.features.UpdatePromptScreen(
                        info = info,
                        onUpdate = {
                            updateManager.downloadAndInstall(info.download_url, info.version_name ?: info.version_code.toString())
                            if (info.is_critical == 0) updateInfo.value = null
                        },
                        onDismiss = {
                            updateInfo.value = null
                        }
                    )
                }

                // ── 2. REACTIVE THREAT BUS OBSERVER (Foreground) ──
                LaunchedEffect(Unit) {
                    com.protctus.app.engine.ThreatEventBus.threatEvents.collect { event ->
                        val encodedUrl = java.net.URLEncoder.encode(event.url, "UTF-8")
                        navController.navigate(Route.UrlScanner(encodedUrl)) {
                            // Ensure the user can't "Back" into the malicious site
                            popUpTo(navController.graph.startDestinationId) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                }

                // ── 3. INTENT-BASED ROUTING (Background/Lockscreen) ──
                val blockedUrl by vpnBlockedUrl.collectAsState()
                LaunchedEffect(blockedUrl) {
                    blockedUrl?.let { url ->
                        navController.navigate(Route.ThreatResolution(
                            packageName = "NETWORK_SHIELD",
                            title = "Phishing Site Blocked",
                            description = "Protctus intercepted a request to: $url"
                        )) {
                            popUpTo(navController.graph.startDestinationId) { inclusive = false }
                            launchSingleTop = true
                        }
                        vpnBlockedUrl.value = null
                    }
                }

                com.protctus.app.ui.SettingsSideEffects(mainViewModel)
                com.protctus.app.ui.DeepLinkRouter(
                    navController = navController,
                    pendingSharedUrl = pendingSharedUrl,
                    onSharedUrlHandled = { pendingSharedUrl.value = null },
                    pendingThreatRoute = pendingThreatRoute,
                    onThreatRouteHandled = { pendingThreatRoute.value = false }
                )
                com.protctus.app.ui.FirestoreNotificationListener()

                val context = LocalContext.current
                LaunchedEffect(Unit) {
                    mainViewModel.runNativeSecurityCheck(context)
                }

                DisposableEffect(Unit) {
                    val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
                    val listener = com.google.firebase.auth.FirebaseAuth.AuthStateListener {
                        if (it.currentUser != null) {
                            try {
                                com.protctus.app.utils.DeviceRegistrar.registerDevice(context)
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                    auth.addAuthStateListener(listener)
                    onDispose { auth.removeAuthStateListener(listener) }
                }

                // ── DYNAMIC GLOBAL QUEST INTERCEPTOR ──
                // Automatically mark quests as completed when a user navigates to the relevant screen dynamically.
                val activeQuest by com.protctus.app.utils.GlobalQuestManager.activeQuest.collectAsState()

                LaunchedEffect(navController, activeQuest) {
                    val quest = activeQuest ?: return@LaunchedEffect
                    navController.currentBackStackEntryFlow.collect { entry ->
                        val routeStr = entry.destination.route ?: return@collect
                        val normalizedRoute = routeStr.substringAfterLast(".").substringBefore("/").lowercase().replace(Regex("[^a-z0-9]"), "")
                        val normalizedTrigger = quest.actionTrigger.lowercase().replace(Regex("[^a-z0-9]"), "")
                        
                        // Dynamic Fuzzy Matcher: If the screen name resembles the quest action, mark it complete!
                        if (normalizedRoute == normalizedTrigger || 
                            normalizedRoute.contains(normalizedTrigger) || 
                            normalizedTrigger.contains(normalizedRoute)) {
                            
                            // Delay slightly to let the screen fully render before marking it done
                            kotlinx.coroutines.delay(1000)
                            com.protctus.app.utils.QuestTracker.markTaskCompleted(context, quest.actionTrigger)
                        }
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                        ProtctusNavGraph(
                            navController = navController,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }

                    // ── DYNAMIC QUEST GUIDE OVERLAY ──
                    com.protctus.app.ui.components.QuestGuideOverlay(
                        activeQuest = activeQuest,
                        onDismiss = { com.protctus.app.utils.GlobalQuestManager.setActiveQuest(null) }
                    )
                }
            }
        }
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.getStringExtra("type") == "APP_UPDATE") {
            triggerUpdateCheck.value = true
        }

        if (intent.action == "com.protctus.app.ACTION_SHOW_BLOCK_SCREEN") {
            vpnBlockedUrl.value = intent.getStringExtra("domain")
        }
        
        if (intent.getBooleanExtra("NAVIGATE_TO_THREAT_FEED", false)) {
            pendingThreatRoute.value = true
        }

        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            sharedText?.let {
                val urlToScan = extractUrlFromText(it)
                if (urlToScan != null) {
                    pendingSharedUrl.value = urlToScan
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════
    //  COMPOSABLE SIDE-EFFECT MODULES (extracted from God Activity)
    // ═══════════════════════════════════════════════════



    // ═══════════════════════════════════════════════════
    //  NAVIGATION GRAPH (extracted for readability)
    // ═══════════════════════════════════════════════════

    @Composable
    private fun ProtctusNavGraph(navController: NavHostController, modifier: Modifier) {
        // Activity-scoped ViewModels — shared across multiple screens in the auth flow
        val authViewModel: AuthViewModel = hiltViewModel()
        val scannerViewModel: ScannerViewModel = hiltViewModel()
        // Always-alive: collects GlobalQuestTracker events → Room DB XP
        val _questIntegration: QuestIntegrationViewModel = hiltViewModel()

        NavHost(
            navController = navController,
            startDestination = Route.Splash,
            modifier = modifier,
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(300))
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Left,
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + androidx.compose.animation.scaleOut(
                    targetScale = 0.95f,
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                )
            },
            popEnterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + androidx.compose.animation.scaleIn(
                    initialScale = 0.95f,
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                )
            },
            popExitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Right,
                    animationSpec = tween(300, easing = FastOutSlowInEasing)
                ) + fadeOut(animationSpec = tween(300))
            }
        ) {
            // ── Auth Flow ──
            composable<Route.Splash> { SplashRouter(navController = navController) }
            composable<Route.BiometricUnlock> { BiometricUnlockScreen(navController) }
            composable<Route.Walkthrough> { WalkthroughScreen(navController = navController) }
            composable<Route.Onboarding> { OnboardingScreen(navController = navController) }
            composable<Route.SignIn> { SignInScreen(navController = navController, authViewModel = authViewModel) }
            composable<Route.SignUp> { SignUpScreen(navController = navController, authViewModel = authViewModel) }
            composable<Route.Verification> { VerificationScreen(navController = navController, authViewModel = authViewModel) }
            composable<Route.SetPassword> { SetPasswordScreen(navController = navController, authViewModel = authViewModel) }
            composable<Route.Fingerprint> { FingerprintScreen(navController = navController) }
            composable<Route.Username> { UsernameScreen(navController = navController, authViewModel = authViewModel) }
            composable<Route.PlanSelection> { PlanSelectionScreen(navController = navController) }
            composable<Route.PaymentSuccess> { PaymentSuccessScreen(navController = navController) }

            // ── Dashboard ──
            composable<Route.Dashboard> { HomeScreen(navController = navController) }
            composable<Route.Rewards> { RewardsScreen(navController = navController) }
            composable(
                route = "rank_detail/{rankId}",
                arguments = listOf(
                    androidx.navigation.navArgument("rankId") { type = androidx.navigation.NavType.IntType }
                )
            ) { backStackEntry ->
                val rankId = backStackEntry.arguments?.getInt("rankId") ?: 1
                com.protctus.app.ui.screens.dashboard.RankDefendScreen(
                    navController = navController,
                    targetRankId = rankId
                )
            }

            // ── Features ──
            composable<Route.Search> { SearchScreen(navController = navController) }
            composable<Route.ThreatFeed> { ThreatFeedScreen(navController = navController) }
            composable<Route.DailyQuests> { DailyQuestsScreen(navController = navController) }
            composable<Route.ThreatMap> { ThreatMapScreen(navController = navController) }
            composable<Route.FeaturesShortcut> { FeaturesShortcutScreen(navController = navController) }
            composable<Route.LabChapters> { com.protctus.app.ui.screens.features.LabChaptersScreen(navController = navController) }
            composable<Route.MicrolearningFeed> {
                com.protctus.app.ui.screens.features.MicrolearningScreen(navController = navController)
            }
            composable<Route.GetPremium> { GetPremiumScreen(navController = navController) }

            // ── Scanner ──
            composable<Route.SmartScan> { SmartScanScreen(navController = navController, scannerViewModel = scannerViewModel) }
            composable<Route.ScanResults> { ScanResultsScreen(navController = navController, scannerViewModel = scannerViewModel) }
            composable<Route.DarkWebScan> { DarkWebScanScreen(navController = navController) }
            composable<Route.DeepScan> { DeepScanScreen(navController = navController) }

            // ── Settings ──
            composable<Route.Settings> { SettingsScreen(navController = navController) }
            composable<Route.EditProfile> { EditProfileScreen(navController = navController) }
            composable<Route.AccountSecurity> { AccountSecurityScreen(navController = navController) }
            composable<Route.Preferences> { PreferencesScreen(navController = navController) }
            composable<Route.HelpCenter> { ContactUsScreen(navController = navController) }
            composable<Route.TermsOfService> { TermsOfServiceScreen(navController = navController) }
            composable<Route.PrivacyPolicy> { PrivacyPolicyScreen(navController = navController) }
            composable<Route.AdvanceSettings> { com.protctus.app.ui.screens.settings.AdvanceSettingsScreen(navController = navController) }

            // ── Social ──
            composable<Route.Leaderboard> { com.protctus.app.ui.screens.leaderboard.LeaderboardScreen(navController = navController) }
            composable<Route.Notifications> { com.protctus.app.ui.screens.notifications.NotificationsScreen(navController = navController) }
            composable<Route.Referral> { com.protctus.app.ui.screens.referral.ReferralScreen(navController = navController) }

            // ── Auth Recovery ──
            composable<Route.ResetPassword> { com.protctus.app.ui.screens.auth.ResetPasswordScreen(navController = navController) }
            composable<Route.NewPassword> { com.protctus.app.ui.screens.auth.NewPasswordScreen(navController = navController) }
            composable<Route.ChangeEmail> { com.protctus.app.ui.screens.auth.ChangeEmailScreen(navController = navController) }
            composable<Route.EmailVerification> { com.protctus.app.ui.screens.auth.EmailVerificationScreen(navController = navController) }

            // ── Dynamic Routes ──
            composable<Route.UrlScanner> { backStackEntry ->
                val route = backStackEntry.toRoute<Route.UrlScanner>()
                UrlScannerScreen(sharedUrl = route.url)
            }
            composable<Route.ThreatResolution> { backStackEntry ->
                val route = backStackEntry.toRoute<Route.ThreatResolution>()
                ThreatResolutionScreen(
                    navController = navController,
                    packageName = route.packageName,
                    appName = route.title,
                    reason = route.description
                )
            }
        }
    }

    // ═══════════════════════════════════════════════════
    //  PRIVATE HELPERS
    // ═══════════════════════════════════════════════════

    private fun registerWorkers() {
        val workRequest = androidx.work.PeriodicWorkRequestBuilder<com.protctus.app.worker.DailyNotificationWorker>(
            8, java.util.concurrent.TimeUnit.HOURS
        ).setInitialDelay(1, java.util.concurrent.TimeUnit.HOURS).build()

        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "protctus_daily_notifications",
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )

        val postureScanRequest = androidx.work.PeriodicWorkRequestBuilder<com.protctus.app.worker.PeriodicScanWorker>(
            24, java.util.concurrent.TimeUnit.HOURS
        ).setInitialDelay(2, java.util.concurrent.TimeUnit.HOURS).build()

        androidx.work.WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            com.protctus.app.worker.PeriodicScanWorker.WORK_NAME,
            androidx.work.ExistingPeriodicWorkPolicy.KEEP,
            postureScanRequest
        )
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = android.app.NotificationChannel(
                com.protctus.app.worker.DailyNotificationWorker.CHANNEL_ID,
                com.protctus.app.worker.DailyNotificationWorker.CHANNEL_NAME,
                android.app.NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Daily security alerts and quest reminders from Protctus"
            }
            val manager = getSystemService(android.app.NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun sendLocalNotification(context: android.content.Context, title: String, message: String) {
        try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                androidx.core.content.ContextCompat.checkSelfPermission(
                    context, android.Manifest.permission.POST_NOTIFICATIONS
                ) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                return
            }

            val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            val pIntent = android.app.PendingIntent.getActivity(
                context, 0, launchIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )

            val notif = androidx.core.app.NotificationCompat.Builder(
                context, com.protctus.app.worker.DailyNotificationWorker.CHANNEL_ID
            )
                .setSmallIcon(R.drawable.smart_scan)
                .setContentTitle(title)
                .setContentText(message)
                .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pIntent)
                .setAutoCancel(true)
                .build()

            androidx.core.app.NotificationManagerCompat.from(context)
                .notify(System.currentTimeMillis().toInt(), notif)
        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    override fun onResume() {
        super.onResume()
        // Sync tier with server on every resume — catches Admin Grants and background changes
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            entitlementManager.syncWithServer()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun extractUrlFromText(text: String): String? {
        val matcher = android.util.Patterns.WEB_URL.matcher(text)
        return if (matcher.find()) matcher.group() else null
    }

    // ═══════════════════════════════════════════════════
    //  RAZORPAY PAYMENT CALLBACKS
    // ═══════════════════════════════════════════════════

    override fun onPaymentSuccess(paymentId: String?, data: com.razorpay.PaymentData?) {
        try {
            android.util.Log.d("Razorpay", "Payment Success callback triggered: $paymentId")
            if (paymentId != null && data != null) {
                premiumViewModel.onPaymentSuccess(
                    paymentId = paymentId,
                    orderId = data.orderId,
                    signature = data.signature
                )
            } else {
                android.util.Log.e("Razorpay", "Payment Success callback fired but paymentId or data is null")
            }
        } catch (e: Exception) {
            android.util.Log.e("Razorpay", "CRASH in onPaymentSuccess", e)
            // Optional Crashlytics log: com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(e)
        }
    }

    override fun onPaymentError(errorCode: Int, errorMessage: String?, data: com.razorpay.PaymentData?) {
        try {
            android.util.Log.e("Razorpay", "Payment Error [$errorCode]: $errorMessage")
            premiumViewModel.onPaymentError(errorCode, errorMessage)
        } catch (e: Exception) {
            android.util.Log.e("Razorpay", "CRASH in onPaymentError", e)
        }
    }
}
