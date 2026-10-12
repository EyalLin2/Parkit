package com.parkit.app.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.sync.withLock
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Reverse geocoding (coordinates -> a human-readable street address) so the
 * report flow can show "Rothschild Blvd 45" instead of raw lat/lng — same
 * "no Google API key available" precedent as osmdroid for the map itself.
 * Nominatim's usage policy asks for a real User-Agent and ~1 req/sec, both
 * fine for a single interactive user tapping/dragging a map.
 */
@JsonClass(generateAdapter = true)
data class NominatimAddress(
    @Json(name = "house_number") val houseNumber: String? = null,
    val road: String? = null,
    val city: String? = null,
    val town: String? = null,
    val suburb: String? = null,
)

@JsonClass(generateAdapter = true)
data class NominatimReverseResponse(
    @Json(name = "display_name") val displayName: String? = null,
    val address: NominatimAddress? = null,
) {
    /** A short "Street Number, City" label, falling back to the full display name. */
    fun shortLabel(): String {
        val a = address
        val street = a?.road
        if (street != null) {
            val withNumber = if (a.houseNumber != null) "$street ${a.houseNumber}" else street
            val place = a.city ?: a.town ?: a.suburb
            return if (place != null) "$withNumber, $place" else withNumber
        }
        return displayName ?: "Unknown location"
    }

    /** Just "Street Number" — for list rows, where the city is the same
     * for every spot and would only push the useful part off-screen. */
    fun streetLabel(): String? {
        val street = address?.road ?: return null
        return if (address.houseNumber != null) "$street ${address.houseNumber}" else street
    }
}

interface GeocodingService {
    @GET("reverse?format=json&zoom=18")
    suspend fun reverse(
        @Query("lat") lat: Double,
        @Query("lon") lon: Double,
        // Street names in the app's language (Hebrew UI → Hebrew streets),
        // matching the map labels instead of whatever the server defaults to.
        @Query("accept-language") language: String = java.util.Locale.getDefault().language.let { if (it == "iw") "he" else it },
    ): NominatimReverseResponse
}

object GeocodingClient {
    val service: GeocodingService by lazy {
        val userAgent = Interceptor { chain ->
            chain.proceed(chain.request().newBuilder().header("User-Agent", "ParkItDemo/0.1 (student project)").build())
        }
        val client = OkHttpClient.Builder().addInterceptor(userAgent).build()
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        Retrofit.Builder()
            .baseUrl("https://nominatim.openstreetmap.org/")
            .client(client)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeocodingService::class.java)
    }
}

/** The ONE way the app talks to Nominatim. Every caller (the map's pin
 * address, the nearby list, the spot sheet) used to fire requests on its
 * own, so together they blew through Nominatim's ~1 req/sec policy, got
 * throttled, and the UI fell back to raw coordinates for a few seconds.
 * Here: a single FIFO lane spaced >= 1.1s apart, a few retries with
 * backoff, and a cache keyed on a ~11m grid so revisiting a place (or
 * nudging the map slightly) is instant and costs no request at all. */
object Geocoder {
    private const val MIN_INTERVAL_MS = 1100L
    private val lane = kotlinx.coroutines.sync.Mutex()
    private var lastRequestAt = 0L
    private val cache = android.util.LruCache<String, NominatimReverseResponse>(300)

    private fun key(lat: Double, lng: Double) = "%.4f,%.4f".format(java.util.Locale.ROOT, lat, lng)

    fun cached(lat: Double, lng: Double): NominatimReverseResponse? = cache.get(key(lat, lng))

    /** Null only after every retry failed — callers show a message, never coordinates. */
    suspend fun reverse(lat: Double, lng: Double, attempts: Int = 3): NominatimReverseResponse? {
        cached(lat, lng)?.let { return it }
        repeat(attempts) { attempt ->
            val result = lane.withLock {
                cached(lat, lng)?.let { return it }
                val wait = lastRequestAt + MIN_INTERVAL_MS - System.currentTimeMillis()
                if (wait > 0) kotlinx.coroutines.delay(wait)
                lastRequestAt = System.currentTimeMillis()
                try {
                    GeocodingClient.service.reverse(lat, lng)
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
            }
            if (result != null) {
                cache.put(key(lat, lng), result)
                return result
            }
            if (attempt < attempts - 1) kotlinx.coroutines.delay(800L * (attempt + 1))
        }
        return null
    }
}

/** Per-spot street addresses for the nearby list (only for older spots
 * reported before the backend stored addresses). Goes through [Geocoder],
 * so it shares the same rate-limited lane as the pin address. */
object SpotAddressCache {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun get(spotId: String): String? = cache[spotId]

    suspend fun resolve(spotId: String, lat: Double, lng: Double): String? {
        cache[spotId]?.let { return it }
        val label = Geocoder.reverse(lat, lng, attempts = 1)?.streetLabel() ?: return null
        cache[spotId] = label
        return label
    }
}
