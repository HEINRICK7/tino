package com.tino.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.ui.graphics.toArgb
import com.tino.app.core.auth.OidcAuthCoordinator
import com.tino.app.core.ui.AppOrientationController
import com.tino.app.ui.theme.TinoPaper
import com.tino.app.ui.theme.TinoTheme
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var oidcAuthCoordinator: OidcAuthCoordinator
    private lateinit var orientationController: AppOrientationController
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        orientationController = AppOrientationController(this)
        orientationController.lockPortrait()
        WindowCompat.setDecorFitsSystemWindows(window, true)
        window.statusBarColor = TinoPaper.toArgb()
        window.navigationBarColor = TinoPaper.toArgb()
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        setContent {
            TinoTheme {
                TinoApp(openNotification = intent.getBooleanExtra(EXTRA_OPEN_NOTIFICATION, false))
            }
        }
        oidcAuthCoordinator.handleRedirect(intent)
        requestNotificationPermissionIfNeeded()
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        oidcAuthCoordinator.handleRedirect(intent)
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED ||
            getPreferences(MODE_PRIVATE).getBoolean(NOTIFICATION_PERMISSION_ASKED, false)
        ) return
        getPreferences(MODE_PRIVATE).edit().putBoolean(NOTIFICATION_PERMISSION_ASKED, true).apply()
        window.decorView.post {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    companion object {
        const val NOTIFICATION_PERMISSION_ASKED = "notification_permission_asked"
        const val EXTRA_OPEN_NOTIFICATION = "com.tino.app.OPEN_NOTIFICATION"
    }
}
