@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.parkit.app.ui

import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.joinAll
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
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.CheckCircle
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
import androidx.compose.ui.draw.alpha
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
import com.parkit.app.api.isUnauthorized
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
private const val DEFAULT_RADIUS_M = 800 // ~10 min walk; drag the pin to search elsewhere
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
private const val FIX_TIMEOUT_MS = 8_000L
private const val QUICK_FIX_MAX_AGE_MS = 10 * 60_000L
private const val PRECISE_FIX_TIMEOUT_MS = 20_000L
private const val PRECISE_JUMP_M = 60.0
private const val LAST_KNOWN_MAX_AGE_MS = 30 * 60_000L

private fun inServiceArea(p: LatLng) = p.distanceTo(SERVICE_AREA_CENTER) <= SERVICE_AREA_RADIUS_M

/** Precise *or* approximate — Android 12+ lets the user grant only
 * approximate, and that's still plenty to centre the map on them. */
private fun hasAnyLocation(context: android.content.Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

private fun hasPreciseLocation(context: android.content.Context) =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

private val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

private fun locationServicesOn(context: android.content.Context): Boolean {
    val lm = context.getSystemService(LocationManager::class.java) ?: return false
    return androidx.core.location.LocationManagerCompat.isLocationEnabled(lm)
}

/** The freshest cached fix any provider has, if it's recent and in Israel —
 * lets the map open where the user actually is with zero wait. On a real
 * phone this is often empty (nothing used GPS lately), which is exactly why
 * it can't be the only source. */
@SuppressLint("MissingPermission")
private fun recentLastKnown(context: android.content.Context): LatLng? {
    if (!hasAnyLocation(context)) return null
    val lm = context.getSystemService(LocationManager::class.java) ?: return null
    val best = lm.getProviders(true)
        .mapNotNull { runCatching { lm.getLastKnownLocation(it) }.getOrNull() }
        .maxByOrNull { it.time } ?: return null
    if (System.currentTimeMillis() - best.time > LAST_KNOWN_MAX_AGE_MS) return null
    return LatLng(best.latitude, best.longitude).takeIf { inServiceArea(it) }
}

/** A fresh fix, fast: Google's fused provider on Wi-Fi + cell (a second or
 * two, even indoors — waiting for GPS here is what made the first fix take
 * 10s+), then the plain Android providers for phones without Google
 * services, then any cached fix. GPS precision comes after, in [locate]. */
@SuppressLint("MissingPermission")
private suspend fun freshFix(context: android.content.Context): LatLng? {
    if (!hasAnyLocation(context)) return null
    return firstFusedFix(context)
        ?: kotlinx.coroutines.withTimeoutOrNull(FIX_TIMEOUT_MS) { platformFix(context) }
        ?: fusedLastKnown(context)
        ?: recentLastKnown(context)
}

/** Wi-Fi/cell and GPS raced: whichever answers first wins, so a phone with
 * Wi-Fi off doesn't sit out the whole timeout before GPS even starts. */
private suspend fun firstFusedFix(context: android.content.Context): LatLng? = kotlinx.coroutines.coroutineScope {
    val first = kotlinx.coroutines.CompletableDeferred<LatLng?>()
    val attempts = listOfNotNull(false, true.takeIf { hasPreciseLocation(context) }).map { precise ->
        launch { fusedFix(context, precise)?.let { first.complete(it) } }
    }
    launch { attempts.joinAll(); first.complete(null) }
    kotlinx.coroutines.withTimeoutOrNull(FIX_TIMEOUT_MS) { first.await() }
        .also { coroutineContext.cancelChildren() }
}

@SuppressLint("MissingPermission")
private suspend fun fusedFix(context: android.content.Context, precise: Boolean): LatLng? = try {
    val client = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
    val priority = if (precise) {
        com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY
    } else {
        com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY
    }
    val cts = com.google.android.gms.tasks.CancellationTokenSource()
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        cont.invokeOnCancellation { cts.cancel() }
        client.getCurrentLocation(priority, cts.token)
            .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc?.let { LatLng(it.latitude, it.longitude) }) { } }
            .addOnFailureListener { if (cont.isActive) cont.resume(null) { } }
    }
} catch (_: Exception) {
    null // no Google Play services on this phone
}

/** Google's cached fix — instant, and on a real phone usually only minutes
 * old because other apps keep it fresh. [maxAgeMs] drops older ones. */
