package com.protctus.app.engine

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.ValueCallback
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.JsResult
import android.webkit.JsPromptResult
import android.net.Uri
import com.google.gson.Gson
import com.protctus.app.BuildConfig
import com.protctus.app.network.ScanResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * InvisibleHydrationEngine securely loads Single Page Applications (SPAs)
 * in a headless WebView, extracts the hydrated DOM, and POSTs it to the
 * Cloudflare Worker for deep phishing analysis.
 */
class InvisibleHydrationEngine(private val context: Context) {

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    /**
     * Executes the hydration process and returns the ScanResponse from Cloudflare.
     */
    suspend fun hydrateAndScan(targetUrl: String): ScanResponse? = withContext(Dispatchers.Main) {
        var webView: WebView? = null
        try {
            // 1. Initialize Headless WebView on the Main Thread
            webView = WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = true
                    loadsImagesAutomatically = false // Save bandwidth/memory
                    domStorageEnabled = true
                    blockNetworkImage = true
                    
                    // Security Hardening: Disable features to prevent Sandbox Escapes / WebAssembly miners
                    setGeolocationEnabled(false)
                    mediaPlaybackRequiresUserGesture = true
                    allowFileAccess = false
                    allowContentAccess = false
                    @Suppress("DEPRECATION")
                    allowFileAccessFromFileURLs = false
                    @Suppress("DEPRECATION")
                    allowUniversalAccessFromFileURLs = false
                }

                // WebChromeClient Silencing: Drop all JS alert/confirm/prompt to prevent UI freezing
                webChromeClient = object : WebChromeClient() {
                    override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                        result?.cancel()
                        return true // Silently consumed
                    }
                    override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                        result?.cancel()
                        return true
                    }
                    override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
                        result?.cancel()
                        return true
                    }
                }
            }

            // 2. Load the URL and Wait for Hydration (Strict 3000ms Timeout to prevent CPU Pinning)
            val outerHtml = withTimeoutOrNull(3000L) {
                suspendCancellableCoroutine<String> { continuation ->
                webView.webViewClient = object : WebViewClient() {
                    // Intent Hijacking Defense: Block non-HTTP schemes
                    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                        val scheme = request?.url?.scheme?.lowercase() ?: return false
                        // Only allow http and https; block intent://, file://, content://, javascript:
                        return scheme != "http" && scheme != "https"
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        // 3. The Hydration Wait (Delay 1000ms for SPA to render)
                        Handler(Looper.getMainLooper()).postDelayed({
                            if (continuation.isActive) {
                                // 4. JavaScript Injection (DOM Extraction - Fork Bomb Defense: Max 500kb)
                                view?.evaluateJavascript(
                                    "(function() { return document.documentElement.outerHTML.substring(0, 500000); })();"
                                ) { htmlResult ->
                                    if (htmlResult != null && htmlResult != "null") {
                                        // evaluateJavascript returns a JSON-encoded string, unquote it
                                        val unquotedHtml = try {
                                            gson.fromJson(htmlResult, String::class.java)
                                        } catch (e: Exception) {
                                            htmlResult
                                        }
                                        continuation.resume(unquotedHtml)
                                    } else {
                                        continuation.resume("")
                                    }
                                }
                            }
                        }, 1000L) // 1000ms delay
                    }
                }

                continuation.invokeOnCancellation {
                    cleanupWebView(webView)
                }

                webView.loadUrl(targetUrl)
            }
            } // close withTimeoutOrNull

            // If empty HTML was extracted or timeout hit, abort.
            if (outerHtml.isNullOrBlank()) {
                return@withContext null
            }

            // 5. Secure POST to Backend
            return@withContext postToCloudflare(targetUrl, outerHtml)

        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        } finally {
            // 6. Strict Memory Management
            cleanupWebView(webView)
        }
    }

    private suspend fun postToCloudflare(url: String, html: String): ScanResponse? = withContext(Dispatchers.IO) {
        try {
            val apiSecret = BuildConfig.PHISHING_API_KEY

            val jsonPayload = JSONObject().apply {
                put("url", url)
                put("html", html)
            }.toString()

            val requestBody = jsonPayload.toRequestBody("application/json; charset=utf-8".toMediaType())

            val request = Request.Builder()
                .url("https://protctus-phishing-engine.jatinsharmashivpuri02122008.workers.dev/scan-hydrated")
                .header("X-Protctus-Auth", apiSecret)
                .post(requestBody)
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext null
                }
                
                val responseBody = response.body?.string() ?: return@withContext null
                return@withContext gson.fromJson(responseBody, ScanResponse::class.java)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext null
        }
    }

    private fun cleanupWebView(webView: WebView?) {
        try {
            webView?.let {
                it.stopLoading()
                it.clearHistory()
                it.clearCache(true)
                it.loadUrl("about:blank")
                it.removeAllViews()
                it.destroy()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
