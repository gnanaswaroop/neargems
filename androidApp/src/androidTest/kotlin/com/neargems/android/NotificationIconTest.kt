package com.neargems.android

import android.graphics.drawable.Icon
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.neargems.android.platform.AndroidPlaceNotifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The place notifications use the NearGems pin as their small icon, not Android's generic location icon,
 * so that the status bar and the lock screen show the app's own mark.
 */
@RunWith(AndroidJUnit4::class)
class NotificationIconTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = context.getSystemService(android.app.NotificationManager::class.java)

    @Before
    fun clearNotifications() {
        InstrumentationRegistry.getInstrumentation().uiAutomation
            .grantRuntimePermission(context.packageName, "android.permission.POST_NOTIFICATIONS")
        manager.cancelAll()
    }

    @Test
    fun theLivePlaceNotificationUsesTheNearGemsIcon() {
        AndroidPlaceNotifier(context).liveUpdate("Corner Cafe", "10 m away")

        assertSmallIcon(expectedIcon())
    }

    @Test
    fun theNewPlaceNotificationUsesTheNearGemsIcon() {
        AndroidPlaceNotifier(context).newPlace("Luigi", "500 m away")

        assertSmallIcon(expectedIcon())
    }

    private fun expectedIcon(): Icon = Icon.createWithResource(context, com.neargems.android.R.drawable.ic_stat_neargems)

    private fun assertSmallIcon(expected: Icon) {
        // The notification service applies a post a moment later, so wait for it to appear.
        val deadline = System.currentTimeMillis() + POST_TIMEOUT_MS
        var posted = emptyList<android.service.notification.StatusBarNotification>()
        while (posted.isEmpty() && System.currentTimeMillis() < deadline) {
            posted = manager.activeNotifications.filter { it.packageName == context.packageName }
            if (posted.isEmpty()) Thread.sleep(100)
        }
        assertTrue("a NearGems notification should be posted", posted.isNotEmpty())
        posted.forEach { status ->
            assertEquals(expected.toString(), status.notification.smallIcon.toString())
        }
    }

    private companion object {
        const val POST_TIMEOUT_MS = 5_000L
    }
}
