package com.parkit.app.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

/**
 * Reverse geocoding (coordinates -> "Dizengoff 50, Tel Aviv") through
 * ParkIt's OWN backend (`GET /geocode/reverse`), which answers from
 * OpenStreetMap data loaded into our PostGIS — no third-party geocoder in
 * the request path, so no external rate limit (the public Nominatim server
 * allows ~1 request/second for the whole app, across every user).
 */
@JsonClass(generateAdapter = true)
data class ReverseGeocodeOut(
    val label: String? = null,
    val street: String? = null,
) {
    /** "Street Number, City" — for the pin and the report header. */
    fun shortLabel(): String? = label

    /** Just "Street Number" — for list rows, where the city is the same
     * for every spot and would only push the useful part off-screen. */
    fun streetLabel(): String? = street
}

interface GeocodingService {
    @GET("geocode/reverse")
    suspend fun reverse(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        // Street names in the app's language (Hebrew UI → Hebrew streets),
        // matching the map labels.
        @Query("lang") language: String = if (java.util.Locale.getDefault().language == "en") "en" else "he",
    ): ReverseGeocodeOut
}

object GeocodingClient {
    val service: GeocodingService by lazy {
        val moshi = Moshi.Builder().add(KotlinJsonAdapterFactory()).build()
        Retrofit.Builder()
            .baseUrl(com.parkit.app.ApiConfig.BASE_URL)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GeocodingService::class.java)
    }
}

/** The one way the app resolves addresses. Lookups now hit our own
 * backend (no external rate limit), but this still keeps a cache on a ~11m
 * grid so revisiting a place, or nudging the map slightly, costs no request
 * at all, plus a few retries with backoff for flaky mobile networks. The
 * backend keeps its own shared cache in Redis on the same grid. */
object Geocoder {
    private val cache = android.util.LruCache<String, ReverseGeocodeOut>(300)

    private fun key(lat: Double, lng: Double) = "%.4f,%.4f".format(java.util.Locale.ROOT, lat, lng)

    fun cached(lat: Double, lng: Double): ReverseGeocodeOut? = cache.get(key(lat, lng))

    /** Null only after every retry failed — callers show a message, never coordinates. */
    suspend fun reverse(lat: Double, lng: Double, attempts: Int = 3): ReverseGeocodeOut? {
        cached(lat, lng)?.let { return it }
        repeat(attempts) { attempt ->
            val result = try {
                GeocodingClient.service.reverse(lat, lng)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }
            if (result != null) {
                // An answer with no label is still an answer ("nothing
                // mapped here") — cache it rather than retrying forever.
                cache.put(key(lat, lng), result)
                return result
            }
            if (attempt < attempts - 1) kotlinx.coroutines.delay(600L * (attempt + 1))
        }
        return null
    }
}

/** Per-spot street addresses for the nearby list (only for older spots
 * reported before the backend stored addresses). Goes through [Geocoder],
 * so it shares the same cache as the pin address. */
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
