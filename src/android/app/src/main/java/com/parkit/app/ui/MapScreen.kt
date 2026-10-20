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
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.automirrored.filled.Accessible
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocalParking
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.LocalCarWash
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.LocalCafe
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Garage
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Alarm
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.parkit.app.api.localAddress
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
// Same limit as the server (routers/spots.py): you report spots you can see.
private const val MAX_REPORT_DISTANCE_M = 150.0
private const val MAX_ACCURACY_ALLOWANCE_M = 100.0

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
private const val OPEN_AD_EVERY_MS = 6 * 60 * 60 * 1000L
private val DOCK_HEIGHT = 64.dp
private const val NEAR_CAR_M = 150.0 // standing by the car → "leaving?" is the main action
private const val REMINDER_SOON_MIN = 15L
private const val LIVE_FIX_EVERY_MS = 5_000L
private const val NEAR_ME_M = 20.0 // GPS noise: closer than this is "next to you"
private const val SOURCE_CAR = "parked-car"
private const val LAYER_CAR = "parked-car-pin"
private const val CAR_ICON = "car-pin"
private const val LOTS_RADIUS_M = 1500
private const val COLOR_FREE = "#188038"
private const val COLOR_MINE = "#1B4F91"
private const val COLOR_CLAIMED = "#E8710A"
private const val COLOR_LEGEND = "#7B3FE4"

private val SHEET_PEEK = 114.dp
// Empty map: tall enough to show the "car parks / invite friends" buttons.
private val SHEET_PEEK_EMPTY = 178.dp
private const val CLAIM_HOLD_MS = 120_000L // matches the backend's CLAIM_TTL_SECONDS

private val claimAdapter = com.squareup.moshi.Moshi.Builder()
    .add(com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory()).build().adapter(SpotOut::class.java)
