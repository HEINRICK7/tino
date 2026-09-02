package com.tino.app.ui.illustration

import com.tino.app.R
import com.tino.app.domain.profile.BusinessActivity
import org.junit.Assert.assertEquals
import org.junit.Test

class TinoIllustrationContractTest {
    @Test
    fun catalogContainsOnlyStaticCommunicationStates() {
        assertEquals(
            setOf(
                TinoIllustrationState.LOADING,
                TinoIllustrationState.NOT_FOUND,
                TinoIllustrationState.SYNCING,
                TinoIllustrationState.OFFLINE,
                TinoIllustrationState.SUCCESS,
                TinoIllustrationState.WARNING,
                TinoIllustrationState.ERROR,
                TinoIllustrationState.SEARCHING,
                TinoIllustrationState.EXPLAINING,
                TinoIllustrationState.LEARNING,
                TinoIllustrationState.SLEEPING,
            ),
            TinoIllustrationState.entries.toSet(),
        )
    }

    @Test
    fun everyStaticStateResolvesToAnIndividualRuntimeAsset() {
        TinoIllustrationState.entries.forEach { state ->
            assertEquals(
                "Static state $state must have a drawable.",
                true,
                TinoIllustrationAssetResolver.resolve(state) != 0,
            )
        }
    }

    @Test
    fun businessActivitiesUseOneCentralResolver() {
        assertEquals(
            R.drawable.tino_business_mercadinho,
            BusinessActivityIllustrationResolver.resolve(BusinessActivity.MERCADINHO),
        )
        assertEquals(
            R.drawable.tino_business_acougue,
            BusinessActivityIllustrationResolver.resolve(BusinessActivity.ACOUGUE),
        )
        assertEquals(
            R.drawable.tino_business_verdureira,
            BusinessActivityIllustrationResolver.resolve(BusinessActivity.VERDUREIRA),
        )
        assertEquals(
            R.drawable.tino_business_other,
            BusinessActivityIllustrationResolver.resolve(BusinessActivity.OTHER),
        )
    }
}
