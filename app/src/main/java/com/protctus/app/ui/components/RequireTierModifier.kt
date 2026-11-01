package com.protctus.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.protctus.app.data.model.SubscriptionTier

@Composable
fun Modifier.requireTier(
    requiredTier: SubscriptionTier,
    currentTier: SubscriptionTier,
    onInterceptClick: () -> Unit,
    onClick: () -> Unit
): Modifier {
    return this.clickable {
        if (currentTier.hasAccessTo(requiredTier)) {
            onClick()
        } else {
            onInterceptClick()
        }
    }
}
