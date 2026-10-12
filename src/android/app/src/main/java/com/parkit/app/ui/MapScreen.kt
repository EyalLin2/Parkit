@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.parkit.app.ui

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberStandardBottomSheetState
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
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.parkit.app.R
import com.parkit.app.api.ApiService
import com.parkit.app.api.GeocodingClient
import com.parkit.app.api.SpotOut
import com.parkit.app.api.friendlyMessage
import com.parkit.app.auth.SessionStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

private val TEL_AVIV = LatLng(32.0809, 34.7806)
private const val DEFAULT_RADIUS_M = 1500
// MapLibre zoom levels use 512px tiles — one level closer than osmdroid's
// for the same number. 15 shows ~±400m across, matching the search radius feel.
private const val STREET_LEVEL_ZOOM = 15.0

// Guards against snapping to an out-of-country GPS fix (e.g. an
// emulator/device defaulting to Mountain View, CA) — see locate().
private val SERVICE_AREA_CENTER = TEL_AVIV
private const val SERVICE_AREA_RADIUS_M = 350_000.0

// While the first GPS fix is on its way (and there's no recent last-known
// location to start from), show the whole country rather than pretending
// the user is in Tel Aviv.
private val ISRAEL_OVERVIEW = LatLng(31.6, 34.95)
private const val OVERVIEW_ZOOM = 6.6
private const val FIX_TIMEOUT_MS = 10_000L
private const val LAST_KNOWN_MAX_AGE_MS = 30 * 60_000L

private fun inServiceArea(p: LatLng) = p.distanceTo(SERVICE_AREA_CENTER) <= SERVICE_AREA_RADIUS_M

private fun hasFineLocation(context: android.content.Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

/** The freshest cached fix any provider has, if it's recent and in Israel —
 * lets the map open where the user actually is with zero wait. On a real
 * phone this is often empty (nothing used GPS lately), which is exactly why
 * it can't be the only source, as it used to be. */
@SuppressLint("MissingPermission")
private fun recentLastKnown(context: android.content.Context): LatLng? {
    if (!hasFineLocation(context)) return null
    val lm = context.getSystemService(LocationManager::class.java) ?: return null
    val best = lm.getProviders(true)
        .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time } ?: return null
    if (System.currentTimeMillis() - best.time > LAST_KNOWN_MAX_AGE_MS) return null
    return LatLng(best.latitude, best.longitude).takeIf { inServiceArea(it) }
}

/** Asks every enabled provider (GPS, network, fused) for a FRESH fix and
 * returns the first one that arrives, or null after [FIX_TIMEOUT_MS]. */
@SuppressLint("MissingPermission")
private suspend fun freshFix(context: android.content.Context): LatLng? {
    if (!hasFineLocation(context)) return null
    val lm = context.getSystemService(LocationManager::class.java) ?: return null
    val providers = listOf(LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
    if (providers.isEmpty()) return null
    return kotlinx.coroutines.withTimeoutOrNull(FIX_TIMEOUT_MS) {
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            val signal = android.os.CancellationSignal()
            cont.invokeOnCancellation { signal.cancel() }
            providers.forEach { provider ->
                androidx.core.location.LocationManagerCompat.getCurrentLocation(
                    lm,
                    provider,
                    signal,
                    ContextCompat.getMainExecutor(context),
                ) { loc ->
                    if (loc != null && cont.isActive) {
                        signal.cancel()
                        cont.resume(LatLng(loc.latitude, loc.longitude)) { }
                    }
                }
            }
        }
    }
}

private const val SOURCE_SPOTS = "spots"
private const val LAYER_CLUSTERS = "spots-clusters"
private const val LAYER_CLUSTER_COUNT = "spots-cluster-count"
private const val LAYER_PINS = "spots-pins"

// Google-Maps-adjacent status colors: green = go, brand blue = yours,
// amber = someone is already on their way.
private const val COLOR_FREE = "#188038"
private const val COLOR_MINE = "#1B4F91"
private const val COLOR_CLAIMED = "#E8710A"
private const val COLOR_LEGEND = "#7B3FE4"

private val SHEET_PEEK = 114.dp

