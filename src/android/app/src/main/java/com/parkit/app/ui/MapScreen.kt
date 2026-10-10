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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
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
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import kotlin.math.pow

private val TEL_AVIV = GeoPoint(32.0809, 34.7806)
private const val DEFAULT_RADIUS_M = 1500

// Guards against snapping to an out-of-country GPS fix (e.g. an
// emulator/device defaulting to Mountain View, CA) — see useDeviceLocation().
private val SERVICE_AREA_CENTER = TEL_AVIV
private const val SERVICE_AREA_RADIUS_KM = 350.0

private fun distanceKm(a: GeoPoint, b: GeoPoint): Double {
    val earthRadiusKm = 6371.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLon = Math.toRadians(b.longitude - a.longitude)
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)
    val sinDLat = Math.sin(dLat / 2)
    val sinDLon = Math.sin(dLon / 2)
    val h = sinDLat * sinDLat + Math.cos(lat1) * Math.cos(lat2) * sinDLon * sinDLon
    return earthRadiusKm * 2 * Math.atan2(Math.sqrt(h), Math.sqrt(1 - h))
}

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
    val isDarkTheme = com.parkit.app.ui.theme.isDarkThemeActive()
    val tileSource = if (isDarkTheme) MapTiles.DARK_MATTER else MapTiles.VOYAGER

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
            snackbarHostState.showSnackbar(context.getString(R.string.map_load_error, e.message))
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
            // ParkIt only operates in Israel. A device/emulator with a stale or
            // default GPS fix (classically Mountain View, CA on emulators) would
            // otherwise yank a first-time user — or every demo — away from the
            // service area onto an irrelevant map. Silently ignore anything
            // outside it and keep the Tel Aviv default instead.
            if (distanceKm(SERVICE_AREA_CENTER, here) <= SERVICE_AREA_RADIUS_KM) {
                mapViewRef?.controller?.animateTo(here)
                geocodeTarget = here
            }
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
                        setTileSource(tileSource)
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
                    if (mapView.tileProvider.tileSource.name() != tileSource.name()) {
                        mapView.setTileSource(tileSource)
                    }
                    mapView.overlays.filterIsInstance<Marker>().let { mapView.overlays.removeAll(it) }

                    val visible = typeFilter?.let { f -> spots.filter { it.spotType == f } } ?: spots
                    val clusters = clusterSpots(visible, mapView.zoomLevelDouble)

                    clusters.forEach { group ->
                        if (group.size == 1) {
                            val spot = group[0]
                            val marker = Marker(mapView)
                            marker.position = GeoPoint(spot.lat, spot.lng)
                            marker.setAnchor(MarkerBitmaps.ANCHOR_X, MarkerBitmaps.ANCHOR_Y)
                            marker.icon = android.graphics.drawable.BitmapDrawable(
                                mapView.context.resources,
                                MarkerBitmaps.badge(statusColor(spot, sessionStore.userId.value), MarkerBitmaps.relativeTimeShort(context, spot.reportedAt)),
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
                contentDescription = stringResource(R.string.map_report_location_cd),
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
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_logo),
                            contentDescription = stringResource(R.string.logo_cd),
                            modifier = Modifier.size(36.dp),
                        )
                        Text(
                            "ParkIt",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.weight(1f).padding(start = 10.dp),
                        )
                        IconButton(onClick = onOpenProfile) { Icon(Icons.Filled.Person, contentDescription = stringResource(R.string.map_profile_cd)) }
                        IconButton(onClick = { sessionStore.clear(); onLoggedOut() }) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = stringResource(R.string.map_logout_cd))
                        }
                    }
                }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    shadowElevation = 4.dp,
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp).horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf(
                            null to stringResource(R.string.map_filter_all),
                            "street" to stringResource(R.string.map_filter_regular),
                            "disabled" to stringResource(R.string.map_filter_disabled),
                        ).forEach { (value, label) ->
                            val selected = typeFilter == value
                            FilterChip(
                                selected = selected,
                                onClick = { typeFilter = value },
                                label = { Text(label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
                                    containerColor = MaterialTheme.colorScheme.surface,
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                                border = if (selected) null else FilterChipDefaults.filterChipBorder(enabled = true, selected = false),
                            )
                        }
                    }
                }
            }

            // Pinch-to-zoom alone isn't reliably discoverable (and doesn't work at
            // all via a mouse on an emulator), so explicit zoom controls sit above
            // the My Location button rather than replacing it.
            Column(
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 150.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(shape = RoundedCornerShape(14.dp), shadowElevation = 6.dp, color = MaterialTheme.colorScheme.surface) {
                    Column {
                        IconButton(onClick = { mapViewRef?.controller?.zoomIn() }) {
                            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.map_zoom_in_cd))
                        }
                        IconButton(onClick = { mapViewRef?.controller?.zoomOut() }) {
                            Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.map_zoom_out_cd))
                        }
                    }
                }
                Surface(shape = CircleShape, shadowElevation = 6.dp, color = MaterialTheme.colorScheme.surface) {
                    IconButton(onClick = {
                        if (androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                            android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            useDeviceLocation()
                        } else {
                            locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                        }
                    }) {
                        Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.map_my_location_cd), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 150.dp),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                shadowElevation = 4.dp,
            ) {
                Text(
                    pluralStringResource(R.plurals.spots_available, spots.size, spots.size, DEFAULT_RADIUS_M),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            // A circular floating "+" action (Waze's own report-button pattern,
            // also how Spent's add-expense FAB reads) instead of a full-width
            // bar — leaves far more of the map visible, and reads as a single
            // deliberate action rather than a form bar docked to the screen.
            Column(
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    shadowElevation = 4.dp,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        Icon(
                            Icons.Filled.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            resolvedAddress ?: stringResource(R.string.map_locating),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
                FloatingActionButton(
                    onClick = { showReportFlow = true },
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
                    modifier = Modifier.size(64.dp),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.map_report_fab_cd), modifier = Modifier.size(32.dp))
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
                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.map_reported_snackbar)) }
                refreshSpotsNow()
            },
            onSessionExpired = onLoggedOut,
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
            onSessionExpired = onLoggedOut,
        )
    }

    DisposableEffect(Unit) {
        onDispose { mapViewRef?.onDetach() }
    }
}