private val ClaimSaver = androidx.compose.runtime.saveable.Saver<SpotOut?, String>(
    save = { spot -> spot?.let { claimAdapter.toJson(it) } },
    restore = { json -> runCatching<SpotOut?> { claimAdapter.fromJson(json) }.getOrNull() },
)

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
    // Saveable: "I'm on my way" opens a navigation app, and Android may
    // recreate (or kill) ParkIt meanwhile — coming back must still show the trip.
    var activeClaim by androidx.compose.runtime.saveable.rememberSaveable(stateSaver = ClaimSaver) { mutableStateOf<SpotOut?>(null) }
    var claimEndsAt by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf(0L) }
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
            if (spot.localAddress != null || spotAddresses.containsKey(spot.id)) continue
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

    var parkedCar by remember { mutableStateOf(sessionStore.parkedCar) }

    // The spot just handed on with "I'm leaving" — parking right there again
    // (changed your mind, or a mis-tap) takes it back off the map.
    var leftSpot by remember { mutableStateOf<Pair<String, com.parkit.app.auth.ParkedCar>?>(null) }

    fun rememberCar(at: LatLng, address: String?, spotType: String = "street") {
        leftSpot?.let { (id, was) ->
            leftSpot = null
            val near = at.distanceTo(LatLng(was.lat, was.lng)) < 60
            if (near && System.currentTimeMillis() - was.at < 110_000) {
                scope.launch {
                    if (runCatching { api.cancelSpot(id) }.isSuccess) spots = spots.filterNot { it.id == id }
                    refreshMyProfile()
                }
            }
        }
        val car = com.parkit.app.auth.ParkedCar(at.latitude, at.longitude, System.currentTimeMillis(), address, spotType)
        parkedCar = car
        sessionStore.parkedCar = car
        if (address == null) {
            scope.launch {
                val label = com.parkit.app.api.Geocoder.reverse(at.latitude, at.longitude)?.shortLabel() ?: return@launch
                if (parkedCar?.at == car.at) {
                    parkedCar = car.copy(address = label)
                    sessionStore.parkedCar = parkedCar
                }
            }
        }
    }

    // Ending a parking that was paid for: remind to stop it in the app.
    var stopPayIn by remember { mutableStateOf<ParkingPay.App?>(null) }

    fun forgetCar() {
        parkedCar = null
        sessionStore.parkedCar = null
        ParkingPay.sessionApp(context)?.let { stopPayIn = it }
        ParkingPay.clearSession(context)
    }

    /** The phone's position right now: the blue dot keeps updating while the
     * map is open, so prefer it over the fix taken at launch. */
    fun currentFix(): android.location.Location? {
        val live = try {
            map?.locationComponent?.takeIf { it.isLocationComponentActivated }?.lastKnownLocation
        } catch (_: Exception) {
            null
        }
        return live ?: deviceLocation?.let { d ->
            android.location.Location("cached").apply { latitude = d.latitude; longitude = d.longitude }
        }
    }

    /** Reports must be made near the spot (the server enforces the same
     * limit) — say so up front instead of after the photo. */
    fun openReportFlow() {
        val fix = currentFix()
        val message = when {
            fix == null -> R.string.report_needs_location
            LatLng(fix.latitude, fix.longitude).distanceTo(geocodeTarget) >
                MAX_REPORT_DISTANCE_M + minOf(if (fix.hasAccuracy()) fix.accuracy.toDouble() else 0.0, MAX_ACCURACY_ALLOWANCE_M) ->
                R.string.report_too_far
            else -> null
        }
        if (message == null) {
            showReportFlow = true
            return
        }
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = context.getString(message),
                actionLabel = if (fix != null) context.getString(R.string.report_go_to_me) else null,
                duration = androidx.compose.material3.SnackbarDuration.Long,
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed && fix != null) {
                val here = LatLng(fix.latitude, fix.longitude)
                geocodeTarget = here
                flyTo(here)
            }
        }
    }

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
    var showPayChooser by remember { mutableStateOf(false) }
    var showSignReader by remember { mutableStateOf(false) }
    // First tap on "I just parked" explains what it's for before saving anything.
    var showCarExplainer by remember { mutableStateOf(false) }

    // Right after parking: "blue-white? pay" with the apps as one-tap buttons.
    // (The blue-white tick on a report is optional, so it's offered every time.)
    var showParkedPay by remember { mutableStateOf(false) }

    fun offerPango() {
        if (!ParkingPay.isOff(context)) showParkedPay = true
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
            // 1. Instant: where the phone already knows it is. Good enough to
            // show the map right away — no "locating…" while we refine.
            fusedLastKnown(context, maxAgeMs = QUICK_FIX_MAX_AGE_MS)?.takeIf { inServiceArea(it) }?.let {
                use(it, fly = true)
                locating = false
            }
            // 2. Fast: Wi-Fi + cell, a second or two — quietly, if 1 already placed us.
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
    // Say why before Android's own prompt: a bare "allow location?" with no
    // reason is the most common way drivers end up on a useless map.
    var showLocationWhy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        if (hasAnyLocation(context)) locate() else showLocationWhy = true
    }
    if (showLocationWhy) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = {},
            icon = { Icon(Icons.Filled.MyLocation, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(stringResource(R.string.location_why_title)) },
            text = { Text(stringResource(R.string.location_why_body)) },
            confirmButton = {
                androidx.compose.material3.Button(onClick = {
                    showLocationWhy = false
                    locationPermission.launch(LOCATION_PERMISSIONS)
                }) { Text(stringResource(R.string.location_why_continue)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showLocationWhy = false
                    locating = false
                    showTelAvivFallback(null)
                }) { Text(stringResource(R.string.location_why_not_now)) }
            },
        )
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

    /** "I'm leaving": the driver's own spot becomes a report in one tap — no
     * photo, they're standing right at it (the distance check still runs). */
    fun leaveSpot(car: com.parkit.app.auth.ParkedCar) {
        scope.launch {
            val fix = currentFix()
            try {
                val spot = api.reportSpot(
                    com.parkit.app.api.SpotCreate(
                        lat = car.lat,
                        lng = car.lng,
                        spotType = car.spotType,
                        payment = "free",
                        // The spot fits at least the car that was just in it.
                        vehicleSize = sessionStore.driver.value.preferences.vehicleSize,
                        address = car.address,
                        reporterLat = fix?.latitude,
                        reporterLng = fix?.longitude,
                        reporterAccuracyM = fix?.takeIf { it.hasAccuracy() }?.accuracy,
                        handoff = true,
                    ),
                )
                forgetCar()
                leftSpot = spot.id to car.copy(at = System.currentTimeMillis())
                refreshSpotsNow()
                refreshMyProfile()
                snackbarHostState.showSnackbar(context.getString(R.string.car_leaving_thanks))
            } catch (e: Exception) {
                if (e.isUnauthorized()) onLoggedOut() else snackbarHostState.showSnackbar(e.friendlyMessage(context))
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
                setAttributionMargins(with(density) { 12.dp.roundToPx() }, 0, 0, with(density) { (SHEET_PEEK + 120.dp).roundToPx() })
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
                if (!locating || userTookOver) {
                    val target = m.cameraPosition.target ?: geocodeTarget
                    if (target == geocodeTarget) addressLoading = false else geocodeTarget = target
                } else {
                    addressLoading = false
                }
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

    // Full-screen ad on open — with guard rails: at most every 6 hours, never
    // during a trip to a spot, never on a brand-new install's first visit.
    var openAd by remember { mutableStateOf<com.parkit.app.api.AdOut?>(null) }
    LaunchedEffect(deviceLocation != null) {
        val here = deviceLocation ?: return@LaunchedEffect
        val now = System.currentTimeMillis()
        val firstVisit = sessionStore.lastOpenAdAt == 0L
        if (firstVisit) {
            sessionStore.lastOpenAdAt = now // start the clock; the first ad comes on a later open
            return@LaunchedEffect
        }
        if (activeClaim != null || now - sessionStore.lastOpenAdAt < OPEN_AD_EVERY_MS) return@LaunchedEffect
        val ad = runCatching { api.nearbyAd(here.latitude, here.longitude, placement = "open") }.getOrNull() ?: return@LaunchedEffect
        sessionStore.lastOpenAdAt = now
        openAd = ad
        runCatching { api.adView(ad.id) }
    }

    var showCarSheet by remember { mutableStateOf(false) }

    // The dock's main button always offers what matters right now.
    var clock by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(5_000)
            clock = System.currentTimeMillis()
        }
    }
    val reminderLeftMin = com.parkit.app.remind.ParkingReminder.activeChangeAt(context)
        ?.let { (it - clock) / 60_000 }
        ?.takeIf { it in 0..REMINDER_SOON_MIN }
    val nearMyCar = parkedCar?.let { car -> deviceLocation?.distanceTo(LatLng(car.lat, car.lng))?.let { it <= NEAR_CAR_M } } == true
    val mainAction: MainAction = when {
        reminderLeftMin != null -> MainAction.Reminder(reminderLeftMin.toInt())
        nearMyCar -> MainAction.Leave
        else -> MainAction.Report
    }

    fun myCarLook(): CarLook {
        val prefs = sessionStore.driver.value.preferences
        return CarLook(prefs.vehicleSize, myCarPaint(prefs.carPaint, sessionStore.carPaint), sessionStore.carSpecial)
    }

    /** "I just parked": explained once, then one tap saves the car here. */
    fun parkHere() {
        if (!sessionStore.carExplained) {
            showCarExplainer = true
            return
        }
        val fix = currentFix()
        if (fix == null) {
            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.report_needs_location)) }
        } else {
            rememberCar(LatLng(fix.latitude, fix.longitude), null)
            flyTo(LatLng(fix.latitude, fix.longitude))
            offerPango()
        }
    }

    // Follow the phone while the map is on screen (not in the background):
    // the distance to the car and "leaving?" need where you are *now*, not
    // where you were when the app opened. Balanced power: Wi-Fi/cell + GPS.
    val liveLifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(liveLifecycle, style) {
        val client = com.google.android.gms.location.LocationServices.getFusedLocationProviderClient(context)
        val request = com.google.android.gms.location.LocationRequest.Builder(
            com.google.android.gms.location.Priority.PRIORITY_BALANCED_POWER_ACCURACY,
            LIVE_FIX_EVERY_MS,
        ).setMinUpdateDistanceMeters(5f).build()
        val callback = object : com.google.android.gms.location.LocationCallback() {
            override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                val loc = result.lastLocation ?: return
                val here = LatLng(loc.latitude, loc.longitude)
                if (!inServiceArea(here)) return
                deviceLocation = here
                runCatching { map?.locationComponent?.takeIf { it.isLocationComponentActivated }?.forceLocationUpdate(loc) }
            }
        }
        fun start() {
            if (!hasLocationPermission()) return
            @SuppressLint("MissingPermission")
            fun request() = runCatching { client.requestLocationUpdates(request, callback, android.os.Looper.getMainLooper()) }
            request()
        }
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            when (event) {
                androidx.lifecycle.Lifecycle.Event.ON_START -> start()
                androidx.lifecycle.Lifecycle.Event.ON_STOP -> client.removeLocationUpdates(callback)
                else -> Unit
            }
        }
        liveLifecycle.addObserver(observer)
        onDispose {
            liveLifecycle.removeObserver(observer)
            client.removeLocationUpdates(callback)
        }
    }

    // "Parked? / Leaving?" from the car's Bluetooth notification.
    val carAction = com.parkit.app.remind.CarBluetooth.pendingAction.value
    LaunchedEffect(carAction, deviceLocation != null) {
        val action = carAction ?: return@LaunchedEffect
        if (deviceLocation == null) return@LaunchedEffect // wait for a fix
        com.parkit.app.remind.CarBluetooth.pendingAction.value = null
        when (action) {
            "leave" -> parkedCar?.let { leaveSpot(it) }
            "park" -> if (parkedCar == null) {
                currentFix()?.let { fix ->
                    rememberCar(LatLng(fix.latitude, fix.longitude), null)
                    flyTo(LatLng(fix.latitude, fix.longitude))
                    offerPango()
                }
            }
        }
    }

    // Forecast for the area on screen (silent until there's enough history).
    var forecast by remember { mutableStateOf<com.parkit.app.api.AreaForecast?>(null) }
    var forecastNoData by remember { mutableStateOf(false) }
    LaunchedEffect(geocodeTarget) {
        runCatching { api.areaForecast(geocodeTarget.latitude, geocodeTarget.longitude) }
            .onSuccess { forecast = it; forecastNoData = it == null }
            .onFailure { forecast = null; forecastNoData = false }
    }
    // Daily tip: once a day, the best hour to find parking around you.
    var dailyTip by remember { mutableStateOf<com.parkit.app.api.BestHour?>(null) }
    LaunchedEffect(deviceLocation != null) {
        val here = deviceLocation ?: return@LaunchedEffect
        val today = java.time.LocalDate.now().toString()
        if (sessionStore.tipShownDay == today) return@LaunchedEffect
        dailyTip = runCatching { api.bestHour(here.latitude, here.longitude) }.getOrNull()
    }

    // One sponsored card for the area on screen; nothing personal is sent.
    var nearbyAd by remember { mutableStateOf<com.parkit.app.api.AdOut?>(null) }
    LaunchedEffect(geocodeTarget) {
        nearbyAd = runCatching { api.nearbyAd(geocodeTarget.latitude, geocodeTarget.longitude) }.getOrNull()
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

    // "Your car" on the map is the same car as in the profile.
    val myCar = sessionStore.driver.value.preferences
    LaunchedEffect(style, myCar.vehicleSize, myCar.carPaint, myCar.hasDisabledPermit) {
        val s = style ?: return@LaunchedEffect
        s.addImage(CAR_ICON, carMarkerBitmap(context, myCar.vehicleSize, myCarPaint(myCar.carPaint, sessionStore.carPaint), permit = false, special = sessionStore.carSpecial))
    }
    LaunchedEffect(style, parkedCar) {
        val s = style ?: return@LaunchedEffect
        val features = parkedCar?.let { listOf(Feature.fromGeometry(Point.fromLngLat(it.lng, it.lat))) } ?: emptyList()
        s.getSourceAs<GeoJsonSource>(SOURCE_CAR)?.setGeoJson(FeatureCollection.fromFeatures(features))
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
        sheetPeekHeight = (if (spotsLoaded && !spotsFailed && sortedSpots.isEmpty()) SHEET_PEEK_EMPTY else SHEET_PEEK) + with(density) { WindowInsets.navigationBars.getBottom(density).toDp() },
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
                lotsShown = showLots,
                onShowLots = { showLots = true },
                onInvite = { inviteFriends(context) },
                ad = nearbyAd,
                onAdShown = { ad -> scope.launch { runCatching { api.adView(ad.id) } } },
                forecast = forecast,
                forecastNoData = forecastNoData,
                dailyTip = dailyTip,
                onTipShown = { sessionStore.tipShownDay = java.time.LocalDate.now().toString() },
                onTipClose = { dailyTip = null },
                onAdClick = { ad ->
                    scope.launch { runCatching { api.adClick(ad.id) } }
                    val link = ad.linkUrl
                    if (link != null) {
                        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link))) }
                    } else {
                        DriveTo.open(context, ad.lat, ad.lng)
                    }
                },
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
                    avatarId = myProfile?.avatar,
                    level = myProfile?.let { ParkItLevel.of(it.level) },
                    levelProgress = myProfile?.let(::levelProgress),
                    onOpenProfile = onOpenProfile,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MapChip(stringResource(R.string.map_filter_all), Icons.Filled.LocalParking, typeFilter == null) { typeFilter = null }
                    MapChip(stringResource(R.string.map_filter_regular), Icons.Filled.DirectionsCar, typeFilter == "street") { typeFilter = "street" }
                    // Only drivers with a disabled permit need this filter.
                    if (driverPrefs.preferences.hasDisabledPermit || typeFilter == "disabled") {
                        MapChip(stringResource(R.string.map_filter_disabled), Icons.AutoMirrored.Filled.Accessible, typeFilter == "disabled") { typeFilter = "disabled" }
                    }
                    LotsChip(checked = showLots) {
                        showLots = !showLots
                        if (!showLots) selectedLot = null
                    }
                }
                parkedCar?.takeIf { activeClaim == null }?.let { car ->
                    CarLine(
                        car = car,
                        look = myCarLook(),
                        onWalk = { walkTo(context, car.lat, car.lng) },
                        // From where the phone is now (followed live while the map is open).
                        distanceM = deviceLocation?.distanceTo(LatLng(car.lat, car.lng)),
                        onClick = { showCarSheet = true },
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
                activeClaim?.let { claim ->
                    ClaimTripCard(
                        spot = claim,
                        endsAt = claimEndsAt,
                        onNavigate = {
                            DriveTo.open(context, claim.lat, claim.lng)
                        },
                        onFeedback = { type ->
                            scope.launch {
                                try {
                                    api.submitFeedback(claim.id, com.parkit.app.api.FeedbackCreate(type))
                                    if (type == "confirmed_taken") rememberCar(LatLng(claim.lat, claim.lng), claim.localAddress, claim.spotType)
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
                visible = locating && !userTookOver && (deviceLocation == null || (activeClaim == null && parkedCar == null)),
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
                        IntOffset(-20.dp.roundToPx(), top - DOCK_HEIGHT.roundToPx() - buttonSize.roundToPx() - 24.dp.roundToPx())
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
            // One action dock above the list: car · smart main action · sign.
            ActionDock(
                parked = parkedCar != null,
                car = myCarLook(),
                main = mainAction,
                onCar = { if (parkedCar == null) parkHere() else showCarSheet = true },
                onMain = {
                    when (mainAction) {
                        MainAction.Leave -> parkedCar?.let { leaveSpot(it) }
                        is MainAction.Reminder -> showCarSheet = true
                        MainAction.Report -> openReportFlow()
                    }
                },
                onSign = { showSignReader = true },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .offset {
                        val top = sheetTopPx?.toInt() ?: 0
                        IntOffset(0, top - DOCK_HEIGHT.roundToPx() - 12.dp.roundToPx())
                    }
                    .padding(horizontal = 12.dp),
            )
        }
    }

    openAd?.let { ad ->
        OpenAdDialog(
            ad = ad,
            onClose = { openAd = null },
            onOpen = {
                openAd = null
                scope.launch { runCatching { api.adClick(ad.id) } }
                val link = ad.linkUrl
                if (link != null) {
                    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(link))) }
                } else {
                    DriveTo.open(context, ad.lat, ad.lng)
                }
            },
        )
    }

    stopPayIn?.let { app ->
        androidx.compose.material3.ModalBottomSheet(windowInsets = androidx.compose.foundation.layout.WindowInsets(0), onDismissRequest = { stopPayIn = null }) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 20.dp).navigationBarsPadding(),
            ) {
                Icon(Icons.Filled.StopCircle, contentDescription = null, tint = Color(app.brand), modifier = Modifier.size(44.dp))
                Text(
                    stringResource(R.string.stop_pay_title, stringResource(app.label)),
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
                Text(
                    stringResource(R.string.stop_pay_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )
                androidx.compose.material3.Button(
                    onClick = {
                        stopPayIn = null
                        ParkingPay.open(context, app)
                        ParkingPay.clearSession(context) // opening it isn't a new session
                    },
                    shape = RoundedCornerShape(50),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(app.brand)),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text(stringResource(R.string.stop_pay_open, stringResource(app.label)), style = MaterialTheme.typography.titleSmall) }
                androidx.compose.material3.TextButton(onClick = { stopPayIn = null }, modifier = Modifier.padding(top = 6.dp)) {
                    Text(stringResource(R.string.stop_pay_done))
                }
            }
        }
    }

    if (showParkedPay) {
        androidx.compose.material3.ModalBottomSheet(windowInsets = androidx.compose.foundation.layout.WindowInsets(0), onDismissRequest = { showParkedPay = false }) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 20.dp).navigationBarsPadding()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF188038), modifier = Modifier.size(26.dp))
                    Text(stringResource(R.string.parked_pay_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 8.dp))
                }
                Text(
                    stringResource(R.string.parked_pay_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )
                // One tap: the tile opens that app (and becomes the default).
                PayAppTiles(selected = ParkingPay.preferred(context)) { app ->
                    showParkedPay = false
                    ParkingPay.setPreferred(context, app)
                    ParkingPay.open(context, app)
                }
                androidx.compose.material3.TextButton(
                    onClick = { showParkedPay = false },
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 8.dp),
                ) { Text(stringResource(R.string.parked_pay_free)) }
            }
        }
    }

    if (showCarSheet) {
        parkedCar?.let { car ->
            androidx.compose.material3.ModalBottomSheet(windowInsets = androidx.compose.foundation.layout.WindowInsets(0), onDismissRequest = { showCarSheet = false }) {
                ParkedCarCard(
                    car = car,
                    nearCar = nearMyCar,
                    showWalk = deviceLocation?.distanceTo(LatLng(car.lat, car.lng))?.let { it >= NEAR_ME_M } ?: true,
                    onNavigate = { walkTo(context, car.lat, car.lng) },
                    onLeaving = { showCarSheet = false; leaveSpot(car) },
                    onPay = { ParkingPay.pay(context) { showPayChooser = true } },
                    onForget = { showCarSheet = false; forgetCar() },
                    modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp).navigationBarsPadding(),
                )
            }
        } ?: run { showCarSheet = false }
    }

    if (showSignReader) {
        SignReaderSheet(api = api, onDismiss = { showSignReader = false }, onSessionExpired = onLoggedOut)
    }

    if (showCarExplainer) {
        // Short and visual: your own car, one line, one button.
        val prefs = sessionStore.driver.value.preferences
        androidx.compose.material3.ModalBottomSheet(windowInsets = androidx.compose.foundation.layout.WindowInsets(0), onDismissRequest = { showCarExplainer = false }) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding(),
            ) {
                CarArt(
                    size = prefs.vehicleSize ?: "regular",
                    paint = myCarPaint(prefs.carPaint, sessionStore.carPaint),
                    special = sessionStore.carSpecial,
                    outline = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.width(150.dp),
                )
                Text(stringResource(R.string.car_explain_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 12.dp))
                Text(
                    stringResource(R.string.car_explain_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
                androidx.compose.material3.Button(
                    onClick = {
                        showCarExplainer = false
                        sessionStore.carExplained = true
                        val fix = currentFix()
                        if (fix == null) {
                            scope.launch { snackbarHostState.showSnackbar(context.getString(R.string.report_needs_location)) }
                        } else {
                            rememberCar(LatLng(fix.latitude, fix.longitude), null)
                            flyTo(LatLng(fix.latitude, fix.longitude))
                            offerPango()
                        }
                    },
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp).height(52.dp),
                ) { Text(stringResource(R.string.car_explain_ok), style = MaterialTheme.typography.labelLarge) }
            }
        }
    }

    if (showPayChooser) {
        androidx.compose.material3.ModalBottomSheet(windowInsets = androidx.compose.foundation.layout.WindowInsets(0), onDismissRequest = { showPayChooser = false }) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp).navigationBarsPadding()) {
                Text(stringResource(R.string.pay_chooser_title), style = MaterialTheme.typography.titleLarge)
                Text(
                    stringResource(R.string.pay_chooser_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
                )
                PayAppTiles(selected = null) { app ->
                    showPayChooser = false
                    ParkingPay.setPreferred(context, app) // remembered; changeable in Settings
                    ParkingPay.open(context, app)
                }
                Text(
                    stringResource(R.string.pay_chooser_remember),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 14.dp),
                )
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
            currentFix = { currentFix() },
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
            onParked = {
                rememberCar(LatLng(spot.lat, spot.lng), spot.localAddress, spot.spotType)
                offerPango()
            },
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
    // Your parked car, on top of everything.
    style.addImage(CAR_ICON, MarkerBitmaps.carPin(context))
    style.addSource(GeoJsonSource(SOURCE_CAR, FeatureCollection.fromFeatures(emptyList())))
    style.addLayer(
        SymbolLayer(LAYER_CAR, SOURCE_CAR).withProperties(
            PropertyFactory.iconImage(CAR_ICON),
            PropertyFactory.iconAnchor("bottom"),
            PropertyFactory.iconAllowOverlap(true),
            PropertyFactory.iconIgnorePlacement(true),
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
            val profileLabel = stringResource(R.string.map_open_profile_cd)
            Box(
                modifier = Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onOpenProfile)
                    .semantics(mergeDescendants = true) { contentDescription = profileLabel; role = Role.Button },
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
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 11.dp)) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = content, modifier = Modifier.padding(start = 6.dp))
        }
    }
}

