@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.parkit.app.ui

import android.Manifest
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.parkit.app.api.ApiService
import com.parkit.app.api.SpotOut
import com.parkit.app.auth.SessionStore
import kotlinx.coroutines.launch
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.MapEventsOverlay

private val TEL_AVIV = GeoPoint(32.0809, 34.7806)
private const val DEFAULT_RADIUS_M = 1500

@Composable
fun MapScreen(
    api: ApiService,
    sessionStore: SessionStore,
    onOpenProfile: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var spots by remember { mutableStateOf<List<SpotOut>>(emptyList()) }
    var pendingReportPoint by remember { mutableStateOf<GeoPoint?>(null) }
    var selectedSpot by remember { mutableStateOf<SpotOut?>(null) }
    var center by remember { mutableStateOf(TEL_AVIV) }

    fun refreshSpots() {
        val c = mapViewRef?.mapCenter
        val lat = c?.latitude ?: center.latitude
        val lng = c?.longitude ?: center.longitude
        scope.launch {
            try {
                spots = api.nearbySpots(lat, lng, DEFAULT_RADIUS_M)
            } catch (e: Exception) {
                snackbarHostState.showSnackbar("Couldn't load spots: ${e.message}")
            }
        }
    }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val lm = context.getSystemService(LocationManager::class.java)
            val loc = try {
                lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                    ?: lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } catch (_: SecurityException) {
                null
            }
            if (loc != null) {
                center = GeoPoint(loc.latitude, loc.longitude)
                mapViewRef?.controller?.setCenter(center)
            }
        }
        refreshSpots()
    }

    LaunchedEffect(Unit) {
        // Harmless (and immediate, no dialog) if permission is already granted —
        // the callback still fires and loads the initial nearby spots either way.
        locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("ParkIt — Demo") },
                actions = {
                    TextButton(onClick = onOpenProfile) { Text("Profile") }
                    TextButton(onClick = {
                        sessionStore.clear()
                        onLoggedOut()
                    }) { Text("Logout") }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { refreshSpots() }) {
                Icon(Icons.Filled.Add, contentDescription = "Refresh nearby spots")
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        controller.setZoom(15.0)
                        controller.setCenter(center)

                        val receiver = object : MapEventsReceiver {
                            override fun singleTapConfirmedHelper(p: GeoPoint): Boolean {
                                pendingReportPoint = p
                                return true
                            }
                            override fun longPressHelper(p: GeoPoint): Boolean = false
                        }
                        overlays.add(MapEventsOverlay(receiver))
                        mapViewRef = this
                    }
                },
                update = { mapView ->
                    // Rebuild spot markers on every recomposition triggered by a `spots` change.
                    // The MapEventsOverlay must stay first (below markers) so taps on it don't
                    // fire under a marker; markers are simply appended after it each time.
                    val toRemove = mapView.overlays.filterIsInstance<Marker>()
                    mapView.overlays.removeAll(toRemove)
                    spots.forEach { spot ->
                        val marker = Marker(mapView)
                        marker.position = GeoPoint(spot.lat, spot.lng)
                        marker.title = "${spot.spotType} · ${spot.payment} · ${spot.status}"
                        marker.setOnMarkerClickListener { _, _ ->
                            selectedSpot = spot
                            true
                        }
                        mapView.overlays.add(marker)
                    }
                    mapView.invalidate()
                },
            )
        }
    }

    pendingReportPoint?.let { point ->
        ReportDialog(
            api = api,
            lat = point.latitude,
            lng = point.longitude,
            onDismiss = { pendingReportPoint = null },
            onReported = {
                pendingReportPoint = null
                refreshSpots()
            },
        )
    }

    selectedSpot?.let { spot ->
        SpotActionsDialog(
            api = api,
            spot = spot,
            myUserId = sessionStore.userId.value,
            onDismiss = { selectedSpot = null },
            onChanged = {
                selectedSpot = null
                refreshSpots()
            },
        )
    }

    DisposableEffect(Unit) {
        onDispose { mapViewRef?.onDetach() }
    }
}
