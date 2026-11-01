package com.protctus.edge.phishing

import android.app.Activity
import android.content.Intent
import android.net.VpnService
import android.util.Log

object PhishingEdgeManager {

    private const val TAG = "PhishingEdgeManager"
    const val VPN_REQUEST_CODE = 9001

    fun startVpn(activity: Activity) {
        val intent = VpnService.prepare(activity)
        if (intent != null) {
            activity.startActivityForResult(intent, VPN_REQUEST_CODE)
        } else {
            launchService(activity)
        }
    }

    fun handleVpnPermissionResult(activity: Activity, requestCode: Int, resultCode: Int) {
        if (requestCode == VPN_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            launchService(activity)
        } else {
            Log.e(TAG, "VPN permission denied by user.")
        }
    }

    fun stopVpn(activity: Activity) {
        activity.stopService(Intent(activity, PhishingVpnService::class.java))
        Log.d(TAG, "PhishingVpnService stopped.")
    }

    private fun launchService(activity: Activity) {
        val serviceIntent = Intent(activity, PhishingVpnService::class.java)
        activity.startService(serviceIntent)
        Log.d(TAG, "PhishingVpnService started.")
    }
}