private fun pinColor(spot: SpotOut, myUserId: String?): String = when {
    spot.status == "claimed" -> COLOR_CLAIMED
    spot.reporterId == myUserId -> COLOR_MINE
    // Legend-level perk: a distinct purple pin (see levels.py on the backend).
    spot.reporterLevel == "legend" -> COLOR_LEGEND
    else -> COLOR_FREE
}

private fun ageMinutes(iso: String): Long = try {
    java.time.Duration.between(java.time.Instant.parse(iso), java.time.Instant.now()).toMinutes()
} catch (_: Exception) {
    0
}

@Composable
fun MapScreen(
    api: ApiService,
    sessionStore: SessionStore,
    onOpenProfile: () -> Unit,
    onLoggedOut: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val isDarkTheme = com.parkit.app.ui.theme.isDarkThemeActive()
    val language = context.resources.configuration.locales[0].language
    val myUserId = sessionStore.userId.value

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var spots by remember { mutableStateOf<List<SpotOut>>(emptyList()) }
    // "No spots nearby" must mean the server said so — not "still loading".
    var spotsLoaded by remember { mutableStateOf(false) }
    var selectedSpot by remember { mutableStateOf<SpotOut?>(null) }
    var showReportFlow by remember { mutableStateOf(false) }
    var typeFilter by remember { mutableStateOf<String?>(null) } // null = All
    var cameraMoving by remember { mutableStateOf(false) }
    var mapRendered by remember { mutableStateOf(false) }
    // Where to open the map: a recent cached fix if there is one; otherwise
    // the country overview until a fresh fix arrives (see locate()).
    val startFix = remember { recentLastKnown(context) }
    var deviceLocation by remember { mutableStateOf(startFix) }
    var locating by remember { mutableStateOf(true) }
    var userTookOver by remember { mutableStateOf(false) }

    var geocodeTarget by remember { mutableStateOf(startFix ?: TEL_AVIV) }
    var resolvedAddress by remember { mutableStateOf<String?>(null) }
    var resolvedStreet by remember { mutableStateOf<String?>(null) }
    // True from the moment the map starts moving until the new pin address
    // is known — the bar shows a loading state instead of a stale or
    // raw-coordinate label.
    var addressLoading by remember { mutableStateOf(true) }
    var addressFailed by remember { mutableStateOf(false) }

    val visibleSpots = typeFilter?.let { f -> spots.filter { it.spotType == f } } ?: spots
    // "Nearby" means near *you* when we know where you are, otherwise near
    // the pin — the same reference point the search radius uses.
    val reference = deviceLocation ?: geocodeTarget
    // Legend-level reports are listed first (a level perk), then by distance.
    val sortedSpots = visibleSpots.sortedWith(
        compareBy<SpotOut>({ it.reporterLevel != "legend" }, { LatLng(it.lat, it.lng).distanceTo(reference) }),
    )

    // Street + number for each listed spot, resolved nearest-first so the
    // rows you actually see fill in first.
    val spotAddresses = remember { androidx.compose.runtime.mutableStateMapOf<String, String>() }
    LaunchedEffect(sortedSpots.map { it.id }) {
        for (spot in sortedSpots) {
            // Reports carry their own address now; only older ones (made
            // before the backend stored it) still need a lookup.
            if (spot.address != null || spotAddresses.containsKey(spot.id)) continue
            val cached = com.parkit.app.api.SpotAddressCache.get(spot.id)
            if (cached != null) {
                spotAddresses[spot.id] = cached
                continue
            }
            com.parkit.app.api.SpotAddressCache.resolve(spot.id, spot.lat, spot.lng)?.let { spotAddresses[spot.id] = it }
            delay(1100)
        }
    }

    LaunchedEffect(geocodeTarget) {
        val target = geocodeTarget
        com.parkit.app.api.Geocoder.cached(target.latitude, target.longitude)?.let { hit ->
            resolvedAddress = hit.shortLabel()
            resolvedStreet = hit.streetLabel()
            addressLoading = false
            addressFailed = false
            return@LaunchedEffect
        }
        addressLoading = true
        delay(250) // let a quick follow-up drag cancel this before it costs a request
        val result = com.parkit.app.api.Geocoder.reverse(target.latitude, target.longitude)
        resolvedAddress = result?.shortLabel()
        resolvedStreet = result?.streetLabel()
        addressFailed = resolvedAddress == null
        addressLoading = false
    }

    // Spots load independently of the address — a slow geocoder must never
    // delay the thing people actually opened the app for.
    LaunchedEffect(geocodeTarget) {
        delay(300)
        try {
            spots = api.nearbySpots(geocodeTarget.latitude, geocodeTarget.longitude, DEFAULT_RADIUS_M)
            spotsLoaded = true
        } catch (e: Exception) {
            snackbarHostState.showSnackbar(context.getString(R.string.map_load_error, e.friendlyMessage(context)))
        }
    }

    fun flyTo(target: LatLng, zoom: Double = STREET_LEVEL_ZOOM) {
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(target, zoom), 650)
    }

    fun hasLocationPermission() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun enableLocationPuck() {
        val m = map ?: return
        val s = style ?: return
        if (!hasLocationPermission()) return
        try {
            m.locationComponent.apply {
                if (!isLocationComponentActivated) {
                    activateLocationComponent(LocationComponentActivationOptions.builder(context, s).useDefaultLocationEngine(true).build())
                }
                isLocationComponentEnabled = true
                cameraMode = CameraMode.NONE
                renderMode = RenderMode.COMPASS
            }
        } catch (_: Exception) {
            // The blue dot is a nicety — never let it take the map down.
        }
    }

    fun showTelAvivFallback(message: Int?) {
        if (deviceLocation == null && !userTookOver) {
            geocodeTarget = TEL_AVIV
            flyTo(TEL_AVIV)
        }
        message?.let { scope.launch { snackbarHostState.showSnackbar(context.getString(it)) } }
    }

    /** Find the user and fly there. On launch it doesn't override a user who
     * already started panning; from the locate button it always flies. */
    fun locate(fromButton: Boolean = false) {
        enableLocationPuck()
        scope.launch {
            locating = true
            val fix = freshFix(context)
            locating = false
            when {
                fix != null && inServiceArea(fix) -> {
                    deviceLocation = fix
                    if (fromButton || !userTookOver) {
                        geocodeTarget = fix
                        flyTo(fix)
                    }
                }
                // ParkIt only operates in Israel; an out-of-country fix (or the
                // emulator's default Mountain View) must not move the map away.
                fix != null -> showTelAvivFallback(R.string.map_outside_service_area)
                deviceLocation != null && fromButton -> flyTo(deviceLocation!!)
                else -> showTelAvivFallback(R.string.map_location_unavailable)
            }
        }
    }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            locate()
        } else {
            locating = false
            showTelAvivFallback(null)
        }
    }
    LaunchedEffect(Unit) {
        if (hasFineLocation(context)) locate() else locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    fun refreshSpotsNow() {
        scope.launch {
            try {
                spots = api.nearbySpots(geocodeTarget.latitude, geocodeTarget.longitude, DEFAULT_RADIUS_M)
            } catch (_: Exception) {
                // next camera-idle pass will retry
            }
        }
    }

    // --- MapLibre view + lifecycle ------------------------------------
    val mapView = remember {
        MapView(context).apply { onCreate(null) }
    }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) mapView.onPause()
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) mapView.onStop()
            mapView.onDestroy()
        }
    }

    LaunchedEffect(Unit) {
        mapView.getMapAsync { m ->
            m.cameraPosition = if (startFix != null) {
                CameraPosition.Builder().target(startFix).zoom(STREET_LEVEL_ZOOM).build()
            } else {
                CameraPosition.Builder().target(ISRAEL_OVERVIEW).zoom(OVERVIEW_ZOOM).build()
            }
            m.uiSettings.apply {
                isLogoEnabled = false
                isCompassEnabled = true
                isRotateGesturesEnabled = true
                isTiltGesturesEnabled = false
                setCompassMargins(0, with(density) { 150.dp.roundToPx() }, with(density) { 16.dp.roundToPx() }, 0)
                setAttributionMargins(with(density) { 12.dp.roundToPx() }, 0, 0, with(density) { (SHEET_PEEK + 56.dp).roundToPx() })
            }
            m.addOnCameraMoveStartedListener { reason ->
                if (reason == MapLibreMap.OnCameraMoveStartedListener.REASON_API_GESTURE && locating) userTookOver = true
                cameraMoving = true
                addressLoading = true
            }
            m.addOnCameraIdleListener {
                cameraMoving = false
                // While we're still finding the user, the overview camera is
                // not a place anyone wants spots or an address for.
                if (!locating || userTookOver) geocodeTarget = m.cameraPosition.target ?: geocodeTarget
            }
            m.addOnMapClickListener { latLng ->
                val point = m.projection.toScreenLocation(latLng)
                val hit = m.queryRenderedFeatures(point, LAYER_PINS, LAYER_CLUSTERS).firstOrNull()
                    ?: return@addOnMapClickListener false
                if (hit.hasProperty("point_count")) {
                    val source = m.style?.getSourceAs<GeoJsonSource>(SOURCE_SPOTS)
                    val zoom = source?.getClusterExpansionZoom(hit)?.toDouble() ?: (m.cameraPosition.zoom + 2)
                    val p = hit.geometry() as Point
                    m.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(p.latitude(), p.longitude()), zoom), 500)
                } else {
                    val id = hit.getStringProperty("id")
                    selectedSpot = spots.firstOrNull { it.id == id }
                }
                true
            }
            mapView.addOnDidFinishRenderingMapListener { fully -> if (fully) mapRendered = true }
            map = m
        }
    }

    // Style is (re)applied whenever the theme or language changes; the spot
    // layers are rebuilt on top of each fresh style.
    LaunchedEffect(map, isDarkTheme, language) {
        val m = map ?: return@LaunchedEffect
        style = null
        m.setStyle(Style.Builder().fromJson(MapStyle.json(context, isDarkTheme, language))) { s ->
            installSpotLayers(s, isDarkTheme)
            style = s
            enableLocationPuck()
        }
    }

    // Push spot data into the map whenever it, the filter, or the selection changes.
    LaunchedEffect(style, visibleSpots, selectedSpot?.id) {
        val s = style ?: return@LaunchedEffect
        val features = visibleSpots.map { spot ->
            val color = pinColor(spot, myUserId)
            val tag = MarkerBitmaps.relativeTimeShort(context, spot.reportedAt)
            val iconId = "pin-$color-$tag"
            if (s.getImage(iconId) == null) s.addImage(iconId, MarkerBitmaps.pin(context, color, tag))
            Feature.fromGeometry(Point.fromLngLat(spot.lng, spot.lat)).apply {
                addStringProperty("id", spot.id)
                addStringProperty("icon", iconId)
                addNumberProperty("age", ageMinutes(spot.reportedAt))
            }
        }
        s.getSourceAs<GeoJsonSource>(SOURCE_SPOTS)?.setGeoJson(FeatureCollection.fromFeatures(features))
        s.getLayerAs<SymbolLayer>(LAYER_PINS)?.setProperties(
            PropertyFactory.iconSize(
                Expression.switchCase(
                    Expression.eq(Expression.get("id"), Expression.literal(selectedSpot?.id ?: "")),
                    Expression.literal(1.25f),
                    Expression.literal(1f),
                ),
            ),
        )
    }

    // --- Layout ---------------------------------------------------------
    val sheetState = rememberStandardBottomSheetState(initialValue = SheetValue.PartiallyExpanded)
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)
    val sheetTopPx = runCatching { sheetState.requireOffset() }.getOrNull()

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        sheetPeekHeight = SHEET_PEEK + with(density) { WindowInsets.navigationBars.getBottom(density).toDp() },
        sheetShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        sheetContainerColor = MaterialTheme.colorScheme.surface,
        sheetTonalElevation = 0.dp,
        sheetShadowElevation = 12.dp,
        sheetDragHandle = { BottomSheetDefaults.DragHandle(width = 36.dp, height = 4.dp) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        sheetContent = {
            NearbySheet(
                spots = sortedSpots,
                loaded = spotsLoaded,
                addresses = spotAddresses,
                reference = reference,
                myUserId = myUserId,
                onSpotClick = { spot ->
                    flyTo(LatLng(spot.lat, spot.lng), 16.5)
                    selectedSpot = spot
                    scope.launch { sheetState.partialExpand() }
                },
            )
        },
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())

            CenterPin(lifted = cameraMoving, modifier = Modifier.align(Alignment.Center))
            // The report action lives on the pin itself (Uber's "set pickup"
            // pattern): the pin IS the spot being reported. Hidden while the
            // map moves, and when the nearby list is pulled up over the pin.
            ReportHereBubble(
                visible = !cameraMoving && mapRendered && !showReportFlow && !(locating && deviceLocation == null) &&
                    sheetState.targetValue != SheetValue.Expanded,
                onClick = { showReportFlow = true },
                modifier = Modifier.align(Alignment.Center),
            )

            androidx.compose.animation.AnimatedVisibility(
                visible = !mapRendered,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
                modifier = Modifier.align(Alignment.Center),
            ) {
                BrandedMapLoader()
            }

            Column(
                modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().fillMaxWidth().padding(top = 8.dp),
            ) {
                LocationBar(
                    address = resolvedAddress,
                    loading = addressLoading,
                    failed = addressFailed,
                    displayName = sessionStore.displayName.value,
                    onOpenProfile = onOpenProfile,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MapChip(stringResource(R.string.map_filter_all), Icons.Filled.LocalParking, typeFilter == null) { typeFilter = null }
                    MapChip(stringResource(R.string.map_filter_regular), Icons.Filled.DirectionsCar, typeFilter == "street") { typeFilter = "street" }
                    MapChip(stringResource(R.string.map_filter_disabled), Icons.AutoMirrored.Filled.Accessible, typeFilter == "disabled") { typeFilter = "disabled" }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = locating && deviceLocation == null && !userTookOver,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
                modifier = Modifier.align(Alignment.Center),
            ) {
                Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surface, shadowElevation = 6.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp)) {
                        androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.map_locating_you), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 10.dp))
                    }
                }
            }

            // Rides the sheet's top edge (Google Maps' locate-button pattern)
            // so it moves with it and is never covered.
            val buttonSize = 48.dp
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        val top = sheetTopPx?.toInt() ?: 0
                        IntOffset(-20.dp.roundToPx(), top - buttonSize.roundToPx() - 16.dp.roundToPx())
                    }
                    .size(buttonSize)
                    .clickable {
                        if (hasLocationPermission()) locate(fromButton = true) else locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                    },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.MyLocation, contentDescription = stringResource(R.string.map_my_location_cd), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }

    if (showReportFlow) {
        ReportFlowSheet(
            api = api,
            lat = geocodeTarget.latitude,
            lng = geocodeTarget.longitude,
            addressLabel = if (addressLoading) null else resolvedAddress,
            streetAddress = resolvedStreet,
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
            myUserId = myUserId,
            distanceLabel = context.getString(
                R.string.spot_distance_away,
                formatDistance(context, LatLng(spot.lat, spot.lng).distanceTo(reference)),
            ),
            onDismiss = { selectedSpot = null },
            onChanged = {
                selectedSpot = null
                refreshSpotsNow()
            },
            onSessionExpired = onLoggedOut,
        )
    }
}

