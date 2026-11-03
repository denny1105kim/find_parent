package com.caregrandpapa.data

import android.content.Context
import android.os.BatteryManager
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/** Only the latest check-in and device snapshot are retained on the pair. */
object CareStatusRepository {
    suspend fun checkIn(pairId: String, needsHelp: Boolean) {
        Auth.awaitUid()
        FirebaseFirestore.getInstance().collection("pairs").document(pairId).update(
            mapOf("checkIn" to if (needsHelp) "help" else "okay", "checkInAt" to FieldValue.serverTimestamp()),
        ).await()
    }

    suspend fun publishDevice(context: Context, pairId: String) {
        Auth.awaitUid()
        val battery = context.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val percent = battery.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        FirebaseFirestore.getInstance().collection("pairs").document(pairId).update(
            mapOf(
                "batteryPercent" to percent.coerceIn(-1, 100),
                "charging" to battery.isCharging,
                "lastSeenAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }
}
