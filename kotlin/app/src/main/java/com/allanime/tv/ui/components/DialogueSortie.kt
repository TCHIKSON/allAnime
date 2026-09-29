package com.allanime.tv.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.allanime.tv.R
import com.allanime.tv.ui.theme.InkBackground
import com.allanime.tv.ui.theme.InkOverlay
import com.allanime.tv.ui.theme.MistGrey
import com.allanime.tv.ui.theme.PureWhite

/**
 * Confirmation avant de quitter l'application.
 *
 * Elle est posee en surimpression plutot que dans une `Dialog` : sur un televiseur,
 * une fenetre separee capture le focus dans une autre hierarchie et la telecommande
 * s'y perd. Ici tout reste dans le meme arbre, et le voile opaque suffit a isoler la
 * question.
 *
 * **Le focus part sur « Non ».** Quitter par megarde est la seule erreur qu'on ne
 * peut pas annuler : c'est donc la reponse sure qui doit etre a portee de la touche
 * OK. La touche Retour, elle, revient a repondre « Non ».
 */
@Composable
fun DialogueSortie(onConfirmer: () -> Unit, onAnnuler: () -> Unit) {
    val refus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { refus.requestFocus() } }

    BackHandler { onAnnuler() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(InkBackground.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(LARGEUR_BOITE.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(InkOverlay)
                .padding(horizontal = 40.dp, vertical = 34.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.app_logo_exite),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.height(HAUTEUR_LOGO.dp)
            )

            Text(
                text = stringResource(R.string.exit_title),
                style = MaterialTheme.typography.headlineSmall,
                color = PureWhite,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 20.dp)
            )

            Text(
                text = stringResource(R.string.exit_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MistGrey,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp)
            )

            Row(
                modifier = Modifier.padding(top = 26.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                BoutonTv(
                    texte = stringResource(R.string.action_no),
                    onClick = onAnnuler,
                    modifier = Modifier.focusRequester(refus)
                )
                BoutonTv(
                    texte = stringResource(R.string.action_yes),
                    onClick = onConfirmer,
                    principal = false
                )
            }
        }
    }
}

/** Assez large pour que la question tienne sur une ligne, pas plus. */
private const val LARGEUR_BOITE = 460

private const val HAUTEUR_LOGO = 96
