package com.protctus.app.viewmodel

import android.app.Activity
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.firestore.FirebaseFirestore
import com.protctus.app.data.local.SettingsManager
import com.protctus.app.data.repository.PaymentRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

data class PremiumPlan(
    val id: String,
    val title: String,         // e.g., "Annual" or "Monthly"
    val subtitle: String,      // e.g., "First 30 days free - Then ₹349/Year"
    val badgeText: String?,    // e.g., "Best Value"
    val amountCents: Int,      // Amount in PAISE (e.g., 34900 = ₹349)
    val currency: String
)

enum class PaymentState {
    IDLE,
    CREATING_ORDER,
    CHECKOUT_OPEN,
    VERIFYING,
    SUCCESS,
    FAILED
}

@HiltViewModel
class PremiumViewModel @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val paymentRepository: PaymentRepository,
    private val settingsManager: SettingsManager,
    private val entitlementManager: com.protctus.app.data.repository.EntitlementManager
) : ViewModel() {

    private val _plans = MutableStateFlow<List<PremiumPlan>>(emptyList())
    val plans: StateFlow<List<PremiumPlan>> = _plans.asStateFlow()

    private val _paymentState = MutableStateFlow(PaymentState.IDLE)
    val paymentState: StateFlow<PaymentState> = _paymentState.asStateFlow()

    private val _paymentError = MutableStateFlow<String?>(null)
    val paymentError: StateFlow<String?> = _paymentError.asStateFlow()

    // Server-authoritative tier from EntitlementManager (EncryptedSharedPrefs + D1 sync)
    val isPremiumUser: StateFlow<Boolean> = entitlementManager.currentTier
        .map { it.weight > 0 }
        .stateIn(viewModelScope, SharingStarted.Eagerly, entitlementManager.currentTier.value.weight > 0)

    val subscriptionTier: StateFlow<Int> = entitlementManager.currentTier
        .map { it.weight }
        .stateIn(viewModelScope, SharingStarted.Eagerly, entitlementManager.currentTier.value.weight)

    // Temporary storage for the current order context
    private var currentOrderId: String? = null
    private var currentPlanId: String? = null
    private var currentDurationMonths: Int = 12

    init {
        fetchDynamicPlans()
    }

    private fun fetchDynamicPlans() {
        viewModelScope.launch {
            try {
                // Fetch pricing strictly from Firebase to prevent static decompiling
                val snapshot = firestore.collection("subscription_plans").get().await()
                val parsedPlans = snapshot.documents.mapNotNull { doc ->
                    val id = doc.id
                    val title = doc.getString("title") ?: return@mapNotNull null
                    val subtitle = doc.getString("subtitle") ?: ""
                    val badgeText = doc.getString("badgeText")
                    val amountCents = doc.getLong("amountCents")?.toInt() ?: return@mapNotNull null
                    val currency = doc.getString("currency") ?: "INR"
                    
                    PremiumPlan(id, title, subtitle, badgeText, amountCents, currency)
                }
                
                _plans.value = if (parsedPlans.isNotEmpty()) {
                    parsedPlans.sortedByDescending { it.amountCents }
                } else {
                    fallbackPlans()
                }
            } catch (e: Exception) {
                _plans.value = fallbackPlans()
            }
        }
    }

    /** Hardcoded fallback plans — VC-approved pricing strategy */
    private fun fallbackPlans(): List<PremiumPlan> = listOf(
        PremiumPlan(
            id = "elite_annual",
            title = "Elite Annual",
            subtitle = "God Mode — ₹799/year (₹67/mo)",
            badgeText = "GOD MODE",
            amountCents = 79900,
            currency = "INR"
        ),
        PremiumPlan(
            id = "premium_annual",
            title = "Premium Annual",
            subtitle = "Full protection — ₹349/year (₹29/mo)",
            badgeText = "BEST VALUE",
            amountCents = 34900,
            currency = "INR"
        ),
        PremiumPlan(
            id = "premium_monthly",
            title = "Premium Monthly",
            subtitle = "₹49/month — Cancel anytime",
            badgeText = null,
            amountCents = 4900,
            currency = "INR"
        )
    )

    /**
     * Step 1: Initiate purchase — calls backend to create a Razorpay Order,
     * then opens the Razorpay checkout modal on the Activity.
     */
    fun initiatePurchase(plan: PremiumPlan, activity: Activity, durationMonths: Int = 12) {
        if (_paymentState.value == PaymentState.CREATING_ORDER || _paymentState.value == PaymentState.VERIFYING) return

        _paymentState.value = PaymentState.CREATING_ORDER
        _paymentError.value = null
        currentPlanId = plan.id
        currentDurationMonths = durationMonths

        viewModelScope.launch {
            val result = paymentRepository.createOrder(
                amountPaise = plan.amountCents,
                planId = plan.id
            )

            result.onSuccess { order ->
                currentOrderId = order.orderId

                // Open Razorpay Checkout on the UI thread
                try {
                    val checkout = com.razorpay.Checkout()
                    checkout.setKeyID(order.keyId)

                    val options = org.json.JSONObject().apply {
                        put("name", "Protctus")
                        put("description", "${plan.title} Subscription")
                        put("order_id", order.orderId)
                        put("currency", order.currency)
                        put("amount", order.amount)
                        put("theme", org.json.JSONObject().apply {
                            put("color", "#1A1B1F")
                        })
                        put("prefill", org.json.JSONObject().apply {
                            val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
                            user?.email?.let { put("email", it) }
                        })
                    }

                    _paymentState.value = PaymentState.CHECKOUT_OPEN
                    checkout.open(activity, options)
                } catch (e: Exception) {
                    Log.e("PremiumVM", "Checkout open failed: ${e.message}")
                    _paymentError.value = "Failed to open payment gateway"
                    _paymentState.value = PaymentState.FAILED
                }
            }

            result.onFailure { e ->
                Log.e("PremiumVM", "Order creation failed: ${e.message}")
                _paymentError.value = e.message ?: "Order creation failed"
                _paymentState.value = PaymentState.FAILED
            }
        }
    }

    /**
     * Step 2: Called by MainActivity's PaymentResultWithDataListener on SUCCESS.
     * Verifies the signature with the backend, then unlocks premium.
     */
    fun onPaymentSuccess(paymentId: String, orderId: String?, signature: String?) {
        val finalOrderId = orderId ?: currentOrderId
        if (finalOrderId == null || signature == null) {
            _paymentError.value = "Missing payment verification data"
            _paymentState.value = PaymentState.FAILED
            return
        }

        _paymentState.value = PaymentState.VERIFYING

        viewModelScope.launch {
            val userEmail = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email ?: "unknown@example.com"
            val tierStr = when {
                currentPlanId?.contains("elite") == true -> "ELITE"
                currentPlanId?.contains("premium") == true -> "PREMIUM"
                else -> "PREMIUM"
            }
            val amount = _plans.value.find { it.id == currentPlanId }?.amountCents ?: 0
            val receipt = finalOrderId
            
            val result = paymentRepository.verifyPayment(
                orderId = finalOrderId,
                paymentId = paymentId,
                signature = signature,
                email = userEmail,
                tier = tierStr,
                amount = amount,
                receipt = receipt,
                durationMonths = currentDurationMonths
            )

            result.onSuccess {
                // Determine Tier based on Plan ID
                val tier = when {
                    currentPlanId?.contains("elite") == true -> 2
                    currentPlanId?.contains("premium") == true -> 1
                    else -> 1 // Default to Premium
                }
                
                val subscriptionTier = when (tier) {
                    2 -> com.protctus.app.data.model.SubscriptionTier.ELITE
                    1 -> com.protctus.app.data.model.SubscriptionTier.PREMIUM
                    else -> com.protctus.app.data.model.SubscriptionTier.FREE
                }

                // ── STEP 1: SET TIER LOCALLY FIRST (instant, no network) ──
                // This writes to SharedPreferences + DataStore + StateFlow immediately.
                // Guarantees premium is recognized on next cold start even if server push fails.
                entitlementManager.setTierLocally(subscriptionTier)

                // Legacy SettingsManager write (for any code still reading from it)
                settingsManager.setSubscriptionTier(tier)
                settingsManager.setPremiumUser(isPremium = true, paymentId = paymentId)
                
                // ── STEP 2: Push to server (best-effort, non-blocking) ──
                // If this fails, the local cache is already correct from Step 1.
                try {
                    entitlementManager.updateTierOnServer(subscriptionTier)
                } catch (e: Exception) {
                    Log.e("PremiumVM", "Server push failed (local cache is safe): ${e.message}")
                }

                // ── STEP 3: Sync back from server (optional, catches any server-side adjustments) ──
                try {
                    entitlementManager.syncWithServer()
                } catch (e: Exception) {
                    Log.e("PremiumVM", "Server sync failed (local cache is safe): ${e.message}")
                }
                
                _paymentState.value = PaymentState.SUCCESS
                currentOrderId = null
            }

            result.onFailure { e ->
                Log.e("PremiumVM", "Payment verification failed: ${e.message}")
                _paymentError.value = "Payment verification failed. Contact support if amount was deducted."
                _paymentState.value = PaymentState.FAILED
            }
        }
    }

    /**
     * Called by MainActivity's PaymentResultWithDataListener on FAILURE.
     */
    fun onPaymentError(errorCode: Int, errorMessage: String?) {
        Log.e("PremiumVM", "Payment failed [$errorCode]: $errorMessage")
        _paymentError.value = errorMessage ?: "Payment failed"
        _paymentState.value = PaymentState.FAILED
        currentOrderId = null
    }

    fun resetPaymentState() {
        _paymentState.value = PaymentState.IDLE
        _paymentError.value = null
    }
}
