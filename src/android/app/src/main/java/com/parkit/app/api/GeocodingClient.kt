package com.parkit.app.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
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

/** Per-spot street addresses for the nearby list. Spots don't move, so a
 * resolved label is cached for the life of the process; lookups run one at
 * a time with a pause between them to respect Nominatim's ~1 req/sec policy
 * (callers drive the pacing — see MapScreen). */
object SpotAddressCache {
    private val cache = java.util.concurrent.ConcurrentHashMap<String, String>()

    fun get(spotId: String): String? = cache[spotId]

    suspend fun resolve(spotId: String, lat: Double, lng: Double): String? {
        cache[spotId]?.let { return it }
        val label = try {
            GeocodingClient.service.reverse(lat, lng).streetLabel()
        } catch (_: Exception) {
            null
        } ?: return null
        cache[spotId] = label
        return label
    }
}
