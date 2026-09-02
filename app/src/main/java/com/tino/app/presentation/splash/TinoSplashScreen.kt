package com.tino.app.presentation.splash

import android.graphics.Color as AndroidColor
import android.media.MediaPlayer
import android.net.Uri
import android.content.Context
import android.view.Gravity
import android.view.View.MeasureSpec
import android.widget.FrameLayout
import android.widget.VideoView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.tino.app.R
import com.tino.app.ui.theme.TinoPaper
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.ceil
import kotlinx.coroutines.delay

private val SplashBackground = TinoPaper
private const val SplashFallbackTimeoutMillis = 12_000L
private const val SplashVideoWidth = 720f
private const val SplashVideoHeight = 1280f
private const val SplashContentScale = 0.72f

private class CropVideoView(context: Context) : VideoView(context) {
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec),
        )
    }
}

/** Keeps the top entrance flush with the screen while preserving the artwork's intended scale. */
private class SplashVideoLayout(context: Context) : FrameLayout(context) {
    val player = CropVideoView(context)

    init {
        setBackgroundColor(SplashBackground.toArgb())
        clipChildren = true
        addView(
            player,
            LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            ),
        )
    }

    override fun onSizeChanged(width: Int, height: Int, oldWidth: Int, oldHeight: Int) {
        super.onSizeChanged(width, height, oldWidth, oldHeight)
        if (width <= 0 || height <= 0) return

        val scale = (width / SplashVideoWidth) * SplashContentScale
        player.layoutParams = LayoutParams(
            ceil(SplashVideoWidth * scale).toInt(),
            ceil(SplashVideoHeight * scale).toInt(),
            Gravity.TOP or Gravity.CENTER_HORIZONTAL,
        )
    }
}

/** Plays the branded splash once and releases the platform player with the screen. */
@Composable
fun TinoSplashScreen(
    onFirstFrame: () -> Unit = {},
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val currentOnFirstFrame by rememberUpdatedState(onFirstFrame)
    val currentOnFinished by rememberUpdatedState(onFinished)
    val firstFrameSent = remember { AtomicBoolean(false) }
    val completionSent = remember { AtomicBoolean(false) }
    var videoView by remember { mutableStateOf<VideoView?>(null) }

    val sendFirstFrameOnce = {
        if (firstFrameSent.compareAndSet(false, true)) {
            currentOnFirstFrame()
        }
    }
    val finishOnce = {
        if (completionSent.compareAndSet(false, true)) {
            currentOnFinished()
        }
    }

    LaunchedEffect(Unit) {
        delay(SplashFallbackTimeoutMillis)
        sendFirstFrameOnce()
        finishOnce()
    }

    DisposableEffect(Unit) {
        onDispose {
            videoView?.setOnPreparedListener(null)
            videoView?.setOnCompletionListener(null)
            videoView?.setOnErrorListener(null)
            videoView?.setOnInfoListener(null)
            videoView?.stopPlayback()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashBackground),
    ) {
        AndroidView(
            factory = { viewContext ->
                SplashVideoLayout(viewContext).apply {
                    player.setBackgroundColor(SplashBackground.toArgb())
                    player.setOnPreparedListener { mediaPlayer ->
                        mediaPlayer.isLooping = false
                        mediaPlayer.setVolume(0f, 0f)
                        player.start()
                    }
                    player.setOnInfoListener { _, what, _ ->
                        if (what == MediaPlayer.MEDIA_INFO_VIDEO_RENDERING_START) {
                            player.setBackgroundColor(AndroidColor.TRANSPARENT)
                            sendFirstFrameOnce()
                        }
                        false
                    }
                    player.setOnCompletionListener {
                        sendFirstFrameOnce()
                        finishOnce()
                    }
                    player.setOnErrorListener { _, _, _ ->
                        sendFirstFrameOnce()
                        finishOnce()
                        true
                    }
                    player.setVideoURI(
                        Uri.parse(
                            "android.resource://${context.packageName}/${R.raw.tino_splash}",
                        ),
                    )
                    videoView = player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
    }
}
