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
import com.parkit.app.ui.LoginScreen
import com.parkit.app.ui.MapScreen
import com.parkit.app.ui.ProfileScreen
import com.parkit.app.ui.theme.ParkItTheme
import com.parkit.app.ui.theme.ThemeManager
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // osmdroid requires a user agent (tile servers ban the default) and
        // a writable cache dir — both set once here per osmdroid's own setup docs.
        Configuration.getInstance().userAgentValue = packageName
        Configuration.getInstance().osmdroidTileCache = cacheDir

        ThemeManager.init(this)
        val sessionStore = SessionStore(this)

        setContent {
            ParkItTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    ParkItApp(sessionStore = sessionStore)
                }
            }
        }
    }
}

@Composable
fun ParkItApp(sessionStore: SessionStore) {
    val navController = rememberNavController()
    val api = ApiClient.create(sessionStore)
    val startDestination = if (sessionStore.token.value != null) "map" else "login"

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

    NavHost(navController = navController, startDestination = startDestination) {
        composable(
            "login",
            exitTransition = { fadeOut(animationSpec = motionSpec) },
        ) {
            LoginScreen(
                api = api,
                sessionStore = sessionStore,
                onLoggedIn = { navController.navigate("map") { popUpTo("login") { inclusive = true } } },
            )
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
            popExitTransition = { slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.End, animationSpec = offsetSpec) },
        ) {
            ProfileScreen(api = api, onBack = { navController.popBackStack() }, onSessionExpired = goToLogin)
        }
    }
}
