package com.tino.app.presentation.splash

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tino.app.R
import com.tino.app.ui.theme.TinoGreenDark
import com.tino.app.ui.theme.TinoPaper
import kotlinx.coroutines.delay

private val SplashMarkSize = 116.dp

@Composable
fun TinoSplashScreen(
    onFinished: () -> Unit,
) {
    var started by remember { mutableStateOf(false) }
    var wordmarkVisible by remember { mutableStateOf(false) }
    var exiting by remember { mutableStateOf(false) }

    val markScale by animateFloatAsState(
        targetValue = if (started) 1f else 0.65f,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = 280f,
        ),
        label = "tino-splash-mark-scale",
    )
    val markAlpha by animateFloatAsState(
        targetValue = if (started) 1f else 0f,
        animationSpec = tween(300),
        label = "tino-splash-mark-alpha",
    )
    val topOffset by animateDpAsState(
        targetValue = if (started) 0.dp else (-18).dp,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = 280f,
        ),
        label = "tino-splash-top-offset",
    )
    val bottomOffset by animateDpAsState(
        targetValue = if (started) 0.dp else 18.dp,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = 280f,
        ),
        label = "tino-splash-bottom-offset",
    )
    val topRotation by animateFloatAsState(
        targetValue = if (started) 0f else -5f,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = 280f,
        ),
        label = "tino-splash-top-rotation",
    )
    val bottomRotation by animateFloatAsState(
        targetValue = if (started) 0f else 5f,
        animationSpec = spring(
            dampingRatio = 0.72f,
            stiffness = 280f,
        ),
        label = "tino-splash-bottom-rotation",
    )
    val exitAlpha by animateFloatAsState(
        targetValue = if (exiting) 0f else 1f,
        animationSpec = tween(170, easing = FastOutSlowInEasing),
        label = "tino-splash-exit-alpha",
    )
    val exitScale by animateFloatAsState(
        targetValue = if (exiting) 1.04f else 1f,
        animationSpec = tween(170, easing = FastOutSlowInEasing),
        label = "tino-splash-exit-scale",
    )

    LaunchedEffect(Unit) {
        started = true
        delay(280)
        wordmarkVisible = true
        delay(540)
        exiting = true
        delay(180)
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TinoPaper)
            .graphicsLayer {
                alpha = exitAlpha
                scaleX = exitScale
                scaleY = exitScale
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SplitTinoMark(
                markScale = markScale,
                markAlpha = markAlpha,
                topOffset = topOffset,
                bottomOffset = bottomOffset,
                topRotation = topRotation,
                bottomRotation = bottomRotation,
            )
            AnimatedVisibility(
                visible = wordmarkVisible,
                enter = fadeIn(tween(300)) + expandHorizontally(
                    animationSpec = tween(350),
                    expandFrom = Alignment.Start,
                ),
            ) {
                Text(
                    text = "TINO",
                    color = TinoGreenDark,
                    style = MaterialTheme.typography.headlineLarge,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun SplitTinoMark(
    markScale: Float,
    markAlpha: Float,
    topOffset: androidx.compose.ui.unit.Dp,
    bottomOffset: androidx.compose.ui.unit.Dp,
    topRotation: Float,
    bottomRotation: Float,
) {
    Box(
        modifier = Modifier
            .size(SplashMarkSize)
            .graphicsLayer {
                scaleX = markScale
                scaleY = markScale
                alpha = markAlpha
            },
    ) {
        val markPainter = painterResource(R.drawable.tino_mark)
        Image(
            painter = markPainter,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    clipRect(bottom = size.height * 0.51f) {
                        this@drawWithContent.drawContent()
                    }
                }
                .graphicsLayer {
                    translationY = topOffset.toPx()
                    rotationZ = topRotation
                },
        )
        Image(
            painter = markPainter,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    clipRect(top = size.height * 0.51f) {
                        this@drawWithContent.drawContent()
                    }
                }
                .graphicsLayer {
                    translationY = bottomOffset.toPx()
                    rotationZ = bottomRotation
                },
        )
    }
}