@SuppressLint("MissingPermission")
private suspend fun fusedLastKnown(context: android.content.Context, maxAgeMs: Long = Long.MAX_VALUE): LatLng? = try {
    val client = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
    kotlinx.coroutines.withTimeoutOrNull(3_000) {
        kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            client.lastLocation
                .addOnSuccessListener { loc ->
                    val fresh = loc?.takeIf { System.currentTimeMillis() - it.time <= maxAgeMs }
                    if (cont.isActive) cont.resume(fresh?.let { LatLng(it.latitude, it.longitude) }) { }
                }
                .addOnFailureListener { if (cont.isActive) cont.resume(null) { } }
        }
    }
} catch (_: Exception) {
    null
}

/** Asks every enabled Android provider for a fix; first one wins. */
@SuppressLint("MissingPermission")
private suspend fun platformFix(context: android.content.Context): LatLng? {
    val lm = context.getSystemService(LocationManager::class.java) ?: return null
    val providers = listOf(LocationManager.FUSED_PROVIDER, LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
    if (providers.isEmpty()) return null
    return kotlinx.coroutines.suspendCancellableCoroutine { cont ->
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

private const val SOURCE_SPOTS = "spots"
private const val LAYER_CLUSTERS = "spots-clusters"
private const val LAYER_CLUSTER_COUNT = "spots-cluster-count"
private const val LAYER_PINS = "spots-pins"


// Google-Maps-adjacent status colors: green = go, brand blue = yours,
// amber = someone is already on their way.
private const val SOURCE_LOTS = "parking-lots"
private const val LAYER_LOTS = "parking-lots-pins"
private const val LOT_ICON = "lot-pin"
private const val LOTS_RADIUS_M = 1500
private const val COLOR_FREE = "#188038"
private const val COLOR_MINE = "#1B4F91"
private const val COLOR_CLAIMED = "#E8710A"
private const val COLOR_LEGEND = "#7B3FE4"

private val SHEET_PEEK = 114.dp
private const val CLAIM_HOLD_MS = 120_000L // matches the backend's CLAIM_TTL_SECONDS

private fun pinColor(spot: SpotOut, myUserId: String?): String = when {
    spot.status == "claimed" -> COLOR_CLAIMED
    spot.reporterId == myUserId -> COLOR_MINE
    // Legend-level perk: a distinct purple pin (see levels.py on the backend).
    spot.reporterLevel == "legend" -> COLOR_LEGEND
    else -> freshnessGreen(ageMinutes(spot.reportedAt))
}

/** Pin frame for the reporter's level; legends already have their own pin. */
private fun levelFrame(level: String): String? = when (level) {
    "bronze" -> "#B0703C"
    "silver" -> "#9AA7B8"
    "gold" -> "#D4A017"
    else -> null
}

/** A free spot starts deep green and pales as it waits, so the map itself
 * shows how long each spot has been free — no need to read the tags. Steps
 * rather than a smooth blend: five shades the eye can actually tell apart
 * (and five cached bitmaps per tag, not one per minute). */
private fun freshnessGreen(minutes: Long): String = when {
    minutes < 5 -> "#0B5E2B"
    minutes < 15 -> COLOR_FREE
    minutes < 30 -> "#2E9E4F"
    minutes < 45 -> "#55B56E"
    else -> "#7EC791"
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
    val driverPrefs = sessionStore.driver.value
    val homeCentre = driverPrefs.homeLat?.let { lat -> driverPrefs.homeLng?.let { lng -> LatLng(lat, lng) } }

    var map by remember { mutableStateOf<MapLibreMap?>(null) }
    var style by remember { mutableStateOf<Style?>(null) }
    var spots by remember { mutableStateOf<List<SpotOut>>(emptyList()) }
    // "No spots nearby" must mean the server said so — not "still loading".
    var spotsLoaded by remember { mutableStateOf(false) }
    // The server couldn't be reached: say so (and retry), instead of an
    // empty list that would read as "no free spots here".
    var spotsFailed by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }
    var selectedSpot by remember { mutableStateOf<SpotOut?>(null) }
    // The spot this user said "I'm heading there" to. The server hides
    // claimed spots from everyone (so nobody else is sent there), so the
    // claimer keeps it locally — on the map and in a trip card — until they
    // report what happened or the 2-minute hold runs out.
    var activeClaim by remember { mutableStateOf<SpotOut?>(null) }
    var claimEndsAt by remember { mutableStateOf(0L) }
    var showReportFlow by remember { mutableStateOf(false) }
    // The driver's own level/points, for the avatar ring and level-up moments.
    var myProfile by remember { mutableStateOf<com.parkit.app.api.ProfileOut?>(null) }
    var levelUp by remember { mutableStateOf<ParkItLevel?>(null) }
    suspend fun refreshMyProfile() {
        val p = runCatching { api.myProfile() }.getOrNull() ?: return
        myProfile = p
        val reached = ParkItLevel.of(p.level)
        val seen = sessionStore.lastSeenLevel?.let { ParkItLevel.of(it) }
        if (seen != null && reached.minPoints > seen.minPoints) levelUp = reached
        sessionStore.lastSeenLevel = reached.key
    }
    LaunchedEffect(Unit) { refreshMyProfile() }
    var typeFilter by remember { mutableStateOf<String?>(null) } // null = All
    // Paid car parks: off by default — the map is about free street parking,
    // lots only appear when the driver asks for them.
    var showLots by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(false) }
    var lots by remember { mutableStateOf<List<com.parkit.app.api.ParkingLotOut>>(emptyList()) }
    var selectedLot by remember { mutableStateOf<com.parkit.app.api.ParkingLotOut?>(null) }
    var cameraMoving by remember { mutableStateOf(false) }
    var mapRendered by remember { mutableStateOf(false) }
    // Where to open the map: a recent cached fix if there is one; otherwise
    // the country overview until a fresh fix arrives (see locate()).
    // Open where the phone is (cached fix) or was last time — never on a
    // made-up spot while the fresh fix is still coming.
    val startFix = remember {
        recentLastKnown(context)
            ?: sessionStore.lastFix?.let { (lat, lng) -> LatLng(lat, lng) }?.takeIf { inServiceArea(it) }
    }
    var deviceLocation by remember { mutableStateOf(startFix) }
    var locating by remember { mutableStateOf(true) }
    var userTookOver by remember { mutableStateOf(false) }

    var geocodeTarget by remember { mutableStateOf(startFix ?: homeCentre ?: TEL_AVIV) }
    var resolvedAddress by remember { mutableStateOf<String?>(null) }
    var resolvedStreet by remember { mutableStateOf<String?>(null) }
    // True from the moment the map starts moving until the new pin address
    // is known — the bar shows a loading state instead of a stale or
    // raw-coordinate label.
    var addressLoading by remember { mutableStateOf(true) }
    var addressFailed by remember { mutableStateOf(false) }

    val visibleSpots = (typeFilter?.let { f -> spots.filter { it.spotType == f } } ?: spots).let { list ->
        val claim = activeClaim
        if (claim != null && list.none { it.id == claim.id }) list + claim else list
    }
    // "Nearby" means near *you* when we know where you are, otherwise near
    // the pin — the same reference point the search radius uses.
    val reference = deviceLocation ?: geocodeTarget
    // Spots this driver can't use (disabled bay without a permit, too small
    // for their car) go last; otherwise purely by distance — levels are
    // recognition, they don't reorder anyone's list.
    val sortedSpots = visibleSpots.sortedWith(
        compareBy<SpotOut>(
            { fitOf(it, driverPrefs.preferences).unusable },
            { LatLng(it.lat, it.lng).distanceTo(reference) },
        ),
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
    LaunchedEffect(geocodeTarget, reloadTick) {
        delay(300)
        while (true) {
            try {
                spots = api.nearbySpots(geocodeTarget.latitude, geocodeTarget.longitude, DEFAULT_RADIUS_M)
                spotsLoaded = true
                spotsFailed = false
                break
            } catch (e: Exception) {
                if (e.isUnauthorized()) { onLoggedOut(); break }
                spotsFailed = true
                delay(10_000) // keep trying quietly; the sheet shows the problem and a retry button
            }
        }
    }

    fun flyTo(target: LatLng, zoom: Double = STREET_LEVEL_ZOOM) {
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(target, zoom), 650)
    }

    fun hasLocationPermission() = hasAnyLocation(context)

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

    /** Right after "I parked": thanks + a one-tap hand-off to Pango in case
     * it's blue-white. A snackbar, not a dialog — it goes away by itself
     * and never blocks the map. */
    suspend fun offerPango() {
        val result = snackbarHostState.showSnackbar(
            message = context.getString(R.string.parked_pango_prompt),
            actionLabel = context.getString(R.string.parked_pango_action),
            withDismissAction = true,
            duration = androidx.compose.material3.SnackbarDuration.Long,
        )
        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) PangoLink.open(context)
    }

    /** No usable fix: open on the driver's preferred city, else Tel Aviv. */
    fun showTelAvivFallback(message: Int?) {
        if (deviceLocation == null && !userTookOver) {
            val target = homeCentre ?: TEL_AVIV
            geocodeTarget = target
            flyTo(target)
        }
        message?.let { scope.launch { snackbarHostState.showSnackbar(context.getString(it)) } }
    }

    /** Find the user and fly there. On launch it doesn't override a user who
     * already started panning; from the locate button it always flies. */
    fun locate(fromButton: Boolean = false) {
        enableLocationPuck()
        if (!locationServicesOn(context)) {
            // Permission is fine but the phone's location switch is off —
            // no provider will ever answer, so say so and offer the switch.
            showTelAvivFallback(null)
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = context.getString(R.string.location_off),
                    actionLabel = context.getString(R.string.location_turn_on),
                    duration = androidx.compose.material3.SnackbarDuration.Long,
                )
                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                    context.startActivity(android.content.Intent(android.provider.Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
            }
            return
        }
        fun use(fix: LatLng, fly: Boolean) {
            deviceLocation = fix
            sessionStore.lastFix = fix.latitude to fix.longitude
            if (fly && (fromButton || !userTookOver)) {
                geocodeTarget = fix
                flyTo(fix)
            }
        }
        scope.launch {
            locating = true
            // 1. Instant: where the phone already knows it is.
            fusedLastKnown(context, maxAgeMs = QUICK_FIX_MAX_AGE_MS)?.takeIf { inServiceArea(it) }?.let { use(it, fly = true) }
            // 2. Fast: Wi-Fi + cell, a second or two.
            val fix = freshFix(context)
            locating = false
            when {
                fix != null && inServiceArea(fix) -> {
                    use(fix, fly = true)
                    // 3. Precise: GPS in the background; move only if it's meaningfully off.
                    if (hasPreciseLocation(context)) {
                        launch {
                            val precise = kotlinx.coroutines.withTimeoutOrNull(PRECISE_FIX_TIMEOUT_MS) { fusedFix(context, precise = true) }
                                ?.takeIf { inServiceArea(it) } ?: return@launch
                            use(precise, fly = precise.distanceTo(fix) > PRECISE_JUMP_M && !userTookOver)
                        }
                    }
                }
                // ParkIt only operates in Israel; an out-of-country fix (or the
                // emulator's default Mountain View) must not move the map away.
                fix != null -> showTelAvivFallback(R.string.map_outside_service_area)
                // Already placed (cached fix, or last session's) — keep it
                // rather than throwing the map back to the fallback.
                deviceLocation != null -> if (fromButton) flyTo(deviceLocation!!)
                else -> showTelAvivFallback(R.string.map_location_unavailable)
            }
        }
    }

    // Ask for precise and approximate together: on Android 12+ asking for
    // precise alone is ignored, and "approximate" must count as a yes.
    val locationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it } || hasAnyLocation(context)) {
            locate()
        } else {
            locating = false
            showTelAvivFallback(null)
            // Say why it matters and how to fix it — after a "don't ask again",
            // Android won't show the system prompt any more, only Settings can.
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = context.getString(R.string.location_denied),
                    actionLabel = context.getString(R.string.location_open_settings),
                    duration = androidx.compose.material3.SnackbarDuration.Long,
                )
                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                    context.startActivity(
                        android.content.Intent(
                            android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            android.net.Uri.fromParts("package", context.packageName, null),
                        ),
                    )
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        if (hasAnyLocation(context)) locate() else locationPermission.launch(LOCATION_PERMISSIONS)
    }
    // Back from Settings (permission granted / location switched on there)?
    // Try again by itself instead of leaving the map on the fallback.
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME && deviceLocation == null && !locating &&
                hasAnyLocation(context) && locationServicesOn(context)
            ) {
                locate()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
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
                if (hit == null) {
                    // Free spots win a tap; only then try the lots underneath.
                    val lotId = m.queryRenderedFeatures(point, LAYER_LOTS).firstOrNull()?.getNumberProperty("id")?.toInt()
                        ?: return@addOnMapClickListener false
                    selectedLot = lots.firstOrNull { it.id == lotId }
                    return@addOnMapClickListener true
                }
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
            installSpotLayers(context, s, isDarkTheme)
            style = s
            enableLocationPuck()
        }
    }

    LaunchedEffect(showLots, geocodeTarget) {
        if (!showLots) return@LaunchedEffect
        lots = try {
            api.parkingLots(geocodeTarget.latitude, geocodeTarget.longitude, LOTS_RADIUS_M)
        } catch (_: Exception) {
            lots // keep what's shown; the next camera stop retries
        }
    }
    LaunchedEffect(style, showLots, lots) {
        val s = style ?: return@LaunchedEffect
        val features = if (showLots) {
            lots.map { lot ->
                Feature.fromGeometry(Point.fromLngLat(lot.lng, lot.lat)).apply { addNumberProperty("id", lot.id) }
            }
        } else {
            emptyList()
        }
        s.getSourceAs<GeoJsonSource>(SOURCE_LOTS)?.setGeoJson(FeatureCollection.fromFeatures(features))
    }

    // Push spot data into the map whenever it, the filter, or the selection changes.
    LaunchedEffect(style, visibleSpots, selectedSpot?.id) {
        val s = style ?: return@LaunchedEffect
        val features = visibleSpots.map { spot ->
            val color = pinColor(spot, myUserId)
            val tag = MarkerBitmaps.relativeTimeShort(context, spot.reportedAt)
            val disabled = spot.spotType == "disabled"
            val hot = spot.hotMinutes != null && spot.status == "active"
            val frame = levelFrame(spot.reporterLevel)
            val iconId = "pin-$color-$tag-${if (disabled) "d" else "p"}${if (hot) "-hot" else ""}-${frame ?: "w"}"
            if (s.getImage(iconId) == null) s.addImage(iconId, MarkerBitmaps.pin(context, color, tag, disabled, hot, frame))
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
    // Back with the spot list open closes the list, not the whole app.
    androidx.activity.compose.BackHandler(enabled = sheetState.currentValue == SheetValue.Expanded) {
        scope.launch { sheetState.partialExpand() }
    }

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
                failed = spotsFailed,
                onRetry = { spotsFailed = false; reloadTick++ },
                addresses = spotAddresses,
                reference = reference,
                myUserId = myUserId,
                prefs = driverPrefs.preferences,
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
                    avatarId = myProfile?.avatar,
                    level = myProfile?.let { ParkItLevel.of(it.level) },
                    levelProgress = myProfile?.let(::levelProgress),
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
                    LotsChip(checked = showLots) {
                        showLots = !showLots
                        if (!showLots) selectedLot = null
                    }
                }
                activeClaim?.let { claim ->
                    ClaimTripCard(
                        spot = claim,
                        endsAt = claimEndsAt,
                        onNavigate = {
                            val uri = android.net.Uri.parse("geo:${claim.lat},${claim.lng}?q=${claim.lat},${claim.lng}")
                            context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
                        },
                        onFeedback = { type ->
                            scope.launch {
                                try {
                                    api.submitFeedback(claim.id, com.parkit.app.api.FeedbackCreate(type))
                                    activeClaim = null
                                    refreshSpotsNow()
                                    if (type == "confirmed_taken") offerPango() else snackbarHostState.showSnackbar(context.getString(R.string.claim_taken_thanks))
                                } catch (e: Exception) {
                                    snackbarHostState.showSnackbar(e.friendlyMessage(context))
                                }
                            }
                        },
                        onExpired = {
                            activeClaim = null
                            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.claim_expired)) }
                            refreshSpotsNow()
                        },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = locating && !userTookOver,
                enter = androidx.compose.animation.fadeIn(),
                exit = androidx.compose.animation.fadeOut(),
                // Centre while there's nothing to see; once a first fix is on
                // the map, under the top bar so it doesn't cover the pin.
                modifier = if (deviceLocation == null) Modifier.align(Alignment.Center) else Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 128.dp),
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
                        if (hasLocationPermission()) locate(fromButton = true) else locationPermission.launch(LOCATION_PERMISSIONS)
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
            defaultVehicleSize = driverPrefs.preferences.vehicleSize,
            onDismiss = { showReportFlow = false },
            onReported = {
                showReportFlow = false
                scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.map_reported_snackbar)) }
                scope.launch { refreshMyProfile() }
                refreshSpotsNow()
            },
            onSessionExpired = onLoggedOut,
        )
    }

    levelUp?.let { level -> LevelUpDialog(level, onDismiss = { levelUp = null }) }

    selectedLot?.let { lot ->
        LotSheet(lot = lot, onDismiss = { selectedLot = null })
    }

    selectedSpot?.let { spot ->
        SpotActionsSheet(
            api = api,
            spot = spot,
            myUserId = myUserId,
            fit = fitOf(spot, driverPrefs.preferences),
            distanceLabel = context.getString(
                R.string.spot_distance_away,
                formatDistance(context, LatLng(spot.lat, spot.lng).distanceTo(reference)),
            ),
            onDismiss = { selectedSpot = null },
            onChanged = {
                selectedSpot = null
                refreshSpotsNow()
            },
            onParked = { scope.launch { offerPango() } },
            onClaimed = { claimed ->
                selectedSpot = null
                activeClaim = claimed
                claimEndsAt = System.currentTimeMillis() + CLAIM_HOLD_MS
                refreshSpotsNow()
            },
            onSessionExpired = onLoggedOut,
        )
    }
}

