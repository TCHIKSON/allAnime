package com.allanime.tv.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.allanime.tv.R
import com.allanime.tv.ui.theme.InkBackground
import com.allanime.tv.ui.theme.LocalGabarit

/**
 * Ecran de demarrage : l'illustration `app_logo_run`, plein cadre, le temps que
 * l'accueil charge le catalogue.
 *
 * Il prolonge le fond de fenetre du theme de demarrage, qui montre la meme image
 * avant la premiere composition : l'utilisateur ne voit donc qu'un seul ecran, de
 * l'appui sur l'icone jusqu'a l'accueil. L'indicateur de chargement est ce qui
 * distingue les deux — il dit que l'application travaille, et non qu'elle est figee.
 */
@Composable
fun EcranDemarrage(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().background(InkBackground)) {
        Image(
            painter = painterResource(R.drawable.app_logo_run),
            contentDescription = stringResource(R.string.cd_logo),
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize()
        )

        IndicateurChargement(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = (48 * LocalGabarit.current.facteur).dp)
        )
    }
}
