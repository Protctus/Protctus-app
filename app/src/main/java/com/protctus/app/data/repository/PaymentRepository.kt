package com.protctus.app.data.repository

import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

// ── API Data Classes ──

data class CreateOrderRequest(
    val amount: Int,           // Amount in paise (e.g., 34900 = ₹349)
    val currency: String = "INR",
    val receipt: String = "rcpt_${System.currentTimeMillis()}",
    @SerializedName("plan_id") val planId: String = ""
)

data class CreateOrderResponse(
    @SerializedName("order_id") val orderId: String,
    val amount: Int,
    val currency: String,
    @SerializedName("key_id") val keyId: String,
    val error: String? = null
)

data class VerifyPaymentRequest(
    @SerializedName("razorpay_order_id") val orderId: String,
    @SerializedName("razorpay_payment_id") val paymentId: String,
    @SerializedName("razorpay_signature") val signature: String,
    val email: String,
    val tier: String,
    val amount: Int,
    val receipt: String,
    @SerializedName("duration_months") val durationMonths: Int = 12
)

data class VerifyPaymentResponse(
    val verified: Boolean,
    @SerializedName("payment_id") val paymentId: String? = null,
    @SerializedName("order_id") val orderId: String? = null,
    val error: String? = null
)

@Singleton
class PaymentRepository @Inject constructor() {

    companion object {
        // Secure backend URL via standalone payments worker
        private const val BASE_URL = "https://protctus-payments-api.jatinsharmashivpuri02122008.workers.dev"
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Step 1: Create a Razorpay order via our backend.
     * Returns the order_id, amount, currency, and key_id needed to open the checkout.
     */
    suspend fun createOrder(amountPaise: Int, planId: String): Result<CreateOrderResponse> =
        withContext(Dispatchers.IO) {
            try {
                val body = gson.toJson(CreateOrderRequest(amount = amountPaise, planId = planId))
                val request = Request.Builder()
                    .url("$BASE_URL/create-order")
                    .post(body.toRequestBody(jsonMediaType))
                    .build()

                val response = client.newCall(request).execute()
                val responseBody = response.body?.string() ?: ""

                if (!response.isSuccessful) {
                    val errorResponse = try {
                        gson.fromJson(responseBody, CreateOrderResponse::class.java)
                    } catch (_: Exception) { null }
                    return@withContext Result.failure(
                        Exception(errorResponse?.error ?: "Order creation failed (HTTP ${response.code})")
                    )
                }

                val orderResponse = gson.fromJson(responseBody, CreateOrderResponse::class.java)
                Result.success(orderResponse)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /**
     * Step 3: Verify the payment signature via our backend.
     * Must be called AFTER Razorpay SDK returns success.
     */
    suspend fun verifyPayment(
        orderId: String,
        paymentId: String,
        signature: String,
        email: String,
        tier: String,
        amount: Int,
        receipt: String,
        durationMonths: Int = 12
    ): Result<VerifyPaymentResponse> = withContext(Dispatchers.IO) {
        try {
            val body = gson.toJson(
                VerifyPaymentRequest(
                    orderId = orderId,
                    paymentId = paymentId,
                    signature = signature,
                    email = email,
                    tier = tier,
                    amount = amount,
                    receipt = receipt,
                    durationMonths = durationMonths
                )
            )
            val request = Request.Builder()
                .url("$BASE_URL/verify-payment")
                .post(body.toRequestBody(jsonMediaType))
                .build()

            val response = client.newCall(request).execute()
            val responseBody = response.body?.string() ?: ""

            if (!response.isSuccessful) {
                val errorResponse = try {
                    gson.fromJson(responseBody, VerifyPaymentResponse::class.java)
                } catch (_: Exception) { null }
                return@withContext Result.failure(
                    Exception(errorResponse?.error ?: "Verification failed (HTTP ${response.code})")
                )
            }

            val verifyResponse = gson.fromJson(responseBody, VerifyPaymentResponse::class.java)
            if (!verifyResponse.verified) {
                return@withContext Result.failure(Exception("Payment signature mismatch"))
            }
            Result.success(verifyResponse)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
