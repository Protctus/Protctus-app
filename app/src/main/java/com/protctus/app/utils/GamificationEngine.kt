package com.protctus.app.utils

data class LevelData(
    val level: Int,
    val rankTitle: String,
    val currentLevelXp: Int,
    val nextLevelXp: Int,   
    val progress: Float     
)

object GamificationEngine {
    val xpThresholds = listOf(0, 100, 300, 600, 1000, 1500, 2500, 4000)
    val titles = listOf("Cyber Novice", "Network Defender", "Privacy Shield", "Virus Slayer", "Data Guardian", "Anonymity Ace", "Fortress Builder", "Cyber Knight")

    fun calculateLevel(totalXp: Int): LevelData {
        var currentLevelIndex = 0
        for (i in xpThresholds.indices) {
            if (totalXp >= xpThresholds[i]) {
                currentLevelIndex = i
            } else {
                break
            }
        }

        val level = currentLevelIndex + 1
        val rankTitle = titles.getOrNull(currentLevelIndex) ?: "Ascendant"

        val currentLevelStartXp = xpThresholds[currentLevelIndex]
        val currentLevelXp = totalXp - currentLevelStartXp

        val isMaxLevel = currentLevelIndex >= xpThresholds.size - 1
        val nextLevelStartXp = if (isMaxLevel) currentLevelStartXp else xpThresholds[currentLevelIndex + 1]
        
        val xpNeededForNext = if (isMaxLevel) 0 else nextLevelStartXp - currentLevelStartXp
        
        val progress = if (isMaxLevel) {
            1.0f 
        } else {
            (currentLevelXp.toFloat() / xpNeededForNext.toFloat()).coerceIn(0f, 1f)
        }

        return LevelData(
            level = level,
            rankTitle = rankTitle,
            currentLevelXp = currentLevelXp,
            nextLevelXp = xpNeededForNext,
            progress = progress
        )
    }
}
