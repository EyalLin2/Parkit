package com.parkit.app

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.parkit.app.api.ApiClient
import com.parkit.app.auth.SessionStore
import com.parkit.app.locale.LocaleManager
import com.parkit.app.ui.DetailsScreen
import com.parkit.app.ui.DriverProfileScreen
import com.parkit.app.ui.LoginScreen
import com.parkit.app.ui.MapScreen
import com.parkit.app.ui.ProfileScreen
import com.parkit.app.ui.RewardsScreen
import com.parkit.app.ui.theme.ParkItTheme
import com.parkit.app.ui.theme.ThemeManager
import com.parkit.app.ui.SettingsScreen
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre

class MainActivity : ComponentActivity() {
    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        takeCarAction(intent)
    }

    private fun takeCarAction(intent: android.content.Intent?) {
        intent?.getStringExtra(com.parkit.app.remind.CarBluetooth.EXTRA_ACTION)?.let {
            com.parkit.app.remind.CarBluetooth.pendingAction.value = it
            intent.removeExtra(com.parkit.app.remind.CarBluetooth.EXTRA_ACTION)
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Must run before any MapView is inflated. OpenFreeMap needs no API
        // key, so there's nothing to pass beyond the context.
        MapLibre.getInstance(this)

        ThemeManager.init(this)
        com.parkit.app.api.CrashReporter.install(this)
        val sessionStore = SessionStore(this)
        takeCarAction(intent)

        setContent {
            ParkItTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ParkItApp(sessionStore = sessionStore)
                }
            }
        }
    }
}

private const val SESSION_IDLE_MS = 14 * 24 * 60 * 60 * 1000L // two weeks; matches the server's SESSION_IDLE_MINUTES
private const val RENEW_EVERY_MS = 10 * 60 * 1000L

