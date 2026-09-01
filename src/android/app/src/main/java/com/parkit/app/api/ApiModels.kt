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
    @Json(name = "photo_staging_id") val photoStagingId: String? = null,
)

@JsonClass(generateAdapter = true)
data class SpotOut(
    val id: String,
    @Json(name = "reporter_id") val reporterId: String,
    val lat: Double,
    val lng: Double,
    @Json(name = "spot_type") val spotType: String,
    val payment: String,
    @Json(name = "photo_url") val photoUrl: String?,
    val status: String,
    @Json(name = "reported_at") val reportedAt: String,
    @Json(name = "claimed_by") val claimedBy: String?,
)

@JsonClass(generateAdapter = true)
data class FeedbackCreate(val type: String)

@JsonClass(generateAdapter = true)
data class StagedPhotoOut(
    @Json(name = "staging_id") val stagingId: String,
    @Json(name = "faces_blurred") val facesBlurred: Int,
    @Json(name = "preview_base64") val previewBase64: String,
)

@JsonClass(generateAdapter = true)
data class ActivityItem(
    @Json(name = "spot_id") val spotId: String,
    @Json(name = "spot_type") val spotType: String,
    val payment: String,
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
    @Json(name = "successful_reports") val successfulReports: Int,
    val badges: List<Int>,
    val activity: List<ActivityItem>,
)

@JsonClass(generateAdapter = true)
data class LeaderboardEntry(
    val rank: Int,
    @Json(name = "user_id") val userId: String,
    @Json(name = "display_name") val displayName: String,
    @Json(name = "weekly_points") val weeklyPoints: Int,
)

@JsonClass(generateAdapter = true)
data class ApiErrorBody(val detail: String? = null)
