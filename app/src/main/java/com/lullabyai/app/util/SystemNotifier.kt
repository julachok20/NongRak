package com.lullabyai.app.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.lullabyai.app.NongRakApp
import com.lullabyai.app.R

/**
 * Posts real Android system notifications (the pop-up/tray kind). Pairs with NotificationRepository,
 * which records the in-app history row; this makes the device actually notify the parent.
 */
object SystemNotifier {
    private const val CHANNEL_ID = "nongrak_default"

    fun ensureChannel(ctx: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val mgr = ctx.getSystemService(NotificationManager::class.java) ?: return
            if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
                mgr.createNotificationChannel(
                    NotificationChannel(CHANNEL_ID, "การแจ้งเตือน NongRak AI", NotificationManager.IMPORTANCE_DEFAULT)
                        .apply { description = "วัคซีน พัฒนาการ และสุขภาพของลูกน้อย" }
                )
            }
        }
    }

    fun show(title: String, body: String?) {
        val ctx = NongRakApp.instance
        ensureChannel(ctx)
        // API 33+ needs the POST_NOTIFICATIONS runtime permission, requested in MainActivity.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val notif = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(title)
            .setContentText(body ?: "")
            .setStyle(NotificationCompat.BigTextStyle().bigText(body ?: ""))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        runCatching { NotificationManagerCompat.from(ctx).notify((System.currentTimeMillis() % Int.MAX_VALUE).toInt(), notif) }
    }
}
