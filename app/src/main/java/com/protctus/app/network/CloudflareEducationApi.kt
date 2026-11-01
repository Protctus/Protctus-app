package com.protctus.app.network

import androidx.annotation.Keep
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

@Keep
data class EducationStatsResponse(
    val email: String,
    val currentStreak: Int,
    val maxStreak: Int,
    val totalXp: Int,
    val rank: String,
    val completedModules: Int,
    val accuracy: Float,
    val dailyProgress: Int,
    val dailyTarget: Int,
    val error: String? = null
)

@Keep
data class XpAddRequest(
    val xpGained: Int,
    val moduleCompleted: Boolean,
    val accuracy: Float
)

@Keep
data class GenericEducationResponse(
    val success: Boolean,
    val message: String? = null
)

interface CloudflareEducationApi {
    @GET("/api/education/stats")
    suspend fun getStats(
        @Header("Authorization") authHeader: String
    ): Response<EducationStatsResponse>

    @POST("/api/education/streak/increment")
    suspend fun incrementStreak(
        @Header("Authorization") authHeader: String
    ): Response<GenericEducationResponse>

    @POST("/api/education/xp/add")
    suspend fun addXp(
        @Header("Authorization") authHeader: String,
        @Body request: XpAddRequest
    ): Response<GenericEducationResponse>
}
