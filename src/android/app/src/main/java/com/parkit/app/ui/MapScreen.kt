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
// emulator/device defaulting to Mountain View, CA) — see useDeviceLocation().
private val SERVICE_AREA_CENTER = TEL_AVIV
private const val SERVICE_AREA_RADIUS_M = 350_000.0

private const val SOURCE_SPOTS = "spots"
private const val LAYER_CLUSTERS = "spots-clusters"
private const val LAYER_CLUSTER_COUNT = "spots-cluster-count"
private const val LAYER_PINS = "spots-pins"

// Google-Maps-adjacent status colors: green = go, brand blue = yours,
// amber = someone is already on their way.
private const val COLOR_FREE = "#188038"
private const val COLOR_MINE = "#1B4F91"
private const val COLOR_CLAIMED = "#E8710A"

private val SHEET_PEEK = 114.dp

private fun pinColor(spot: SpotOut, myUserId: String?): String = when {
    spot.status == "claimed" -> COLOR_CLAIMED
    spot.reporterId == myUserId -> COLOR_MINE
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
    var deviceLocation by remember { mutableStateOf<LatLng?>(null) }

    var geocodeTarget by remember { mutableStateOf(TEL_AVIV) }
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
    val sortedSpots = visibleSpots.sortedBy { LatLng(it.lat, it.lng).distanceTo(reference) }

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
        delay(350) // let a quick follow-up drag cancel this before it costs a request
        val result = com.parkit.app.api.Geocoder.reverse(target.latitude, target.longitude)
        resolvedAddress = result?.shortLabel()
        resolvedStreet = result?.streetLabel()
        addressFailed = result == null
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

    fun useDeviceLocation() {
        val lm = context.getSystemService(LocationManager::class.java)
        val loc = try {
            lm?.getLastKnownLocation(LocationManager.GPS_PROVIDER) ?: lm?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (_: SecurityException) {
            null
        }
        if (loc != null) {
            val here = LatLng(loc.latitude, loc.longitude)
            // ParkIt only operates in Israel. A device/emulator with a stale or
            // default GPS fix (classically Mountain View, CA on emulators) would
            // otherwise yank a first-time user — or every demo — away from the
            // service area onto an irrelevant map.
            if (here.distanceTo(SERVICE_AREA_CENTER) <= SERVICE_AREA_RADIUS_M) {
                deviceLocation = here
                flyTo(here)
                geocodeTarget = here
            }
        }
        enableLocationPuck()
    }

    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) useDeviceLocation()
    }
    LaunchedEffect(Unit) { locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION) }

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
            m.cameraPosition = CameraPosition.Builder().target(geocodeTarget).zoom(STREET_LEVEL_ZOOM).build()
            m.uiSettings.apply {
                isLogoEnabled = false
                isCompassEnabled = true
                isRotateGesturesEnabled = true
                isTiltGesturesEnabled = false
                setCompassMargins(0, with(density) { 150.dp.roundToPx() }, with(density) { 16.dp.roundToPx() }, 0)
                setAttributionMargins(with(density) { 12.dp.roundToPx() }, 0, 0, with(density) { (SHEET_PEEK + 56.dp).roundToPx() })
            }
            m.addOnCameraMoveStartedListener {
                cameraMoving = true
                addressLoading = true
            }
            m.addOnCameraIdleListener {
                cameraMoving = false
                geocodeTarget = m.cameraPosition.target ?: geocodeTarget
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

            // Both floating actions ride the sheet's top edge (Google Maps'
            // locate-button pattern) so they move with it and are never
            // covered. Report is its own one-tap button on the map — Waze's
            // model — not an action buried inside the nearby-spots sheet.
            val fabHeight = 60.dp
            ReportFab(
                onClick = { showReportFlow = true },
                height = fabHeight,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        val top = sheetTopPx?.toInt() ?: 0
                        IntOffset(-16.dp.roundToPx(), top - fabHeight.roundToPx() - 16.dp.roundToPx())
                    },
            )

            val buttonSize = 48.dp
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset {
                        val top = sheetTopPx?.toInt() ?: 0
                        IntOffset(-22.dp.roundToPx(), top - fabHeight.roundToPx() - buttonSize.roundToPx() - 28.dp.roundToPx())
                    }
                    .size(buttonSize)
                    .clickable {
                        if (hasLocationPermission()) useDeviceLocation() else locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
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
        Box(modifier = Modifier.size(40.dp).background(color.copy(alpha = 0.12f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(
                if (disabled) Icons.AutoMirrored.Filled.Accessible else Icons.Filled.LocalParking,
                contentDescription = null,
                tint = color,
            )
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

/** The app's single most important action, as one big tap target on the
 * map itself: brand-colored pill, icon + label (an icon alone was easy to
 * miss), bouncing in once on entry so the eye finds it immediately. */
@Composable
private fun ReportFab(onClick: () -> Unit, height: androidx.compose.ui.unit.Dp, modifier: Modifier = Modifier) {
    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val scale by animateFloatAsState(
        targetValue = if (entered) 1f else 0.4f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "reportFabEntrance",
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 10.dp,
        modifier = modifier.height(height).graphicsLayer { scaleX = scale; scaleY = scale },
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 18.dp, end = 24.dp)) {
            Box(
                modifier = Modifier.size(34.dp).background(MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.18f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(24.dp))
            }
            Text(
                stringResource(R.string.map_report_fab),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 10.dp),
            )
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
