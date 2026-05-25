package com.example.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MyFirebaseMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d("FCMService", "Refreshed token: $token")
        // Upload token to current user's setting path if logged in
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            val db = FirebaseFirestore.getInstance()
            db.collection("users").document(uid).update("fcmToken", token)
                .addOnSuccessListener {
                    Log.d("FCMService", "FCM Token updated successfully in user document.")
                }
                .addOnFailureListener {
                    // Try to merge if update failed on document initialization
                    val mockData = mapOf("fcmToken" to token, "userId" to uid, "updatedAt" to System.currentTimeMillis())
                    db.collection("users").document(uid).set(mockData)
                }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d("FCMService", "From: ${remoteMessage.from}")

        // Check format and show local push notification helper
        val title = remoteMessage.notification?.title ?: remoteMessage.data["title"] ?: "DayFlow Sync Update"
        val body = remoteMessage.notification?.body ?: remoteMessage.data["body"] ?: "Sync completed successfully."

        showLocalNotification(title, body)
    }

    private fun showLocalNotification(title: String, messageBody: String) {
        val channelId = "dayflow_reminders_channel"
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        )

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "DayFlow Reminders and Habit Alerts",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        // Create notification using default system alert icon or standard fallback launcher icon
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)

        notificationManager.notify(System.currentTimeMillis().toInt(), notificationBuilder.build())
    }
}
