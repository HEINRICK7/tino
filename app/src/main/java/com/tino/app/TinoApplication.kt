package com.tino.app

import android.app.Application
import android.os.Build
import androidx.work.Configuration
import androidx.hilt.work.HiltWorkerFactory
import com.tino.app.core.observability.AuditEventType
import com.tino.app.core.observability.AuditLogger
import com.tino.app.core.sync.SyncScheduler
import com.tino.app.core.intelligence.AttentionNotificationScheduler
import dagger.hilt.android.HiltAndroidApp
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Provider

@HiltAndroidApp
class TinoApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncScheduler: Provider<SyncScheduler>
    @Inject lateinit var attentionNotificationScheduler: Provider<AttentionNotificationScheduler>
    @Inject lateinit var auditLogger: Provider<AuditLogger>

    private val deferredRuntimeStarted = AtomicBoolean(false)
    private val startupExecutor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "tino-deferred-startup")
    }

    override fun onCreate() {
        super.onCreate()
    }

    /** Starts non-visual runtime work after the splash has rendered its first frame. */
    fun startDeferredRuntime() {
        if (!isMainProcess() || !deferredRuntimeStarted.compareAndSet(false, true)) return
        startupExecutor.execute {
            auditLogger.get().record(
                AuditEventType.APP_START,
                mapOf(
                    "build_id" to BuildConfig.TINO_BUILD_ID,
                    "build_channel" to BuildConfig.TINO_BUILD_CHANNEL,
                    "android_api" to Build.VERSION.SDK_INT.toString(),
                    "device_model" to "${Build.MANUFACTURER} ${Build.MODEL}".take(80),
                ),
            )
            syncScheduler.get().schedule()
            attentionNotificationScheduler.get().schedule()
            startupExecutor.shutdown()
        }
    }

    private fun isMainProcess(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            Application.getProcessName() == packageName
        } else {
            true
        }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
