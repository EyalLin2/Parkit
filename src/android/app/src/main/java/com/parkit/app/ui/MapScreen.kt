@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.parkit.app.ui

import android.Manifest
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.parkit.app.R
import com.parkit.app.api.ApiService
import com.parkit.app.api.GeocodingClient
import com.parkit.app.api.SpotOut
import com.parkit.app.auth.SessionStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import kotlin.math.pow

private val TEL_AVIV = GeoPoint(32.0809, 34.7806)
private const val DEFAULT_RADIUS_M = 1500

private fun statusColor(spot: SpotOut, myUserId: String?): String = when {
    spot.status == "claimed" -> "#B8631A"
    spot.reporterId == myUserId -> "#1B4F91"
    else -> "#2C7A4B"
}

/** Simple grid-bucket clustering: cells shrink as you zoom in, so nearby
 * pins collapse into a single "N spots" badge at low zoom and separate
 * out again as you zoom in — no extra clustering library needed. */
private fun clusterSpots(spots: List<SpotOut>, zoom: Double): List<List<SpotOut>> {
    if (spots.isEmpty()) return emptyList()
    val cellSize = 0.4 / 2.0.pow((zoom - 8).coerceAtLeast(0.0))
    val buckets = LinkedHashMap<Pair<Int, Int>, MutableList<SpotOut>>()
    for (spot in spots) {
        val key = (spot.lat / cellSize).toInt() to (spot.lng / cellSize).toInt()
        buckets.getOrPut(key) { mutableListOf() }.add(spot)
    }
    return buckets.values.toList()
}

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
    var selectedSpot by remember { mutableStateOf<SpotOut?>(null) }
    var showReportFlow by remember { mutableStateOf(false) }
    var typeFilter by remember { mutableStateOf<String?>(null) } // null = All

    var geocodeTarget by remember { mutableStateOf(TEL_AVIV) }
    var resolvedAddress by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(geocodeTarget) {
        delay(600)
        try {
            resolvedAddress = GeocodingClient.service.reverse(geocodeTarget.latitude, geocodeTarget.longitude).shortLabel()
        } catch (_: Exception) {
            resolvedAddress = "%.5f, %.5f".format(geocodeTarget.latitude, geocodeTarget.longitude)
        }
        try {
            spots = api.nearbySpots(geocodeTarget.latitude, geocodeTarget.longitude, DEFAULT_RADIUS_M)
        } catch (e: Exception) {
            snackbarHostState.showSnackbar("Couldn't load spots: ${e.message}")
        }
    }

    fun useDeviceLocation() {
        val lm = context.getSystemService(LocationManager::class.java)
        val loc = try {
            lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER) ?: lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (_: SecurityException) {
            null
        }
        if (loc != null) {
            val here = GeoPoint(loc.latitude, loc.longitude)
            mapViewRef?.controller?.animateTo(here)
            geocodeTarget = here
        }
    }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) useDeviceLocation()
    }

    LaunchedEffect(Unit) {
        locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun refreshSpotsNow() {
        scope.launch {
            try {
                spots = api.nearbySpots(geocodeTarget.latitude, geocodeTarget.longitude, DEFAULT_RADIUS_M)
            } catch (_: Exception) {
                // next debounced pass will retry
            }
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    MapView(ctx).apply {
                        setTileSource(TileSourceFactory.MAPNIK)
                        setMultiTouchControls(true)
                        setBuiltInZoomControls(false)
                        controller.setZoom(16.0)
                        controller.setCenter(geocodeTarget)
                        addMapListener(object : MapListener {
                            override fun onScroll(event: ScrollEvent?): Boolean {
                                geocodeTarget = mapCenter as GeoPoint
                                return true
                            }
                            override fun onZoom(event: ZoomEvent?): Boolean {
                                geocodeTarget = mapCenter as GeoPoint
                                return true
                            }
                        })
                        mapViewRef = this
                    }
                },
                update = { mapView ->
                    mapView.overlays.filterIsInstance<Marker>().let { mapView.overlays.removeAll(it) }

                    val visible = typeFilter?.let { f -> spots.filter { it.spotType == f } } ?: spots
                    val clusters = clusterSpots(visible, mapView.zoomLevelDouble)

                    clusters.forEach { group ->
                        if (group.size == 1) {
                            val spot = group[0]
                            val marker = Marker(mapView)
                            marker.position = GeoPoint(spot.lat, spot.lng)
                            marker.setAnchor(0.5f, 0.5f)
                            marker.icon = android.graphics.drawable.BitmapDrawable(
                                mapView.context.resources,
                                MarkerBitmaps.badge(statusColor(spot, sessionStore.userId.value), MarkerBitmaps.relativeTimeShort(spot.reportedAt)),
                            )
                            marker.title = "${spot.spotType} · ${spot.payment} · ${spot.status}"
                            marker.setOnMarkerClickListener { _, _ -> selectedSpot = spot; true }
                            mapView.overlays.add(marker)
                        } else {
                            val centerLat = group.map { it.lat }.average()
                            val centerLng = group.map { it.lng }.average()
                            val marker = Marker(mapView)
                            marker.position = GeoPoint(centerLat, centerLng)
                            marker.setAnchor(0.5f, 0.5f)
                            marker.icon = android.graphics.drawable.BitmapDrawable(
                                mapView.context.resources,
                                MarkerBitmaps.clusterBadge(group.size),
                            )
                            marker.title = "${group.size} spots"
                            marker.setOnMarkerClickListener { _, _ ->
                                mapView.controller.animateTo(GeoPoint(centerLat, centerLng))
                                mapView.controller.zoomIn()
                                true
                            }
                            mapView.overlays.add(marker)
                        }
                    }
                    mapView.invalidate()
                },
            )

            Image(
                painter = painterResource(R.drawable.ic_center_reticle),
                contentDescription = "Report location",
                modifier = Modifier.align(Alignment.Center).size(40.dp),
            )

            // Floating translucent header — logo/profile/logout + a type filter row underneath.
            Column(
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().fillMaxWidth()
                    .padding(horizontal = 16.dp).padding(top = 8.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shadowElevation = 6.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("ParkIt", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                        IconButton(onClick = onOpenProfile) { Icon(Icons.Filled.Person, contentDescription = "Profile") }
                        IconButton(onClick = { sessionStore.clear(); onLoggedOut() }) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Logout")
                        }
                    }
                }
                Row(
                    modifier = Modifier.padding(top = 8.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    FilterChip(selected = typeFilter == null, onClick = { typeFilter = null }, label = { Text("All") })
                    FilterChip(selected = typeFilter == "street", onClick = { typeFilter = "street" }, label = { Text("Regular") })
                    FilterChip(selected = typeFilter == "disabled", onClick = { typeFilter = "disabled" }, label = { Text("Disabled") })
                }
            }

            Surface(
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 172.dp),
                shape = CircleShape,
                shadowElevation = 6.dp,
                color = MaterialTheme.colorScheme.surface,
            ) {
                IconButton(onClick = {
                    if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                        android.content.pm.PackageManager.PERMISSION_GRANTED
                    ) {
                        useDeviceLocation()
                    } else {
                        locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    }
                }) {
                    Icon(Icons.Filled.MyLocation, contentDescription = "My location", tint = MaterialTheme.colorScheme.primary)
                }
            }

            // One unified card: address confirmation + the single report action, rather
            // than two separate floating pieces.
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 10.dp,
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            resolvedAddress ?: "Locating…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                    Button(
                        onClick = { showReportFlow = true },
                        shape = RoundedCornerShape(50),
                        colors = ButtonDefaults.buttonColors(),
                        modifier = Modifier.fillMaxWidth().height(56.dp).padding(top = 12.dp),
                    ) {
                        Text("Report Parking Here", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }
    }

    if (showReportFlow) {
        ReportFlowSheet(
            api = api,
            lat = geocodeTarget.latitude,
            lng = geocodeTarget.longitude,
            addressLabel = resolvedAddress ?: "%.5f, %.5f".format(geocodeTarget.latitude, geocodeTarget.longitude),
            onDismiss = { showReportFlow = false },
            onReported = {
                showReportFlow = false
                scope.launch { snackbarHostState.showSnackbar("Spot reported 🅿️") }
                refreshSpotsNow()
            },
        )
    }

    selectedSpot?.let { spot ->
        SpotActionsSheet(
            api = api,
            spot = spot,
            myUserId = sessionStore.userId.value,
            onDismiss = { selectedSpot = null },
            onChanged = {
                selectedSpot = null
                refreshSpotsNow()
            },
        )
    }

    DisposableEffect(Unit) {
        onDispose { mapViewRef?.onDetach() }
    }
}
