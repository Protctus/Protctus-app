package com.protctus.app.network

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import androidx.annotation.Keep

@Keep
data class TierResponse(
    val email: String,
    val tier: String,
    val error: String? = null
)

@Keep
data class TierRequest(
    val tier: String
)

@Keep
data class UserStatusResponse(
    val email: String,
    val tier: String,
    val status: String,
    val expires_at: String? = null
)

interface CloudflareTierApi {
    @GET("/api/user/tier")
    suspend fun getUserTier(
        @Header("Authorization") authHeader: String
    ): Response<TierResponse>

    @POST("/api/user/tier")
    suspend fun updateUserTier(
        @Header("Authorization") authHeader: String,
        @Body request: TierRequest
    ): Response<TierResponse>

    @GET("/api/user/status")
    suspend fun getUserStatus(
        @Header("Authorization") authHeader: String
    ): Response<UserStatusResponse>
}