/** An on/off toggle, not one of the single-choice type filters above: it
 * fills with the car-park slate when lots are shown. */
@Composable
private fun LotsChip(checked: Boolean, onClick: () -> Unit) {
    val slate = Color(android.graphics.Color.parseColor(MarkerBitmaps.LOT_COLOR))
    Surface(
        shape = RoundedCornerShape(50),
        color = if (checked) slate else MaterialTheme.colorScheme.surface,
        shadowElevation = 3.dp,
        modifier = Modifier.height(36.dp).clickable(onClick = onClick),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 11.dp)) {
            Icon(Icons.Filled.Garage, contentDescription = null, tint = if (checked) Color.White else slate, modifier = Modifier.size(18.dp))
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
    lotsShown: Boolean,
    onShowLots: () -> Unit,
    onInvite: () -> Unit,
    ad: com.parkit.app.api.AdOut? = null,
    onAdClick: (com.parkit.app.api.AdOut) -> Unit = {},
    forecast: com.parkit.app.api.AreaForecast? = null,
    forecastNoData: Boolean = false,
    onAdShown: (com.parkit.app.api.AdOut) -> Unit = {},
    dailyTip: com.parkit.app.api.BestHour? = null,
    onTipShown: () -> Unit = {},
    onTipClose: () -> Unit = {},
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
                if (forecast != null || forecastNoData) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                        Text(
                            stringResource(R.string.forecast_tag),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = Color.White,
                            modifier = Modifier.clip(RoundedCornerShape(50))
                                .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(Color(0xFF7B3FC4), Color(0xFF2E7BD6))))
                                .padding(horizontal = 6.dp),
                        )
                        Text(
                            forecast?.let {
                                // Past an hour and a half a number reads as noise.
                                (if (it.everyMinutes > 90) stringResource(R.string.forecast_rare) else pluralStringResource(R.plurals.forecast_every, it.everyMinutes, it.everyMinutes)) + when (it.scope) {
                                    "area" -> " · " + stringResource(R.string.forecast_scope_area)
                                    "city" -> " · " + stringResource(R.string.forecast_scope_city)
                                    else -> ""
                                }
                            } ?: stringResource(R.string.forecast_no_data),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
                dailyTip?.let { tip ->
                    LaunchedEffect(tip) { onTipShown() }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                        Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = Color(0xFFE37400), modifier = Modifier.size(15.dp))
                        Text(
                            stringResource(R.string.tip_best_hour, "%d:00".format(tip.hour), "%d:00".format((tip.hour + 1) % 24)),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f, fill = false).padding(start = 6.dp),
                        )
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.ad_close),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp).size(16.dp).clickable(onClick = onTipClose),
                        )
                    }
                }
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
        // An empty map is where a new driver gives up: offer the car parks
        // that are always there, and the one thing that fills the map —
        // more drivers nearby.
        if (loaded && spots.isEmpty()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 16.dp),
            ) {
                if (!lotsShown) {
                    androidx.compose.material3.FilledTonalButton(onClick = onShowLots, shape = RoundedCornerShape(50), modifier = Modifier.weight(1f)) {
                        Icon(Icons.Filled.LocalParking, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(stringResource(R.string.map_empty_show_lots), maxLines = 1, modifier = Modifier.padding(start = 6.dp))
                    }
                }
                androidx.compose.material3.Button(onClick = onInvite, shape = RoundedCornerShape(50), modifier = Modifier.weight(1f)) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.map_empty_invite), maxLines = 1, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }
        if (spots.isNotEmpty()) {
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
            LazyColumn(modifier = Modifier.fillMaxWidth().height(360.dp)) {
                // The card sits after the first two spots: seen, never in the way.
                spots.forEachIndexed { index, spot ->
                    item(key = spot.id) {
                        SpotRow(spot, spot.localAddress ?: addresses[spot.id], reference, myUserId, fitOf(spot, prefs), onClick = { onSpotClick(spot) })
                    }
                    if (ad != null && index == minOf(1, spots.lastIndex)) item(key = "ad") { SponsoredCard(ad, onAdClick, onAdShown) }
                }
            }
        } else if (ad != null) {
            SponsoredCard(ad, onAdClick, onAdShown)
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
            // A photo that fails to load (already deleted) shows the icon,
            // never an empty tile.
            var photoFailed by remember(spot.id) { mutableStateOf(false) }
            if (spot.photoUrl != null && !photoFailed) {
                // The reporter's photo, when there is one — the quickest
                // read of "is this spot real and will my car fit".
                coil.compose.AsyncImage(
                    model = com.parkit.app.ApiConfig.BASE_URL.trimEnd('/') + spot.photoUrl,
                    contentDescription = null,
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                    onError = { photoFailed = true },
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
                    if (spot.payment == "paid") stringResource(R.string.spot_blue_white_short) else null,
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

/** Where the car is, a walk back to it, and "I'm leaving" — which hands the
 * spot to the next driver in one tap. */
@Composable
private fun ParkedCarCard(
    car: com.parkit.app.auth.ParkedCar,
    nearCar: Boolean,
    showWalk: Boolean,
    onNavigate: () -> Unit,
    onLeaving: () -> Unit,
    onPay: () -> Unit,
    onForget: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val green = Color(0xFF188038)
    val primary = MaterialTheme.colorScheme.primary
    val now = System.currentTimeMillis()
    val since = if (now - car.at < android.text.format.DateUtils.MINUTE_IN_MILLIS) {
        stringResource(R.string.car_just_now)
    } else {
        // Our own plurals: the platform's Hebrew output reads "לפני דקה (1)".
        MarkerBitmaps.relativeTimeLong(LocalContext.current, java.time.Instant.ofEpochMilli(car.at).toString())
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).background(primary.copy(alpha = 0.12f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.DirectionsCar, contentDescription = null, tint = primary)
                }
                Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                    Text(stringResource(R.string.car_card_title, since), style = MaterialTheme.typography.labelMedium, color = primary)
                    Text(
                        car.address ?: stringResource(R.string.map_locating),
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ReminderCountdown()
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
                if (showWalk) TripAction(stringResource(R.string.car_navigate), Icons.AutoMirrored.Filled.DirectionsWalk, primary, Modifier.weight(1f), onNavigate)
                // Blue-white curb? Pay in Pango / Cellopark without hunting for the app.
                TripAction(stringResource(R.string.car_pay_short), Icons.Filled.Payments, Color(0xFF1565C0), Modifier.weight(1f), onPay)
            }
            // One way to finish. By the car it offers to hand the spot on
            // (+15); away from it, it just ends the parking.
            var askHandOff by remember { mutableStateOf(false) }
            androidx.compose.material3.Button(
                onClick = { if (nearCar) askHandOff = true else onForget() },
                shape = RoundedCornerShape(14.dp),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = green),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(48.dp),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.car_end_parking), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 6.dp))
            }
            if (askHandOff) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { askHandOff = false },
                    title = { Text(stringResource(R.string.car_handoff_title)) },
                    text = { Text(stringResource(R.string.car_handoff_body)) },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = { askHandOff = false; onLeaving() }) { Text(stringResource(R.string.car_handoff_yes)) }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { askHandOff = false; onForget() }) { Text(stringResource(R.string.car_handoff_no)) }
                    },
                )
            }
        }
    }
}