/** Native GeoJSON clustering + symbol layers — the map engine itself
 * groups, positions, and hit-tests pins (on the GPU, smoothly through
 * zoom), instead of Kotlin re-bucketing bitmaps on every recomposition. */
private fun installSpotLayers(context: android.content.Context, style: Style, dark: Boolean) {
    // Car parks sit *under* the spot pins, and only from street zoom: from
    // further out hundreds of them would bury the free spots.
    style.addImage(LOT_ICON, MarkerBitmaps.lotPin(context))
    style.addSource(GeoJsonSource(SOURCE_LOTS, FeatureCollection.fromFeatures(emptyList())))
    style.addLayer(
        SymbolLayer(LAYER_LOTS, SOURCE_LOTS).withProperties(
            PropertyFactory.iconImage(LOT_ICON),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
        ).apply { minZoom = 14f },
    )
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
            // Age is shown by the pin's shade of green (see freshnessGreen),
            // so pins stay fully opaque and readable.
            PropertyFactory.symbolSortKey(Expression.get("age")),
        ),
    )
}

/** Google-Maps-style floating search bar, repurposed as "where the pin
 * is" — the address you'd be reporting, plus your avatar (profile). */
@Composable
private fun LocationBar(
    address: String?,
    loading: Boolean,
    failed: Boolean,
    displayName: String?,
    avatarId: String?,
    level: ParkItLevel?,
    levelProgress: Float?,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
            // Avatar in a ring that fills toward the next level, in the level's
            // colour — progress is visible every time the map is open.
            val ringColor = level?.color ?: MaterialTheme.colorScheme.primary
            val track = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onOpenProfile),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                    val stroke = 3.dp.toPx()
                    val inset = stroke / 2
                    val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
                    val topLeft = androidx.compose.ui.geometry.Offset(inset, inset)
                    drawArc(track, 0f, 360f, false, topLeft, arcSize, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                    drawArc(
                        ringColor, -90f, 360f * (levelProgress ?: 0f), false, topLeft, arcSize,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round),
                    )
                }
                Avatar(displayName, MaterialTheme.colorScheme.primary, size = 38.dp, textColor = MaterialTheme.colorScheme.onPrimary, avatarId = avatarId)
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

