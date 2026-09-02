package com.tino.app.ui.illustration

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.tino.app.R
import com.tino.app.domain.profile.BusinessActivity

/** Business identity artwork; independent from static TINO state artwork. */
object BusinessActivityIllustrationResolver {
    @DrawableRes
    fun resolve(activity: BusinessActivity): Int = when (activity) {
        BusinessActivity.MERCADINHO -> R.drawable.tino_business_mercadinho
        BusinessActivity.ACOUGUE -> R.drawable.tino_business_acougue
        BusinessActivity.VERDUREIRA -> R.drawable.tino_business_verdureira
        BusinessActivity.PADARIA -> R.drawable.tino_business_padaria
        BusinessActivity.CONFEITARIA -> R.drawable.tino_business_confeitaria
        BusinessActivity.RESTAURANTE -> R.drawable.tino_business_restaurante
        BusinessActivity.LANCHONETE -> R.drawable.tino_business_lanchonete
        BusinessActivity.SALAO_BELEZA -> R.drawable.tino_business_salao_beleza
        BusinessActivity.OFICINA -> R.drawable.tino_business_oficina
        BusinessActivity.OTHER -> R.drawable.tino_business_other
    }
}

@Composable
fun BusinessActivityIllustration(
    activity: BusinessActivity,
    modifier: Modifier = Modifier,
    contentDescription: String? = activity.displayName,
) {
    Image(
        painter = painterResource(BusinessActivityIllustrationResolver.resolve(activity)),
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = ContentScale.Fit,
    )
}