/** Walking directions back to the car; any maps app that takes a geo: link
 * if Google Maps isn't there. */
private fun walkTo(context: android.content.Context, lat: Double, lng: Double) {
    val walk = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("google.navigation:q=$lat,$lng&mode=w"))
    try {
        context.startActivity(walk)
    } catch (_: android.content.ActivityNotFoundException) {
        context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("geo:$lat,$lng?q=$lat,$lng")))
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

/** The same invite as Settings → "Invite friends", one tap from an empty map. */
internal fun inviteFriends(context: android.content.Context) {
    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(android.content.Intent.EXTRA_TEXT, context.getString(R.string.profile_share_message))
    }
    context.startActivity(android.content.Intent.createChooser(intent, context.getString(R.string.profile_share_title)))
}

/** The paint for "your car": a colour picked by hand wins, then the
 * registry's colour, then ParkIt blue. */
private fun myCarPaint(registryHex: String?, manual: Int): Color {
    return CarPaints.getOrNull(manual)
        ?: registryHex?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() }
        ?: CarPaints[0]
}

/** "ממומן": one local business, clearly labelled, one tap to go there. */
@Composable
private fun SponsoredCard(ad: com.parkit.app.api.AdOut, onClick: (com.parkit.app.api.AdOut) -> Unit, onShown: (com.parkit.app.api.AdOut) -> Unit = {}) {
    val context = LocalContext.current
    // One view per card per screen visit — when it's really been drawn.
    LaunchedEffect(ad.id) { onShown(ad) }
    val hebrew = context.resources.configuration.locales[0].language in setOf("he", "iw")
    val icon = when (ad.category) {
        "car_wash" -> Icons.Filled.LocalCarWash
        "garage" -> Icons.Filled.Build
        "cafe" -> Icons.Filled.LocalCafe
        "parking" -> Icons.Filled.LocalParking
        else -> Icons.Filled.Storefront
    }
    Surface(
        onClick = { onClick(ad) },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFF9AB00).copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = Color(0xFFE37400))
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(if (hebrew) ad.titleHe else ad.titleEn, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Text(
                        stringResource(R.string.ad_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp),
                    )
                }
                (if (hebrew) ad.bodyHe else ad.bodyEn)?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Text(
                    "${ad.advertiser} · ${stringResource(R.string.spot_distance_away, formatDistanceShort(context, ad.distanceM))}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun formatDistanceShort(context: android.content.Context, meters: Double): String =
    if (meters < 1000) context.getString(R.string.distance_m, (Math.round(meters / 10.0) * 10).toInt())
    else context.getString(R.string.distance_km, meters / 1000.0)

/** The "on open" ad: one business, a clear "Sponsored" tag, an X that's
 * there from the first frame (Play policy: never trap the user). */
@Composable
private fun OpenAdDialog(ad: com.parkit.app.api.AdOut, onClose: () -> Unit, onOpen: () -> Unit) {
    val context = LocalContext.current
    val hebrew = context.resources.configuration.locales[0].language in setOf("he", "iw")
    val icon = when (ad.category) {
        "car_wash" -> Icons.Filled.LocalCarWash
        "garage" -> Icons.Filled.Build
        "cafe" -> Icons.Filled.LocalCafe
        "parking" -> Icons.Filled.LocalParking
        else -> Icons.Filled.Storefront
    }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        Surface(shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) {
            Box {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                    Text(
                        stringResource(R.string.ad_label),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 1.dp),
                    )
                    Box(
                        Modifier.padding(top = 18.dp).size(96.dp).clip(RoundedCornerShape(28.dp))
                            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFFFFD54F), Color(0xFFF57C00)))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(52.dp))
                    }
                    Text(
                        if (hebrew) ad.titleHe else ad.titleEn,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    (if (hebrew) ad.bodyHe else ad.bodyEn)?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    }
                    Text(
                        "${ad.advertiser} · ${stringResource(R.string.spot_distance_away, formatDistanceShort(context, ad.distanceM))}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                    androidx.compose.material3.Button(
                        onClick = onOpen,
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth().padding(top = 20.dp).height(52.dp),
                    ) {
                        Text(stringResource(if (ad.linkUrl != null) R.string.ad_open_link else R.string.ad_navigate), style = MaterialTheme.typography.labelLarge)
                    }
                }
                androidx.compose.material3.IconButton(onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.ad_close))
                }
            }
        }
    }
}