/** An on/off toggle, not one of the single-choice type filters above: a
 * checkbox and the car-park slate, so it reads as "also show lots". */
@Composable
private fun LotsChip(checked: Boolean, onClick: () -> Unit) {
    val slate = Color(android.graphics.Color.parseColor(MarkerBitmaps.LOT_COLOR))
    Surface(
        shape = RoundedCornerShape(50),
        color = if (checked) slate else MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
        modifier = Modifier.height(36.dp).clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp)) {
            Icon(
                if (checked) Icons.Filled.CheckBox else Icons.Filled.CheckBoxOutlineBlank,
                contentDescription = null,
                tint = if (checked) Color.White else slate,
                modifier = Modifier.size(18.dp),
            )
            Text(
                stringResource(R.string.map_filter_lots),
                style = MaterialTheme.typography.labelLarge,
                color = if (checked) Color.White else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 6.dp),
            )
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
    prefs: com.parkit.app.api.Preferences,
    failed: Boolean,
    onRetry: () -> Unit,
    onSpotClick: (SpotOut) -> Unit,
) {
    val context = LocalContext.current
    if (failed) {
        // Offline / server down: a clear state with a way out, never a fake "no spots".
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 16.dp, bottom = 16.dp),
        ) {
            Icon(Icons.Filled.CloudOff, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(28.dp))
            Column(modifier = Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(stringResource(R.string.map_offline_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.map_offline_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            androidx.compose.material3.FilledTonalButton(onClick = onRetry, shape = RoundedCornerShape(50)) {
                Text(stringResource(R.string.map_offline_retry))
            }
        }
        return
    }
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
                // The list isn't purely by distance, so "nearest" is computed —
                // and it's the nearest one this driver can actually use.
                val nearest = (spots.filterNot { fitOf(it, prefs).unusable }.ifEmpty { spots })
                    .minByOrNull { LatLng(it.lat, it.lng).distanceTo(reference) }
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
                    SpotRow(spot, spot.address ?: addresses[spot.id], reference, myUserId, fitOf(spot, prefs), onClick = { onSpotClick(spot) })
                }
            }
        }
    }
}

