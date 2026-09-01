package com.parkit.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.parkit.app.api.ApiClient
import com.parkit.app.auth.SessionStore
import com.parkit.app.ui.LoginScreen
import com.parkit.app.ui.MapScreen
import com.parkit.app.ui.ProfileScreen
import com.parkit.app.ui.theme.ParkItTheme
import org.osmdroid.config.Configuration

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // osmdroid requires a user agent (tile servers ban the default) and
        // a writable cache dir — both set once here per osmdroid's own setup docs.
        Configuration.getInstance().userAgentValue = packageName
        Configuration.getInstance().osmdroidTileCache = cacheDir

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

    NavHost(navController = navController, startDestination = startDestination) {
        composable("login") {
            LoginScreen(
                api = api,
                sessionStore = sessionStore,
                onLoggedIn = { navController.navigate("map") { popUpTo("login") { inclusive = true } } },
            )
        }
        composable("map") {
            MapScreen(
                api = api,
                sessionStore = sessionStore,
                onOpenProfile = { navController.navigate("profile") },
                onLoggedOut = { navController.navigate("login") { popUpTo("map") { inclusive = true } } },
            )
        }
        composable("profile") {
            ProfileScreen(api = api, onBack = { navController.popBackStack() })
        }
    }
}