/** "⏰ 0:42 עד שינוי בחניה" — ticking down to the change a sign reminder is set for. */
@Composable
private fun ReminderCountdown() {
    val context = LocalContext.current
    var left by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            left = com.parkit.app.remind.ParkingReminder.activeChangeAt(context)?.minus(System.currentTimeMillis())
            kotlinx.coroutines.delay(30_000)
        }
    }
    val ms = left ?: return
    val minutes = ms / 60_000
    Text(
        stringResource(R.string.reminder_countdown, "%d:%02d".format(minutes / 60, minutes % 60)),
        style = MaterialTheme.typography.labelMedium,
        color = Color(0xFFE37400),
        modifier = Modifier.padding(top = 2.dp),
    )
}

/** What the dock's big button does right now. */
private sealed interface MainAction {
    data object Report : MainAction
    data object Leave : MainAction
    data class Reminder(val minutes: Int) : MainAction
}

/** Car · main action · sign — every action in one place the thumb reaches. */
@Composable
private fun ActionDock(
    parked: Boolean,
    car: CarLook,
    main: MainAction,
    onCar: () -> Unit,
    onMain: () -> Unit,
    onSign: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 8.dp,
        modifier = modifier.fillMaxWidth().height(DOCK_HEIGHT),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 6.dp)) {
            DockSide(
                label = stringResource(if (parked) R.string.dock_my_car else R.string.dock_parked),
                modifier = Modifier.weight(1f),
                onClick = onCar,
            ) { CarArt(car.size ?: "regular", car.paint, MaterialTheme.colorScheme.outline, Modifier.width(40.dp), special = car.special, animate = false) }
            val (label, color) = when (main) {
                MainAction.Report -> stringResource(R.string.dock_report) to MaterialTheme.colorScheme.primary
                MainAction.Leave -> stringResource(R.string.dock_leave) to Color(0xFF188038)
                is MainAction.Reminder -> stringResource(R.string.dock_reminder, main.minutes) to Color(0xFFE37400)
            }
            androidx.compose.material3.Button(
                onClick = onMain,
                shape = RoundedCornerShape(50),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = color),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp),
                modifier = Modifier.weight(1.6f).height(48.dp),
            ) {
                if (main == MainAction.Report) Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(label, style = MaterialTheme.typography.titleSmall, maxLines = 1, modifier = Modifier.padding(start = 4.dp))
            }
            DockSide(label = stringResource(R.string.dock_sign), modifier = Modifier.weight(1f), onClick = onSign) {
                Box {
                    SignScanIcon(30.dp, animate = false)
                    AiBadge(Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-4).dp), small = true)
                }
            }
        }
    }
}

