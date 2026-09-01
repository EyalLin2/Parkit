package com.parkit.app.api

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @POST("auth/dev-login")
    suspend fun devLogin(@Body body: DevLoginRequest): TokenOut

    @GET("spots")
    suspend fun nearbySpots(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radius_m") radiusM: Int,
    ): List<SpotOut>

    @POST("spots")
    suspend fun reportSpot(@Body body: SpotCreate): SpotOut

    @POST("spots/{id}/claim")
    suspend fun claimSpot(@Path("id") spotId: String): SpotOut

    @POST("spots/{id}/feedback")
    suspend fun submitFeedback(@Path("id") spotId: String, @Body body: FeedbackCreate)

    @DELETE("spots/{id}")
    suspend fun cancelSpot(@Path("id") spotId: String)

    @Multipart
    @POST("photos/stage")
    suspend fun stagePhoto(@Part file: MultipartBody.Part): StagedPhotoOut

    @GET("users/me")
    suspend fun myProfile(): ProfileOut

    @GET("leaderboard")
    suspend fun leaderboard(@Query("limit") limit: Int = 10): List<LeaderboardEntry>
}
