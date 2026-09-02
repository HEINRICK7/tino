package com.tino.app.core.intelligence

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.tino.app.MainActivity
import com.tino.app.R
import com.tino.app.domain.intelligence.AttentionDigest
import com.tino.app.domain.intelligence.TinoAttentionEngine
import com.tino.app.domain.intelligence.TinoEvidenceEngine
import com.tino.app.domain.intelligence.TinoEvidenceSnapshotBuilder
import com.tino.app.domain.intelligence.TinoEvidenceRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

interface AttentionNotificationScheduler {
    fun schedule()

    /** Re-evaluates attention after local facts change, without waiting for the digest. */
    fun refreshNow()
}

@Singleton
class WorkManagerAttentionNotificationScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) : AttentionNotificationScheduler {
    override fun schedule() {
        runCatching {
            val request = PeriodicWorkRequestBuilder<TinoAttentionNotificationWorker>(6, TimeUnit.HOURS)
                .setInitialDelay(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "tino-attention-notifications",
                ExistingPeriodicWorkPolicy.KEEP,
                request,
            )
            refreshNow()
        }
    }

    override fun refreshNow() {
        runCatching {
            val request = OneTimeWorkRequestBuilder<TinoAttentionNotificationWorker>().build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "tino-attention-refresh",
                ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}

@Singleton
class TinoAttentionNotificationPublisher @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun publish(digest: AttentionDigest) {
        val manager = NotificationManagerCompat.from(context)
        val currentIds = digest.items.mapTo(mutableSetOf()) { it.id }
        val previousIds = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .getStringSet(PUBLISHED_IDS, emptySet())
            .orEmpty()
        previousIds.filterNot { it in currentIds }.forEach { manager.cancel(it.hashCode()) }
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(PUBLISHED_IDS, currentIds)
            .apply()
        if (digest.isEmpty || Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return

        ensureChannel()
        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(MainActivity.EXTRA_OPEN_NOTIFICATION, true)
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        digest.items.forEach { item ->
            manager.notify(
                item.id.hashCode(),
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(R.drawable.tino_app_icon)
                    .setContentTitle("TINO percebeu algo")
                    .setContentText(item.title)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(item.explanation))
                    .setContentIntent(pendingIntent)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                    .build(),
            )
        }
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Percepções do TINO",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Sinais importantes percebidos pelo TINO"
            },
        )
    }

    private companion object {
        const val CHANNEL_ID = "tino-attention"
        const val PREFERENCES = "tino-attention-notifications"
        const val PUBLISHED_IDS = "published_ids"
    }
}

@HiltWorker
class TinoAttentionNotificationWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val snapshotBuilder: TinoEvidenceSnapshotBuilder,
    private val attentionEngine: TinoAttentionEngine,
    private val evidenceRepository: TinoEvidenceRepository,
    private val publisher: TinoAttentionNotificationPublisher,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result = runCatching {
        val snapshot = snapshotBuilder.build(screen = "Notification")
        val analysis = TinoEvidenceEngine.analyze(snapshot)
        evidenceRepository.upsertAll(analysis.evidence)
        val now = snapshot.nowEpochMs
        val items = attentionEngine.reconcile(analysis, now)
        publisher.publish(AttentionDigest(now, items.take(3)))
        Result.success()
    }.getOrElse { Result.retry() }
}
