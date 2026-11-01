package com.protctus.app.data.model

enum class SubscriptionTier(val weight: Int) {
    FREE(0),
    PREMIUM(1),
    ELITE(2);

    fun hasAccessTo(requiredTier: SubscriptionTier): Boolean {
        return this.weight >= requiredTier.weight
    }

    companion object {
        fun fromString(tier: String?): SubscriptionTier {
            return when (tier?.uppercase()) {
                "ELITE" -> ELITE
                "PREMIUM" -> PREMIUM
                else -> FREE
            }
        }
    }
}
