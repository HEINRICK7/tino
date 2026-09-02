package com.tino.app.ui.illustration

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.tino.app.R

/**
 * Static communication artwork. This catalog is deliberately independent from
 * the live mascot/presence system; it must never be used to drive voice UI.
 */
enum class TinoIllustrationState {
    LOADING,
    NOT_FOUND,
    SYNCING,
    OFFLINE,
    SUCCESS,
    WARNING,
    ERROR,
    SEARCHING,
    EXPLAINING,
    LEARNING,
    SLEEPING,
}

object TinoIllustrationAssetResolver {
    @DrawableRes
    fun resolve(state: TinoIllustrationState): Int = when (state) {
        TinoIllustrationState.LOADING -> R.drawable.tino_mascot_loading
        TinoIllustrationState.NOT_FOUND -> R.drawable.tino_mascot_not_found
        TinoIllustrationState.SYNCING -> R.drawable.tino_mascot_syncing
        TinoIllustrationState.OFFLINE -> R.drawable.tino_mascot_offline
        TinoIllustrationState.SUCCESS -> R.drawable.tino_mascot_success
        TinoIllustrationState.WARNING -> R.drawable.tino_mascot_warning
        TinoIllustrationState.ERROR -> R.drawable.tino_mascot_error
        TinoIllustrationState.SEARCHING -> R.drawable.tino_mascot_searching
        TinoIllustrationState.EXPLAINING -> R.drawable.tino_mascot_explaining
        TinoIllustrationState.LEARNING -> R.drawable.tino_mascot_learning
        TinoIllustrationState.SLEEPING -> R.drawable.tino_mascot_sleeping
    }
}

/**
 * Renders one static explanatory illustration. The caller owns the message;
 * accessibility text is explicit so artwork never becomes the only channel.
 */
@Composable
fun TinoIllustration(
    state: TinoIllustrationState,
    modifier: Modifier = Modifier,
    contentDescription: String? = null,
) {
    Image(
        painter = painterResource(TinoIllustrationAssetResolver.resolve(state)),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}