/** Native GeoJSON clustering + symbol layers — the map engine itself
 * groups, positions, and hit-tests pins (on the GPU, smoothly through
 * zoom), instead of Kotlin re-bucketing bitmaps on every recomposition. */
private fun installSpotLayers(style: Style, dark: Boolean) {
    style.addSource(
        GeoJsonSource(
            SOURCE_SPOTS,
            FeatureCollection.fromFeatures(emptyList()),
            GeoJsonOptions().withCluster(true).withClusterRadius(48).withClusterMaxZoom(14),
        ),
    )
    val pointCount = Expression.get("point_count")
    style.addLayer(
        CircleLayer(LAYER_CLUSTERS, SOURCE_SPOTS).withFilter(Expression.has("point_count")).withProperties(
            PropertyFactory.circleColor(COLOR_FREE),
            PropertyFactory.circleRadius(Expression.step(pointCount, Expression.literal(18f), Expression.stop(10, 22f), Expression.stop(30, 27f))),
            PropertyFactory.circleStrokeColor(if (dark) "#1D222B" else "#FFFFFF"),
            PropertyFactory.circleStrokeWidth(3f),
        ),
    )
    style.addLayer(
        SymbolLayer(LAYER_CLUSTER_COUNT, SOURCE_SPOTS).withFilter(Expression.has("point_count")).withProperties(
            PropertyFactory.textField(Expression.toString(pointCount)),
            PropertyFactory.textFont(arrayOf("Noto Sans Bold")),
            PropertyFactory.textSize(14f),
            PropertyFactory.textColor("#FFFFFF"),
            PropertyFactory.textAllowOverlap(true),
            PropertyFactory.textIgnorePlacement(true),
        ),
    )
    style.addLayer(
        SymbolLayer(LAYER_PINS, SOURCE_SPOTS).withFilter(Expression.not(Expression.has("point_count"))).withProperties(
            PropertyFactory.iconImage(Expression.get("icon")),
            PropertyFactory.iconAnchor("bottom"),
            PropertyFactory.iconOffset(arrayOf(0f, MarkerBitmaps.TIP_PADDING_DP)),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
            // Crowdsourced data goes stale — older reports literally fade,
            // so a fresh spot visibly outranks a 40-minute-old one.
            PropertyFactory.iconOpacity(
                Expression.interpolate(Expression.linear(), Expression.get("age"), Expression.stop(10, 1f), Expression.stop(60, 0.55f)),
            ),
            PropertyFactory.symbolSortKey(Expression.get("age")),
        ),
    )
}

