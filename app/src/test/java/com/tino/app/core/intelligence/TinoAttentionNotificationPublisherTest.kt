package com.tino.app.core.intelligence

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.tino.app.MainActivity
import com.tino.app.domain.intelligence.AttentionDigest
import com.tino.app.domain.intelligence.AttentionRecord
import com.tino.app.domain.intelligence.AttentionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowNotificationManager
import org.robolectric.shadows.ShadowPendingIntent

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32])
class TinoAttentionNotificationPublisherTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var notificationManager: NotificationManager
    private lateinit var publisher: TinoAttentionNotificationPublisher

    @Before
    fun setUp() {
        context.getSharedPreferences("tino-attention-notifications", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        notificationManager = context.getSystemService(NotificationManager::class.java)
        ShadowNotificationManager.reset()
        shadowOf(notificationManager).apply {
            setNotificationsEnabled(true)
        }
        publisher = TinoAttentionNotificationPublisher(context)
    }

    @Test
    fun publishesChannelAndStableNotificationForVisibleAttention() {
        publisher.publish(AttentionDigest(100L, listOf(record("attention-1"))))

        val manager = shadowOf(notificationManager)
        assertEquals(listOf("tino-attention"), manager.notificationChannels.map { it.id })
        assertEquals(1, manager.allNotifications.size)
        assertEquals(
            "TINO percebeu algo",
            manager.allNotifications.single().extras.getCharSequence(Notification.EXTRA_TITLE),
        )
        val contentIntent = shadowOf(manager.allNotifications.single().contentIntent)
        assertTrue(contentIntent.getSavedIntent().getBooleanExtra(MainActivity.EXTRA_OPEN_NOTIFICATION, false))
    }

    @Test
    fun removesNotificationWhenAttentionLeavesTheDigest() {
        publisher.publish(AttentionDigest(100L, listOf(record("attention-1"))))
        publisher.publish(AttentionDigest(200L, emptyList()))

        assertTrue(shadowOf(notificationManager).allNotifications.isEmpty())
    }

    private fun record(id: String) = AttentionRecord(
        id = id,
        insightId = id,
        subjectId = "product-1",
        title = "Café",
        explanation = "Restam poucas unidades.",
        evidenceIds = listOf("evidence-1"),
        relevance = 80,
        urgency = 70,
        confidence = 0.8,
        state = AttentionState.ACTIVE,
        createdAtEpochMs = 1L,
        lastSeenAtEpochMs = 100L,
    )
}
