package com.fiesta.datasaving

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

class SmartPromoExpiryWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : Worker(appContext, workerParams) {
    override fun doWork(): Result {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channelId = "smart_promo_expiry"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(channelId, "Smart promo reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Reminders about manually recorded Smart promo expiry dates"
                }
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Check your Smart promo")
            .setContentText("Your recorded promo is approaching expiry. Verify its status and renewal options in the official Smart App.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Your recorded Smart promo is approaching expiry. Check the official Smart App. Rollover depends on your exact promo and timely renewal."))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        return try {
            NotificationManagerCompat.from(applicationContext).notify(2108, notification)
            Result.success()
        } catch (_: SecurityException) {
            Result.success()
        }
    }
}
