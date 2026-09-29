package com.allanime.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.allanime.tv.ui.theme.Coral
import com.allanime.tv.ui.theme.Focus
import com.allanime.tv.ui.theme.InkBackground
import com.allanime.tv.ui.theme.MistGrey
import com.allanime.tv.ui.theme.PureWhite

/**
 * Vignette d'episode au format paysage.
 *
 * Elle porte quatre informations, dans cet ordre de lecture :
 *
 *  1. **l'image de l'episode** — a defaut, la jaquette de la serie, pour qu'aucune
 *     carte ne soit un rectangle gris ;
 *  2. **son etat** : floutee tant que l'episode n'a pas ete commence. Ce n'est pas
 *     un effet decoratif — c'est ce qui evite qu'une vignette raconte la scene d'un
 *     episode qu'on n'a pas encore vu ;
 *  3. **l'avancement**, en trait fin au bas de l'image, quand la lecture est
 *     commencee ;
 *  4. **une pastille** : le temps restant si l'episode est en cours, « Nouveau » s'il
 *     vient apres le dernier episode regarde.
 *
 * @param avancement entre 0 et 1, ou `null` quand on ne sait pas ou l'on en est.
 * @param flou `true` tant que l'episode n'a pas ete ouvert. Volontairement separe de
 *   [avancement] : un episode qu'on vient de lancer a une position de zero seconde,
 *   et le deduire de l'avancement le laisserait flou precisement au moment ou l'on
 *   est en train de le regarder.
 * @param pastille texte affiche en haut a droite, ou `null`.
 */
@Composable
fun CarteEpisodePaysage(
    numero: String,
    titre: String?,
    detail: String,
    imageUrl: String?,
    avancement: Float?,
    flou: Boolean,
    pastille: String?,
    enCours: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    largeur: Dp = LARGEUR
) {
    var focalisee by remember { mutableStateOf(false) }

    Column(modifier = modifier.width(largeur)) {
        CarteFocalisable(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(RATIO),
            echelle = Focus.ECHELLE_CARTE,
            surFocus = { focalisee = it }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                ImageDistante(
                    url = imageUrl,
                    contentDescription = titre ?: numero,
                    modifier = Modifier.fillMaxSize(),
                    coins = 0.dp,
                    // Jamais ouvert : on montre qu'il y a une image, pas laquelle.
                    flou = flou
                )

                // Voile bas : il garantit la lisibilite de la barre d'avancement et
                // du numero quelle que soit la scene derriere.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(HAUTEUR_VOILE.dp)
                        .align(Alignment.BottomCenter)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, InkBackground.copy(alpha = 0.85f))
                            )
                        )
                )

                pastille?.let { texte ->
                    Text(
                        text = texte,
                        style = MaterialTheme.typography.labelSmall,
                        color = PureWhite,
                        maxLines = 1,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (enCours) Coral else InkBackground.copy(alpha = 0.82f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }

                // Trait d'avancement, colle au bas de la vignette.
                avancement?.let { fraction ->
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(EPAISSEUR_BARRE.dp)
                            .background(PureWhite.copy(alpha = 0.25f))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(fraction.coerceIn(0f, 1f))
                                .fillMaxSize()
                                .background(Coral)
                        )
                    }
                }
            }
        }

        Text(
            text = numero,
            style = MaterialTheme.typography.labelMedium,
            color = if (focalisee) PureWhite else MistGrey,
            maxLines = 1,
            modifier = Modifier.padding(top = 10.dp)
        )

        titre?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.titleSmall,
                color = if (focalisee) PureWhite else MistGrey,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }

        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MistGrey,
            maxLines = 1,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

/** Format 16:9, celui des captures d'ecran que les sources fournissent. */
private const val RATIO = 16f / 9f

/** Repli : quatre vignettes visibles a la fois sur l'ecran de reference. */
private val LARGEUR = 260.dp

private const val HAUTEUR_VOILE = 48

private const val EPAISSEUR_BARRE = 4