/** Google-Maps-style floating search bar, repurposed as "where the pin
 * is" — the address you'd be reporting, plus your avatar (profile). */
@Composable
private fun LocationBar(address: String?, loading: Boolean, failed: Boolean, displayName: String?, onOpenProfile: () -> Unit, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth().height(56.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 16.dp, end = 8.dp)) {
            Image(painter = painterResource(R.drawable.ic_logo), contentDescription = stringResource(R.string.logo_cd), modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    stringResource(R.string.map_pin_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                androidx.compose.animation.Crossfade(targetState = if (loading) null else address, label = "pinAddress") { shown ->
                    when {
                        shown != null -> Text(shown, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        failed && !loading -> Text(
                            stringResource(R.string.map_address_unavailable),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                        else -> AddressSkeleton()
                    }
                }
            }
            Box(
                modifier = Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary, CircleShape).clickable(onClick = onOpenProfile),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    initialOf(displayName),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

/** Pulsing placeholder bar where the address will appear (the skeleton
 * pattern Google/Uber use) — honest "loading" without a spinner, and
 * without ever flashing raw coordinates. */
@Composable
private fun AddressSkeleton() {
    val alpha by androidx.compose.animation.core.rememberInfiniteTransition(label = "skeleton").animateFloat(
        initialValue = 0.35f,
        targetValue = 0.8f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(700),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "skeletonAlpha",
    )
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.height(24.dp)) {
        Box(
            Modifier.width(150.dp).height(14.dp)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f * alpha), RoundedCornerShape(7.dp)),
        )
        Text(
            stringResource(R.string.map_locating),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

internal fun initialOf(name: String?): String = name?.trim()?.firstOrNull()?.uppercaseChar()?.toString() ?: "?"

@Composable
private fun MapChip(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    val container = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
    val content = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Surface(
        shape = RoundedCornerShape(50),
        color = container,
        shadowElevation = 3.dp,
        modifier = Modifier.height(36.dp).clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp)) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = content, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

/** Uber/Google-style drop pin: lifts while the map moves and settles with a
 * small bounce when it stops — so it's obvious the pin marks "here, where
 * the map stopped", not a fixed decoration. The needle tip is the exact
 * screen center. */
@Composable
private fun CenterPin(lifted: Boolean, modifier: Modifier = Modifier) {
    val lift by animateDpAsState(
        targetValue = if (lifted) 12.dp else 0.dp,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "pinLift",
    )
    val shadowScale by animateFloatAsState(if (lifted) 0.6f else 1f, label = "pinShadow")
    val primary = MaterialTheme.colorScheme.primary
    val headSize = 30.dp
    val needle = 16.dp
    Box(modifier = modifier.size(width = headSize, height = (headSize + needle) * 2)) {
        Canvas(modifier = Modifier.align(Alignment.Center).size(10.dp, 4.dp).graphicsLayer { scaleX = shadowScale; scaleY = shadowScale }) {
            drawOval(Color.Black.copy(alpha = 0.25f))
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.align(Alignment.TopCenter).offset(y = -lift),
        ) {
            Box(
                modifier = Modifier.size(headSize).shadow(6.dp, CircleShape).background(primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(10.dp).background(Color.White, CircleShape))
            }
            Canvas(modifier = Modifier.size(width = 3.dp, height = needle)) {
                drawLine(primary, Offset(size.width / 2, 0f), Offset(size.width / 2, size.height), strokeWidth = size.width)
            }
        }
    }
}

@Composable
private fun NearbySheet(
    spots: List<SpotOut>,
    loaded: Boolean,
    addresses: Map<String, String>,
    reference: LatLng,
    myUserId: String?,
    onSpotClick: (SpotOut) -> Unit,
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, bottom = 16.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (!loaded) stringResource(R.string.map_nearby_searching)
                    else if (spots.isEmpty()) stringResource(R.string.map_nearby_none)
                    else pluralStringResource(R.plurals.map_nearby_count, spots.size, spots.size),
                    style = MaterialTheme.typography.titleLarge,
                )
                val nearest = spots.firstOrNull()
                Text(
                    if (!loaded) stringResource(R.string.map_nearby_searching_hint)
                    else if (nearest == null) stringResource(R.string.map_nearby_empty_hint)
                    else stringResource(
                        R.string.map_nearest,
                        formatDistance(context, LatLng(nearest.lat, nearest.lng).distanceTo(reference)),
                        MarkerBitmaps.relativeTimeLong(context, nearest.reportedAt),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (spots.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            LazyColumn(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                items(spots, key = { it.id }) { spot ->
                    SpotRow(spot, spot.address ?: addresses[spot.id], reference, myUserId, onClick = { onSpotClick(spot) })
                }
            }
        }
    }
}

@Composable
private fun SpotRow(spot: SpotOut, address: String?, reference: LatLng, myUserId: String?, onClick: () -> Unit) {
    val context = LocalContext.current
    val color = Color(android.graphics.Color.parseColor(pinColor(spot, myUserId)))
    val disabled = spot.spotType == "disabled"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            if (spot.photoUrl != null) {
                // The reporter's photo, when there is one — the quickest
                // read of "is this spot real and will my car fit".
                coil.compose.AsyncImage(
                    model = com.parkit.app.ApiConfig.BASE_URL.trimEnd('/') + spot.photoUrl,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    if (disabled) Icons.AutoMirrored.Filled.Accessible else Icons.Filled.LocalParking,
                    contentDescription = null,
                    tint = color,
                )
            }
        }
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
            // The address is the headline — it's what you'd actually drive
            // to. Type, freshness and status read as one quiet detail line.
            Text(
                address ?: stringResource(R.string.map_locating),
                style = MaterialTheme.typography.titleSmall,
                color = if (address == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOfNotNull(
                    stringResource(if (disabled) R.string.map_spot_kind_disabled else R.string.map_spot_kind_regular),
                    MarkerBitmaps.relativeTimeLong(context, spot.reportedAt),
                    when {
                        spot.status == "claimed" -> stringResource(R.string.report_status_claimed)
                        spot.reporterId == myUserId -> stringResource(R.string.map_spot_yours)
                        else -> null
                    },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            formatDistance(context, LatLng(spot.lat, spot.lng).distanceTo(reference)),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

private fun formatDistance(context: android.content.Context, meters: Double): String =
    if (meters < 1000) context.getString(R.string.distance_m, (Math.round(meters / 10.0) * 10).toInt())
    else context.getString(R.string.distance_km, meters / 1000.0)

/** Speech-bubble outline: rounded body plus a centered tail, as ONE path,
 * so a single shadow follows the whole silhouette (two separate shapes
 * left a visible seam where the tail met the pill). */
private class BubbleShape(private val cornerPx: Float, private val tailWidthPx: Float, private val tailHeightPx: Float) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): androidx.compose.ui.graphics.Outline {
        val bodyBottom = size.height - tailHeightPx
        val path = androidx.compose.ui.graphics.Path().apply {
            addRoundRect(
                androidx.compose.ui.geometry.RoundRect(
                    0f, 0f, size.width, bodyBottom,
                    androidx.compose.ui.geometry.CornerRadius(cornerPx, cornerPx),
                ),
            )
            val cx = size.width / 2f
            moveTo(cx - tailWidthPx / 2f, bodyBottom - 1f)
            quadraticBezierTo(cx - tailWidthPx / 6f, bodyBottom + tailHeightPx * 0.2f, cx, size.height)
            quadraticBezierTo(cx + tailWidthPx / 6f, bodyBottom + tailHeightPx * 0.2f, cx + tailWidthPx / 2f, bodyBottom - 1f)
            close()
        }
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}

/** "Report a spot here" — anchored to the center pin, because the pin is
 * exactly the spot being reported. Springs in once the map settles, gives
 * press feedback, and its arrow (auto-mirrored for Hebrew) reads as
 * "tap to continue" rather than as a label. */
@Composable
private fun ReportHereBubble(visible: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    val shape = remember(density) {
        with(density) { BubbleShape(cornerPx = 22.dp.toPx(), tailWidthPx = 18.dp.toPx(), tailHeightPx = 8.dp.toPx()) }
    }
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.95f else 1f, label = "bubblePress")
    val bodyHeight = 44.dp
    val tail = 8.dp

    androidx.compose.animation.AnimatedVisibility(
        visible = visible,
        enter = androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(160)) +
            androidx.compose.animation.scaleIn(
                spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMediumLow),
                initialScale = 0.6f,
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f),
            ),
        exit = androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)) +
            androidx.compose.animation.scaleOut(
                targetScale = 0.6f,
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f),
            ),
        // Pin head top sits 46dp above center; the tail tip lands 6dp above it.
        modifier = modifier.offset(y = -(46.dp + 6.dp + (bodyHeight + tail) / 2)),
    ) {
        Surface(
            onClick = onClick,
            interactionSource = interaction,
            shape = shape,
            color = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            shadowElevation = 8.dp,
            modifier = Modifier.height(bodyHeight + tail).graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
            },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = tail).height(bodyHeight).padding(start = 6.dp, end = 10.dp),
            ) {
                Box(
                    modifier = Modifier.size(32.dp).background(MaterialTheme.colorScheme.onPrimary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                }
                Text(
                    stringResource(R.string.map_report_here),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                    modifier = Modifier.padding(start = 10.dp, end = 2.dp),
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** Branded loader shown only until the first fully-rendered map frame —
 * a real signal from the engine now, not a fixed timer. */
@Composable
private fun BrandedMapLoader() {
    val rotation by androidx.compose.animation.core.rememberInfiniteTransition(label = "mapLoaderRotation").animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1000, easing = androidx.compose.animation.core.LinearEasing),
        ),
        label = "mapLoaderAngle",
    )
    val primary = MaterialTheme.colorScheme.primary

    Surface(shape = CircleShape, shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
        Box(modifier = Modifier.size(64.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize().padding(7.dp).graphicsLayer { rotationZ = rotation }) {
                drawArc(
                    brush = androidx.compose.ui.graphics.Brush.sweepGradient(listOf(primary.copy(alpha = 0f), primary.copy(alpha = 0.25f), primary)),
                    startAngle = 0f,
                    sweepAngle = 300f,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round),
                )
            }
            Image(painter = painterResource(R.drawable.ic_logo), contentDescription = null, modifier = Modifier.size(26.dp))
        }
    }
}
