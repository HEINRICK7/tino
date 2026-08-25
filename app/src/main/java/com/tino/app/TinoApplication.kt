package com.tino.app

import android.app.Application
import androidx.work.Configuration
import androidx.hilt.work.HiltWorkerFactory
import android.os.Build
import com.tino.app.core.observability.AuditEventType
import com.tino.app.core.observability.AuditLogger
import com.tino.app.core.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class TinoApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var syncScheduler: SyncScheduler
    @Inject lateinit var auditLogger: AuditLogger

    override fun onCreate() {
        super.onCreate()
        auditLogger.record(
            AuditEventType.APP_START,
            mapOf(
                "build_id" to BuildConfig.TINO_BUILD_ID,
                "build_channel" to BuildConfig.TINO_BUILD_CHANNEL,
                "android_api" to Build.VERSION.SDK_INT.toString(),
                "device_model" to "${Build.MANUFACTURER} ${Build.MODEL}".take(80),
            ),
        )
        syncScheduler.schedule()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
