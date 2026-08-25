package com.tino.app.core.sync

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.tino.app.core.database.DomainEventDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Outbox worker boundary. The cloud transport is intentionally injected later,
 * when the sync API contract is available; events remain safely PENDING locally.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val coordinator: SyncCoordinator,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        return when (coordinator.syncOnce()) {
            SyncAttemptResult.SUCCESS -> Result.success()
            SyncAttemptResult.RETRY -> Result.retry()
        }
    }
}
