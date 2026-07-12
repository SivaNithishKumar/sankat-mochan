package com.sankatmochan.mesh

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Keeps the app's process alive while the mesh is on. It does no work of its own.
 *
 * Why it must exist: the mesh (GATT server + advertiser in [com.sankatmochan.mesh.mesh.BleMeshService])
 * is a plain object in the UI process. Without a foreground service, Android freezes that process
 * minutes after the screen locks — but the Bluetooth controller keeps the advertisement on air and
 * keeps ACCEPTING connections. The phone then looks alive to the boards while the frozen app can
 * never answer the subscribe: the gateway logs "connected but never answered the subscribe within
 * 10s" over and over, and no SOS or rescue status can flow. A running foreground service exempts
 * the process from that freeze, which is the whole job of this class.
 */
class MeshKeepAliveService : Service() {

    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(
                CHANNEL, "Mesh link active", NotificationManager.IMPORTANCE_LOW
            ).apply { description = "Shown while this phone is part of the rescue mesh" }
        )
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_rescue)
            .setContentTitle("Rescue mesh is on")
            .setContentText("Staying connected so SOS messages and rescue updates keep flowing")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 2,
                    Intent(this, MainActivity::class.java),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )
            .build()
        // Must go foreground immediately; on API 34+ a type is mandatory. connectedDevice is
        // the platform's type for maintaining a Bluetooth link to a nearby device.
        ServiceCompat.startForeground(
            this,
            ONGOING_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE else 0
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "mesh_keepalive"
        private const val ONGOING_ID = 44

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, MeshKeepAliveService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MeshKeepAliveService::class.java))
        }
    }
}
