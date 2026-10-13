package com.parkit.app.api

import okhttp3.MultipartBody
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @POST("auth/dev-login")
    suspend fun devLogin(@Body body: DevLoginRequest): TokenOut

    @POST("auth/refresh")
    suspend fun refreshToken(): TokenOut

    @POST("auth/email/request")
    suspend fun requestEmailOtp(@Body body: EmailOtpRequest): EmailOtpRequested

    @POST("auth/email/verify")
    suspend fun verifyEmailOtp(@Body body: EmailOtpVerify): TokenOut

    @POST("auth/otp/request")
    suspend fun requestOtp(@Body body: OtpRequest): OtpRequested

    @POST("auth/otp/verify")
    suspend fun verifyOtp(@Body body: OtpVerify): TokenOut

    @PATCH("users/me/details")
    suspend fun updateDetails(@Body body: DetailsUpdate): AccountOut

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

    @DELETE("users/me")
    suspend fun deleteAccount()

    @GET("users/me")
    suspend fun myProfile(): ProfileOut

    /** Sends the whole set: the screen edits all of it, and an explicit null
     * clears a value (the client serializes nulls for exactly this). */
    @PATCH("users/me/preferences")
    suspend fun updatePreferences(@Body body: Preferences): Preferences

    @GET("vehicles/lookup")
    suspend fun lookupVehicle(@Query("plate") plate: String): VehicleLookupOut

    @GET("parking-lots")
    suspend fun parkingLots(
        @Query("lat") lat: Double,
        @Query("lng") lng: Double,
        @Query("radius_m") radiusM: Int,
    ): List<ParkingLotOut>

    @GET("places/cities")
    suspend fun cities(): List<CityOut>

    @GET("leaderboard")
    suspend fun leaderboard(@Query("limit") limit: Int = 10, @Query("city") city: String? = null): List<LeaderboardEntry>
}
