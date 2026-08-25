package com.tino.app.core.ui

import android.app.Activity
import android.content.pm.ActivityInfo

/** Centralizes orientation policy so business screens remain portrait-first. */
class AppOrientationController(private val activity: Activity) {
    fun lockPortrait() {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    fun allowCameraLandscape() {
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
    }

    fun restorePortrait() {
        lockPortrait()
    }
}
