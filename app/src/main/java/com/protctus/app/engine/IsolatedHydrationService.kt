package com.protctus.app.engine

import android.app.Service
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * IsolatedHydrationService runs in a completely separate Android process (':hydration').
 * This is a strict Red Team security mitigation.
 * If a malicious SPA attempts a Sandbox Escape, a DOM Fork Bomb, or causes an OutOfMemoryError,
 * only this isolated process will crash. The main VPN and UI processes remain entirely unaffected.
 */
class IsolatedHydrationService : Service() {

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    
    private lateinit var hydrationEngine: InvisibleHydrationEngine

    // Messenger for receiving IPC requests from the main process
    private val messenger = Messenger(IncomingHandler())

    inner class IncomingHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                MSG_HYDRATE_SCAN -> {
                    val url = msg.data.getString(EXTRA_URL)
                    val replyTo = msg.replyTo

                    if (url != null && replyTo != null) {
                        serviceScope.launch {
                            try {
                                val result = hydrationEngine.hydrateAndScan(url)
                                val replyMsg = Message.obtain(null, MSG_SCAN_RESULT)
                                val bundle = Bundle().apply {
                                    putBoolean(EXTRA_SUCCESS, result != null)
                                    // Normally we'd parcel the ScanResponse, but for simplicity we return success status
                                    // and let the main app handle failure or we serialize to JSON.
                                    if (result != null) {
                                        putString(EXTRA_JSON_RESULT, com.google.gson.Gson().toJson(result))
                                    }
                                }
                                replyMsg.data = bundle
                                replyTo.send(replyMsg)
                            } catch (e: RemoteException) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
                else -> super.handleMessage(msg)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        hydrationEngine = InvisibleHydrationEngine(this)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return messenger.binder
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceJob.cancel()
    }

    companion object {
        const val MSG_HYDRATE_SCAN = 1
        const val MSG_SCAN_RESULT = 2
        
        const val EXTRA_URL = "url"
        const val EXTRA_SUCCESS = "success"
        const val EXTRA_JSON_RESULT = "json_result"
    }
}
