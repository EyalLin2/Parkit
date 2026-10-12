package com.parkit.app.api

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class DevLoginRequest(
    @Json(name = "auth_provider") val authProvider: String = "dev",
    @Json(name = "external_id") val externalId: String,
    @Json(name = "display_name") val displayName: String,
)

@JsonClass(generateAdapter = true)
data class TokenOut(
    @Json(name = "access_token") val accessToken: String,
    @Json(name = "user_id") val userId: String,
)

@JsonClass(generateAdapter = true)
data class SpotCreate(
    val lat: Double,
    val lng: Double,
    @Json(name = "spot_type") val spotType: String,
    val payment: String,
    @Json(name = "vehicle_size") val vehicleSize: String? = null,
    @Json(name = "photo_staging_id") val photoStagingId: String? = null,
    val address: String? = null,
)

@JsonClass(generateAdapter = true)
data class SpotOut(
    val id: String,
    @Json(name = "reporter_id") val reporterId: String,
    val lat: Double,
    val lng: Double,
    @Json(name = "spot_type") val spotType: String,
    val payment: String,
    @Json(name = "vehicle_size") val vehicleSize: String?,
    @Json(name = "photo_url") val photoUrl: String?,
    val address: String? = null,
    val status: String,
    @Json(name = "reported_at") val reportedAt: String,
    @Json(name = "claimed_by") val claimedBy: String?,
    @Json(name = "reporter_level") val reporterLevel: String = "newbie",
    /** Set in hot areas: the median minutes nearby spots lasted. */
    @Json(name = "hot_minutes") val hotMinutes: Int? = null,
)

@JsonClass(generateAdapter = true)
data class FeedbackCreate(val type: String)

@JsonClass(generateAdapter = true)
data class StagedPhotoOut(
    @Json(name = "staging_id") val stagingId: String,
    @Json(name = "faces_blurred") val facesBlurred: Int,
    @Json(name = "plates_blurred") val platesBlurred: Int = 0,
    @Json(name = "preview_base64") val previewBase64: String,
)

@JsonClass(generateAdapter = true)
data class ActivityItem(
    @Json(name = "spot_id") val spotId: String,
    @Json(name = "spot_type") val spotType: String,
    val payment: String,
    val address: String? = null,
    @Json(name = "reported_at") val reportedAt: String,
    val status: String,
    @Json(name = "removed_reason") val removedReason: String?,
)

@JsonClass(generateAdapter = true)
data class ProfileOut(
    @Json(name = "user_id") val userId: String,
    @Json(name = "display_name") val displayName: String,
    val points: Int,
    @Json(name = "weekly_points") val weeklyPoints: Int,
    @Json(name = "weekly_rank") val weeklyRank: Int? = null,
    @Json(name = "successful_reports") val successfulReports: Int,
    val badges: List<Int>,
    val level: String = "newbie",
    @Json(name = "level_min_points") val levelMinPoints: Int = 0,
    @Json(name = "next_level") val nextLevel: String? = null,
    @Json(name = "next_level_points") val nextLevelPoints: Int? = null,
    val activity: List<ActivityItem>,
    val preferences: Preferences = Preferences(),
)

/** The driver's car and parking preferences. The plate itself is never
 * sent here — only what was looked up from it. */
@JsonClass(generateAdapter = true)
data class Preferences(
    @Json(name = "vehicle_size") val vehicleSize: String? = null,
    @Json(name = "car_label") val carLabel: String? = null,
    @Json(name = "has_disabled_permit") val hasDisabledPermit: Boolean = false,
    @Json(name = "home_city") val homeCity: String? = null,
) {
    val isEmpty: Boolean
        get() = vehicleSize == null && !hasDisabledPermit && homeCity == null
}

@JsonClass(generateAdapter = true)
data class VehicleLookupOut(
    val make: String,
    val model: String,
    val year: Int?,
    val body: String?,
    val size: String,
    val label: String,
)

/** A public off-street car park (OpenStreetMap). Most fields are often
 * missing in the source data — the UI only shows what's there. */
@JsonClass(generateAdapter = true)
data class ParkingLotOut(
    val id: Int,
    val lat: Double,
    val lng: Double,
    val name: String?,
    @Json(name = "name_en") val nameEn: String?,
    val kind: String?,
    val fee: String?,
    val capacity: Int?,
    val operator: String?,
    @Json(name = "distance_m") val distanceM: Double,
)

@JsonClass(generateAdapter = true)
data class CityOut(
    val name: String,
    @Json(name = "name_en") val nameEn: String? = null,
    val lat: Double,
    val lng: Double,
)

@JsonClass(generateAdapter = true)
data class LeaderboardEntry(
    val rank: Int,
    @Json(name = "user_id") val userId: String,
    @Json(name = "display_name") val displayName: String,
    @Json(name = "weekly_points") val weeklyPoints: Int,
    val level: String = "newbie",
)

@JsonClass(generateAdapter = true)
data class ApiErrorBody(val detail: String? = null)