@Composable
private fun SpotRow(spot: SpotOut, address: String?, reference: LatLng, myUserId: String?, fit: DriverFit, onClick: () -> Unit) {
    val context = LocalContext.current
    val color = Color(android.graphics.Color.parseColor(pinColor(spot, myUserId)))
    val disabled = spot.spotType == "disabled"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp)
            // Still listed (the driver may know better), just visibly secondary.
            .alpha(if (fit.unusable) 0.55f else 1f),
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
                    spot.hotMinutes?.let { stringResource(R.string.hot_short) },
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
            fitNote(fit)?.let { (text, color) ->
                Text(text, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            formatDistance(context, LatLng(spot.lat, spot.lng).distanceTo(reference)),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** The one line a list row has room for, most important first. */
@Composable
private fun fitNote(fit: DriverFit): Pair<String, Color>? = when {
    fit.needsDisabledPermit -> stringResource(R.string.fit_needs_disabled_permit) to MaterialTheme.colorScheme.onSurfaceVariant
    fit.tooSmall -> stringResource(R.string.fit_too_small) to MaterialTheme.colorScheme.onSurfaceVariant
    else -> null
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

/** "You're on your way" — stays on screen after "On my way" so the
 * claimer knows the spot is held for them (no visible timer — see below) and can
 * close the loop ("I parked here" / "already taken") from the map. */
@Composable
private fun ClaimTripCard(
    spot: SpotOut,
    endsAt: Long,
    onNavigate: () -> Unit,
    onFeedback: (String) -> Unit,
    onExpired: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var remaining by remember(endsAt) { mutableStateOf(((endsAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0)) }
    LaunchedEffect(endsAt) {
        while (remaining > 0) {
            delay(1000)
            remaining = ((endsAt - System.currentTimeMillis()) / 1000).coerceAtLeast(0)
        }
        onExpired()
    }
    val orange = Color(0xFFE8710A)
    val green = Color(0xFF188038)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(orange.copy(alpha = 0.14f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = orange)
                }
                Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(stringResource(R.string.claim_trip_title), style = MaterialTheme.typography.labelMedium, color = orange)
                    Text(
                        spot.address ?: stringResource(R.string.map_locating),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            // No ticking countdown: a timer on screen while driving pushes
            // people to hurry. The hold still ends quietly in the background.
            Text(
                stringResource(R.string.claim_drive_safely),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                TripAction(stringResource(R.string.spot_navigate), Icons.Filled.Navigation, MaterialTheme.colorScheme.primary, Modifier.weight(1f), onNavigate)
                TripAction(stringResource(R.string.spot_i_took_it), Icons.Filled.CheckCircle, green, Modifier.weight(1f)) { onFeedback("confirmed_taken") }
                TripAction(stringResource(R.string.spot_flag_taken), Icons.Filled.DoNotDisturbOn, orange, Modifier.weight(1f)) { onFeedback("flagged_false") }
            }
        }
    }
}

@Composable
private fun TripAction(label: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(14.dp), color = color.copy(alpha = 0.1f), modifier = modifier.height(44.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = color, maxLines = 1, modifier = Modifier.padding(start = 4.dp))
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
