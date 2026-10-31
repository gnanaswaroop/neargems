package com.neargems.android.platform

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.neargems.platform.PlaceNotifier

/**
 * The place on screen, kept in the notification shade while the app runs, and a soft alert when a
 * new place is found. On Android 16 and later the shade entry is a promoted live update, so the
 * place name and distance show in the status bar.
 */
class AndroidPlaceNotifier(private val context: Context) : PlaceNotifier {
    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(
            NotificationChannel(LIVE_CHANNEL, "Place on screen", NotificationManager.IMPORTANCE_LOW).apply {
                description = "The place NearGems is showing, kept in the notification shade."
                setSound(null, null)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(NEW_CHANNEL, "New places", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A soft sound when NearGems finds a new place to suggest."
            },
        )
    }

    override fun liveUpdate(title: String, detail: String) {
        val builder = Notification.Builder(context, LIVE_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(title)
            .setContentText(detail)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_NAVIGATION)
        if (Build.VERSION.SDK_INT >= ANDROID_16) {
            // The promoted form shows in the status bar as a live update.
            builder.setRequestPromotedOngoing(true).setShortCriticalText(title)
        }
        manager.notify(LIVE_ID, builder.build())
    }

    override fun newPlace(title: String, detail: String) {
        val notification = Notification.Builder(context, NEW_CHANNEL)
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle(title)
            .setContentText(detail)
            .setAutoCancel(true)
            .build()
        manager.notify(NEW_ID, notification)
    }

    override fun clear() {
        manager.cancel(LIVE_ID)
    }

    private companion object {
        const val LIVE_CHANNEL = "place-on-screen"
        const val NEW_CHANNEL = "new-places"
        const val LIVE_ID = 2
        const val NEW_ID = 3
        const val ANDROID_16 = 36
    }
}
