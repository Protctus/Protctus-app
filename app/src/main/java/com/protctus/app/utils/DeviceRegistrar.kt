package com.protctus.app.utils

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

object DeviceRegistrar {

    fun registerDevice(context: Context) {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser ?: return
        val uid = user.uid
        val email = user.email ?: ""

        val firestore = FirebaseFirestore.getInstance(
            com.google.firebase.FirebaseApp.getInstance(), "protctus"
        )


        val userUpdate = hashMapOf<String, Any>(
            "email" to email,
            "lastLogin" to System.currentTimeMillis()
        )
        firestore.collection("users").document(uid)
            .set(userUpdate, SetOptions.merge())

        // ── 2. Build a unique, stable device ID ──
        val androidId = Settings.Secure.getString(
            context.contentResolver, Settings.Secure.ANDROID_ID
        ) ?: "unknown"
        val deviceDocId = "${uid}_${androidId}"

        // ── 3. Write device telemetry ──
        val deviceData = hashMapOf<String, Any>(
            "userId" to uid,
            "model" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "os" to "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            "androidId" to androidId,
            "status" to "secure",
            "lastSeen" to System.currentTimeMillis()
        )
        firestore.collection("devices").document(deviceDocId)
            .set(deviceData, SetOptions.merge())
    }
}