@Composable
fun ParkItApp(sessionStore: SessionStore) {
    val navController = rememberNavController()
    val api = ApiClient.create(sessionStore)
    // Decided once: the token flips to non-null mid-login, and a changed
    // startDestination rebuilds the graph — which used to jump straight to
    // the map and skip the post-login onboarding step.
    val startDestination = androidx.compose.runtime.remember {
        when {
            sessionStore.token.value != null && sessionStore.needsProfile -> "details"
            sessionStore.token.value != null && !sessionStore.onboardingSeen -> "onboarding"
            sessionStore.token.value != null -> "map"
            !sessionStore.introSeen -> "intro"
            else -> "login"
        }
    }

    // One path back to login for both a manual Logout tap and an expired
    // JWT (dev-login tokens last 24h with no refresh) — popUpTo(0) clears
    // the back stack regardless of which screen triggered it.
    val goToLogin: () -> Unit = {
        sessionStore.clear()
        navController.navigate("login") { popUpTo(0) { inclusive = true } }
    }

    // Real motion, not a hard cut between screens — a fade for the one-way
    // login→map handoff (it's a state change, not a "place" to go back to),
    // and a directional push/pop slide for map↔profile that uses
    // Start/End (not Left/Right) specifically so it mirrors correctly in
    // Hebrew/RTL instead of always sliding the same physical direction.
    val motionSpec = tween<Float>(280)
    val offsetSpec = tween<androidx.compose.ui.unit.IntOffset>(280)

    // A crash on the previous run left a report on disk: send it now.
    val appContext = androidx.compose.ui.platform.LocalContext.current.applicationContext
    androidx.compose.runtime.LaunchedEffect(Unit) { com.parkit.app.api.CrashReporter.sendPending(appContext, api) }

    // Sessions end after SESSION_IDLE_MINUTES away from the app (the server
    // enforces the same window). While the app is open the session is
    // renewed every few minutes; coming back after longer than the window
    // goes straight to the SMS sign-in — even offline.
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    suspend fun renew() {
        if (sessionStore.token.value == null) return
        try {
            val fresh = api.refreshToken()
            sessionStore.save(fresh.accessToken, fresh.userId, sessionStore.displayName.value.orEmpty())
        } catch (e: Exception) {
            if (e is retrofit2.HttpException && e.code() == 401) goToLogin()
        }
    }
    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    androidx.compose.runtime.DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> sessionStore.leftAppAt = System.currentTimeMillis()
                androidx.lifecycle.Lifecycle.Event.ON_START -> {
                    val away = System.currentTimeMillis() - sessionStore.leftAppAt
                    if (sessionStore.token.value != null && sessionStore.leftAppAt > 0 && away > SESSION_IDLE_MS) goToLogin()
                    else scope.launch { renew() }
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(RENEW_EVERY_MS)
            renew()
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable("intro", exitTransition = { fadeOut(animationSpec = motionSpec) }) {
            com.parkit.app.ui.IntroScreen(onDone = {
                sessionStore.introSeen = true
                navController.navigate("login") { popUpTo("intro") { inclusive = true } }
            })
        }
        composable(
            "login",
            exitTransition = { fadeOut(animationSpec = motionSpec) },
        ) {
            LoginScreen(
                api = api,
                sessionStore = sessionStore,
                onLoggedIn = { needsProfile ->
                    sessionStore.needsProfile = needsProfile
                    val next = when {
                        needsProfile -> "details"
                        sessionStore.onboardingSeen -> "map"
                        else -> "onboarding"
                    }
                    navController.navigate(next) { popUpTo("login") { inclusive = true } }
                },
            )
        }
        composable("details", exitTransition = { fadeOut(animationSpec = motionSpec) }) {
            DetailsScreen(
                api = api,
                sessionStore = sessionStore,
                firstTime = true,
                onDone = {
                    sessionStore.needsProfile = false
                    navController.navigate(if (sessionStore.onboardingSeen) "map" else "onboarding") { popUpTo("details") { inclusive = true } }
                },
                onSessionExpired = goToLogin,
            )
        }
        composable(
            "details-edit",
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, animationSpec = offsetSpec) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, animationSpec = offsetSpec) },
        ) {
            DetailsScreen(api = api, sessionStore = sessionStore, firstTime = false, onDone = { navController.popBackStack() }, onSessionExpired = goToLogin)
        }
        composable(
            "onboarding",
            exitTransition = { fadeOut(animationSpec = motionSpec) },
        ) {
            DriverProfileScreen(
                api = api,
                sessionStore = sessionStore,
                onboarding = true,
                onDone = { navController.navigate(if (sessionStore.tipsSeen) "map" else "tips") { popUpTo("onboarding") { inclusive = true } } },
                onSessionExpired = goToLogin,
            )
        }
        composable("tips", exitTransition = { fadeOut(animationSpec = motionSpec) }) {
            com.parkit.app.ui.TipsScreen(onDone = {
                sessionStore.tipsSeen = true
                // First run goes on to the map; from Settings it just closes.
                if (!navController.popBackStack("settings", inclusive = false)) {
                    navController.navigate("map") { popUpTo("tips") { inclusive = true } }
                }
            })
        }
        composable(
            "map",
            enterTransition = { fadeIn(animationSpec = motionSpec) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, animationSpec = offsetSpec) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, animationSpec = offsetSpec) },
        ) {
            MapScreen(
                api = api,
                sessionStore = sessionStore,
                onOpenProfile = { navController.navigate("profile") },
                onLoggedOut = goToLogin,
            )
        }
        composable(
            "profile",
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, animationSpec = offsetSpec) },
            exitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Start, animationSpec = offsetSpec) },
            popEnterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.End, animationSpec = offsetSpec) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, animationSpec = offsetSpec) },
        ) {
            ProfileScreen(
                api = api,
                onBack = { navController.popBackStack() },
                sessionStore = sessionStore,
                onOpenSettings = { navController.navigate("settings") },
                onOpenDriver = { navController.navigate("driver") },
                onOpenDetails = { navController.navigate("details-edit") },
                onOpenRewards = { navController.navigate("rewards") },
                onOpenBackdrops = { navController.navigate("rewards?tab=3") },
                onSessionExpired = goToLogin,
            )
        }
        composable(
            "rewards?tab={tab}",
            arguments = listOf(androidx.navigation.navArgument("tab") { type = androidx.navigation.NavType.IntType; defaultValue = 1 }),
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, animationSpec = offsetSpec) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, animationSpec = offsetSpec) },
        ) {
            RewardsScreen(
                api = api,
                sessionStore = sessionStore,
                initialTab = it.arguments?.getInt("tab") ?: 1,
                onBack = { navController.popBackStack() },
                onSessionExpired = goToLogin,
            )
        }
        composable(
            "driver",
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, animationSpec = offsetSpec) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, animationSpec = offsetSpec) },
        ) {
            DriverProfileScreen(
                api = api,
                sessionStore = sessionStore,
                onboarding = false,
                onDone = { navController.popBackStack() },
                onSessionExpired = goToLogin,
            )
        }
        composable(
            "settings",
            enterTransition = { slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Start, animationSpec = offsetSpec) },
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, animationSpec = offsetSpec) },
        ) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLogout = goToLogin,
                onDeleteAccount = {
                    api.deleteAccount()
                    goToLogin()
                },
                onOpenDetails = { navController.navigate("details-edit") },
                onOpenDriver = { navController.navigate("driver") },
                onOpenTips = { navController.navigate("tips") },
                carLabel = sessionStore.driver.value.preferences.carLabel,
                onSendFeedback = { text ->
                    val recent = com.parkit.app.api.RecentErrors.summary()
                    api.sendFeedback(
                        com.parkit.app.api.FeedbackIn(
                            kind = "feedback",
                            message = if (recent.isBlank()) text else "$text\n\n[recent errors: $recent]",
                            appVersion = com.parkit.app.api.CrashReporter.appVersion(appContext),
                            device = com.parkit.app.api.CrashReporter.device(),
                        ),
                    )
                },
            )
        }
    }
}
