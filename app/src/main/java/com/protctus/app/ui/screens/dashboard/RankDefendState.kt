package com.protctus.app.ui.screens.dashboard

import androidx.compose.ui.graphics.Color


data class RankData(
    val levelNumber: Int,
    val rankTitle: String,
    val rankDescription: String,
    val themeColor: Color,
    val currentXp: Int,
    val maxXp: Int = 1000,
    val deviceIntegrity: Float,
    val accountArmor: Float,
    val networkShield: Float,
    val nextMissionText: String,
    val nextXpReward: Int,
    val iconRes: Int = 0, // Default added for backwards compatibility
    val isClaimed: Boolean = false
)