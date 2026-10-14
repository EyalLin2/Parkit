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
    /** First phone sign-in: name + email still to fill in. */
    @Json(name = "needs_profile") val needsProfile: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class OtpRequest(val phone: String)

@JsonClass(generateAdapter = true)
data class OtpRequested(
    val phone: String,
    @Json(name = "resend_after_seconds") val resendAfterSeconds: Int = 60,
    /** Only from a local dev server: the code, so the emulator needs no SMS. */
    @Json(name = "dev_code") val devCode: String? = null,
)

@JsonClass(generateAdapter = true)
data class OtpVerify(val phone: String, val code: String)

@JsonClass(generateAdapter = true)
data class EmailOtpRequest(val email: String)

@JsonClass(generateAdapter = true)
data class EmailOtpRequested(
    val email: String,
    @Json(name = "resend_after_seconds") val resendAfterSeconds: Int = 60,
    @Json(name = "dev_code") val devCode: String? = null,
)

@JsonClass(generateAdapter = true)
data class EmailOtpVerify(val email: String, val code: String)

@JsonClass(generateAdapter = true)
data class DetailsUpdate(
    @Json(name = "first_name") val firstName: String,
    @Json(name = "last_name") val lastName: String,
    val email: String,
)

@JsonClass(generateAdapter = true)
data class AccountOut(
    val phone: String?,
    @Json(name = "first_name") val firstName: String?,
    @Json(name = "last_name") val lastName: String?,
    val email: String?,
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
    /** Optional heads-up for the next driver (max 120 chars). */
    val note: String? = null,
    // Where the phone is — the server only accepts reports made near the spot.
    @Json(name = "reporter_lat") val reporterLat: Double? = null,
    @Json(name = "reporter_lng") val reporterLng: Double? = null,
    @Json(name = "reporter_accuracy_m") val reporterAccuracyM: Float? = null,
    /** "I'm leaving" from a saved car: the server adds a hand-off bonus. */
    val handoff: Boolean = false,
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
    val note: String? = null,
    val status: String,
    @Json(name = "reported_at") val reportedAt: String,
    @Json(name = "claimed_by") val claimedBy: String?,
    @Json(name = "reporter_level") val reporterLevel: String = "newbie",
    /** Set in hot areas: the median minutes nearby spots lasted. */
    @Json(name = "hot_minutes") val hotMinutes: Int? = null,
)

@JsonClass(generateAdapter = true)
data class FeedbackCreate(val type: String)

/** "This photo / note / name is offensive" — reason is photo, note, user or other. */
@JsonClass(generateAdapter = true)
data class ContentReport(val reason: String, val note: String? = null)

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
    /** Left to spend in the rewards shop (the level follows [points]). */
    @Json(name = "points_balance") val pointsBalance: Int = 0,
    val avatar: String? = null,
    @Json(name = "weekly_rank") val weeklyRank: Int? = null,
    @Json(name = "weekly_city_rank") val weeklyCityRank: Int? = null,
    @Json(name = "successful_reports") val successfulReports: Int,
    @Json(name = "total_reports") val totalReports: Int = 0,
    val badges: List<Int>,
    val level: String = "newbie",
    @Json(name = "level_min_points") val levelMinPoints: Int = 0,
    @Json(name = "next_level") val nextLevel: String? = null,
    @Json(name = "next_level_points") val nextLevelPoints: Int? = null,
    val activity: List<ActivityItem>,
    val preferences: Preferences = Preferences(),
    val account: AccountOut? = null,
)

/** The driver's car and parking preferences. The plate itself is never
 * sent here — only what was looked up from it. */
@JsonClass(generateAdapter = true)
data class Preferences(
    @Json(name = "vehicle_size") val vehicleSize: String? = null,
    @Json(name = "car_label") val carLabel: String? = null,
    /** "#RRGGBB" from the registry's colour for the car. */
    @Json(name = "car_paint") val carPaint: String? = null,
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
    val paint: String? = null,
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
    /** OSM "charge" (e.g. "12 ILS/hour") — rarely mapped. */
    val charge: String? = null,
    @Json(name = "opening_hours") val openingHours: String? = null,
    @Json(name = "distance_m") val distanceM: Double,
    @Json(name = "data_updated_at") val dataUpdatedAt: String? = null,
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
    val avatar: String? = null,
)

@JsonClass(generateAdapter = true)
data class AvatarUpdate(val avatar: String?)

@JsonClass(generateAdapter = true)
data class FeedbackIn(
    val kind: String,
    val message: String,
    @Json(name = "app_version") val appVersion: String?,
    val device: String?,
)

@JsonClass(generateAdapter = true)
data class ApiErrorBody(val detail: String? = null)

/** The server stores a spot's street in Hebrew; in the English UI it is
 * looked up again in English (see SpotAddressCache), so this is null there. */
val SpotOut.localAddress: String?
    get() = address.takeIf { java.util.Locale.getDefault().language != "en" }

/** One item in the rewards shop: a ParkIt car finish ("cosmetic") or a
 * business's benefit ("partner"). */
@JsonClass(generateAdapter = true)
data class RewardOut(
    val id: Int,
    val kind: String,
    val key: String?,
    @Json(name = "title_he") val titleHe: String,
    @Json(name = "title_en") val titleEn: String,
    val partner: String?,
    @Json(name = "description_he") val descriptionHe: String?,
    @Json(name = "description_en") val descriptionEn: String?,
    val cost: Int,
    val owned: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class RedemptionOut(
    @Json(name = "reward_id") val rewardId: Int,
    @Json(name = "title_he") val titleHe: String,
    @Json(name = "title_en") val titleEn: String,
    val partner: String?,
    val code: String,
    @Json(name = "created_at") val createdAt: String,
)

@JsonClass(generateAdapter = true)
data class ShopOut(
    val balance: Int,
    val rewards: List<RewardOut>,
    val redemptions: List<RedemptionOut>,
)

/** The sign reader's answer for a regular driver, right now. */
@JsonClass(generateAdapter = true)
data class SignReading(
    val verdict: String, // allowed | not_allowed | unclear
    val until: String? = null,
    val paid: Boolean? = null,
    @Json(name = "summary_he") val summaryHe: String,
    @Json(name = "summary_en") val summaryEn: String,
)

/** What the report photo seems to show — pre-fills the form (advice only). */
@JsonClass(generateAdapter = true)
data class PhotoHints(
    @Json(name = "looks_like_free_spot") val looksLikeFreeSpot: Boolean = true,
    @Json(name = "blue_white_curb") val blueWhiteCurb: Boolean = false,
    @Json(name = "disabled_spot") val disabledSpot: Boolean = false,
)

@JsonClass(generateAdapter = true)
data class GoogleLogin(@Json(name = "id_token") val idToken: String)

