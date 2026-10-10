@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.parkit.app.ui

import android.Manifest
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.ripple.rememberRipple
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
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
import com.parkit.app.api.friendlyMessage
import com.parkit.app.auth.SessionStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.MapTileProviderBasic
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.TilesOverlay
import kotlin.math.pow

/** The transparent-PNG roads+labels layer that sits on top of the plain
 * color "base" tile — see MapTiles' doc comment for why the basemap is
 * split into two layers instead of one. */
private fun referenceOverlay(ctx: android.content.Context, source: OnlineTileSourceBase): TilesOverlay =
    TilesOverlay(MapTileProviderBasic(ctx, source), ctx).apply { loadingBackgroundColor = android.graphics.Color.TRANSPARENT }

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
    val layers = if (isDarkTheme) MapTiles.DARK else MapTiles.LIGHT

    var mapViewRef by remember { mutableStateOf<MapView?>(null) }
    var spots by remember { mutableStateOf<List<SpotOut>>(emptyList()) }
    var selectedSpot by remember { mutableStateOf<SpotOut?>(null) }
    var showReportFlow by remember { mutableStateOf(false) }
    var typeFilter by remember { mutableStateOf<String?>(null) } // null = All

    var geocodeTarget by remember { mutableStateOf(TEL_AVIV) }
    var resolvedAddress by remember { mutableStateOf<String?>(null) }

    // osmdroid draws tiles straight onto its own Canvas, outside Compose, so
    // there's no clean per-tile "loaded" callback to hook into — a blank/gray
    // map with zero feedback was the exact "can't load maps" complaint this
    // answers. A brief spinner over the cold-start window (tiles are either
    // already cached and this never gets noticed, or they're genuinely still
    // loading and now there's visible proof something is happening) is the
    // same tradeoff most map apps make rather than wiring a precise signal.
    var showMapLoading by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(1400)
        showMapLoading = false
    }

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
            snackbarHostState.showSnackbar(context.getString(R.string.map_load_error, e.friendlyMessage(context)))
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
                        setTileSource(layers.base)
                        overlays.add(referenceOverlay(ctx, layers.reference))
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
                    if (mapView.tileProvider.tileSource.name() != layers.base.name()) {
                        mapView.setTileSource(layers.base)
                        mapView.overlays.filterIsInstance<TilesOverlay>().let { mapView.overlays.removeAll(it) }
                        mapView.overlays.add(0, referenceOverlay(mapView.context, layers.reference))
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

            androidx.compose.animation.AnimatedVisibility(
                visible = showMapLoading,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Surface(shape = CircleShape, shadowElevation = 6.dp, color = MaterialTheme.colorScheme.surface) {
                    androidx.compose.material3.CircularProgressIndicator(
                        modifier = Modifier.padding(16.dp).size(28.dp),
                        strokeWidth = 3.dp,
                    )
                }
            }

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
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(34.dp)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Image(
                                painter = painterResource(R.drawable.ic_logo),
                                contentDescription = stringResource(R.string.logo_cd),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        Text(
                            "ParkIt",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.weight(1f).padding(start = 10.dp),
                        )
                        // One profile entry point, not a profile+logout pair —
                        // logout is an account action and now lives in Profile
                        // itself, decluttering the map's primary chrome down to
                        // what you actually need while looking for parking.
                        Box(
                            modifier = Modifier.size(38.dp)
                                .background(MaterialTheme.colorScheme.primary, CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = rememberRipple(bounded = true, radius = 19.dp),
                                    onClick = onOpenProfile,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Person,
                                contentDescription = stringResource(R.string.map_profile_cd),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(20.dp),
                            )
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
            // the My Location button rather than replacing it. Sized and shaped
            // to match the locate button below them (small circles, own shadow
            // each) instead of one tall joined pill — the previous version read
            // as a dated widget bolted onto the map rather than part of the UI.
            Column(
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 150.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Surface(shape = CircleShape, shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surface, modifier = Modifier.size(40.dp)) {
                    IconButton(onClick = { mapViewRef?.controller?.zoomIn() }) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.map_zoom_in_cd), modifier = Modifier.size(18.dp))
                    }
                }
                Surface(shape = CircleShape, shadowElevation = 4.dp, color = MaterialTheme.colorScheme.surface, modifier = Modifier.size(40.dp)) {
                    IconButton(onClick = { mapViewRef?.controller?.zoomOut() }) {
                        Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.map_zoom_out_cd), modifier = Modifier.size(18.dp))
                    }
                }
                Surface(shape = CircleShape, shadowElevation = 6.dp, color = MaterialTheme.colorScheme.surface, modifier = Modifier.size(46.dp).padding(top = 2.dp)) {
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
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 4.dp,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    // A small live-status dot — the spot count is a live
                    // reading of the map, not static copy, so it gets the same
                    // visual language a status indicator would.
                    Box(
                        modifier = Modifier.size(7.dp)
                            .background(if (spots.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f) else MaterialTheme.colorScheme.primary, CircleShape),
                    )
                    Text(
                        pluralStringResource(R.plurals.spots_available, spots.size, spots.size, DEFAULT_RADIUS_M),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
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
                ReportFab(onClick = { showReportFlow = true })
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

/** The single most important action on the whole screen deserved more
 * than a flat Material default FAB — a gloss-gradient fill (not flat
 * color) for real depth, a deeper shadow, and a spring-overshoot
 * entrance so it arrives with some life instead of just being present
 * from frame one. */
@Composable
private fun ReportFab(onClick: () -> Unit) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow,
        ),
        label = "fabEntrance",
    )

    val primary = MaterialTheme.colorScheme.primary
    val gradient = androidx.compose.ui.graphics.Brush.linearGradient(
        colors = listOf(androidx.compose.ui.graphics.lerp(primary, Color.White, 0.22f), primary),
    )

    Box(
        modifier = Modifier
            .graphicsLayer { scaleX = scale; scaleY = scale; alpha = scale }
            .size(64.dp)
            .shadow(elevation = 14.dp, shape = CircleShape, ambientColor = primary, spotColor = primary)
            .background(gradient, CircleShape)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = rememberRipple(bounded = true, radius = 32.dp), onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = stringResource(R.string.map_report_fab_cd),
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(32.dp),
        )
    }
}