@Composable
private fun DockSide(label: String, modifier: Modifier, onClick: () -> Unit, icon: @Composable () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.clip(RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(vertical = 6.dp),
    ) {
        icon()
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, modifier = Modifier.padding(top = 2.dp))
    }
}

/** The parked car as one thin line — tap for walk / leaving / pay. */
@Composable
private fun CarLine(car: com.parkit.app.auth.ParkedCar, look: CarLook, distanceM: Double?, onClick: () -> Unit, onWalk: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 6.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 8.dp, bottom = 8.dp)) {
            CarArt(look.size ?: "regular", look.paint, MaterialTheme.colorScheme.outline, Modifier.width(34.dp), special = look.special, animate = false)
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(
                    stringResource(R.string.car_line_title) + " " + listOfNotNull(
                        car.address?.substringBefore(",")?.trim(), // the street is enough here
                        distanceM?.let { if (it < NEAR_ME_M) context.getString(R.string.car_next_to_you) else context.getString(R.string.car_from_you, formatDistanceShort(context, it)) },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                // Says the line opens something — it looked like a label before.
                Text(
                    stringResource(R.string.car_line_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                )
            }
            ReminderCountdownInline()
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
            // The one thing people want from this line: get back to the car
            // (not when you're already standing by it).
            if (distanceM == null || distanceM >= NEAR_ME_M) Surface(
                onClick = onWalk,
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)) {
                    Icon(Icons.AutoMirrored.Filled.DirectionsWalk, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                    Text(stringResource(R.string.car_navigate), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.padding(start = 4.dp))
                }
            }
        }
    }
}

/** "⏰ 0:42" next to the car line while a sign reminder is set. */
@Composable
private fun ReminderCountdownInline() {
    val context = LocalContext.current
    var left by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            left = com.parkit.app.remind.ParkingReminder.activeChangeAt(context)?.minus(System.currentTimeMillis())
            kotlinx.coroutines.delay(30_000)
        }
    }
    val ms = left ?: return
    val minutes = ms / 60_000
    Icon(Icons.Filled.Alarm, contentDescription = null, tint = Color(0xFFE37400), modifier = Modifier.size(14.dp))
    Text("%d:%02d".format(minutes / 60, minutes % 60), style = MaterialTheme.typography.labelMedium, color = Color(0xFFE37400), modifier = Modifier.padding(start = 2.dp))
}

/** How the driver's own car is drawn (same as the profile and the map). */
private data class CarLook(val size: String?, val paint: Color, val special: String?)

