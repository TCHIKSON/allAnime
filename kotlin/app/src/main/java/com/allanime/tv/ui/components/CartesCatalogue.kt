package com.allanime.tv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.allanime.tv.R
import com.allanime.tv.domain.model.AnimeCatalogue
import com.allanime.tv.domain.model.SortiePlanning
import com.allanime.tv.ui.theme.Coral
import com.allanime.tv.ui.theme.Focus
import com.allanime.tv.ui.theme.InkBackground
import com.allanime.tv.ui.theme.MistGrey
import com.allanime.tv.ui.theme.PureWhite

/**
 * Cartes alimentees par le site : liste du catalogue et planning des sorties.
 *
 * Elles restent distinctes de celles d'AniList (`AfficheAnime`) parce que les deux
 * sources n'ont pas les memes champs : le catalogue n'a ni note ni banniere, mais il
 * a l'heure de diffusion et la langue, que la fiche AniList ignore.
 */

/** Affiche du catalogue : jaquette au format portrait et titre sous la carte. */
@Composable
fun AfficheCatalogue(
    anime: AnimeCatalogue,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    largeur: Dp = 180.dp,
    lignesTitre: Int = 2,
    surFocus: (Boolean) -> Unit = {}
) {
    var focalisee by remember { mutableStateOf(false) }

    Column(modifier = modifier.width(largeur)) {
        CarteFocalisable(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(RATIO_AFFICHE),
            echelle = Focus.ECHELLE_AFFICHE,
            surFocus = {
                focalisee = it
                surFocus(it)
            }
        ) {
            ImageDistante(
                url = anime.image,
                contentDescription = anime.titre,
                modifier = Modifier.fillMaxSize()
            )
        }

        Text(
            text = anime.titre,
            style = MaterialTheme.typography.bodyMedium,
            color = if (focalisee) PureWhite else MistGrey,
            maxLines = lignesTitre,
            overflow = TextOverflow.Ellipsis,
            // Ecarte du bas de l'affiche : l'agrandissement au focus est
            // graphique et deborde de la place reservee par la mise en page.
            modifier = Modifier.padding(top = ESPACE_SOUS_AFFICHE.dp)
        )
    }
}

/**
 * Carte du planning : jaquette, bandeau d'horaire et precision de saison.
 *
 * L'horaire est l'information que l'on vient chercher ici, donc il est pose sur la
 * jaquette plutot que relegue sous le titre. Trois cas, tous explicites : l'heure
 * annoncee, « Reporté » quand le site le signale, et « Heure non précisée » quand il
 * affiche `?` — un episode sans horaire arrete n'est pas un episode a minuit.
 */
@Composable
fun CarteSortie(
    sortie: SortiePlanning,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    largeur: Dp = 150.dp,
    surFocus: (Boolean) -> Unit = {}
) {
    var focalisee by remember { mutableStateOf(false) }

    Column(modifier = modifier.width(largeur)) {
        CarteFocalisable(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(RATIO_AFFICHE),
            echelle = Focus.ECHELLE_AFFICHE,
            surFocus = {
                focalisee = it
                surFocus(it)
            }
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                ImageDistante(
                    url = sortie.image,
                    contentDescription = sortie.titre,
                    modifier = Modifier.fillMaxSize()
                )

                sortie.langue?.let { langue ->
                    Etiquette(
                        texte = langue,
                        fond = InkBackground.copy(alpha = 0.82f),
                        couleurTexte = MistGrey,
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                    )
                }

                Etiquette(
                    texte = horaire(sortie),
                    fond = if (sortie.reporte) Coral else InkBackground.copy(alpha = 0.82f),
                    couleurTexte = PureWhite,
                    modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)
                )
            }
        }

        Text(
            text = sortie.titre,
            style = MaterialTheme.typography.bodyMedium,
            color = if (focalisee) PureWhite else MistGrey,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Ecarte du bas de l'affiche : l'agrandissement au focus est
            // graphique et deborde de la place reservee par la mise en page.
            modifier = Modifier.padding(top = ESPACE_SOUS_AFFICHE.dp)
        )

        Text(
            text = sortie.precision ?: sortie.type,
            style = MaterialTheme.typography.labelMedium,
            color = MistGrey,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Ce que la carte annonce a la place de l'heure quand celle-ci manque ou ne vaut plus. */
@Composable
private fun horaire(sortie: SortiePlanning): String = when {
    sortie.reporte -> stringResource(R.string.planning_postponed)
    sortie.heure != null -> sortie.heure
    else -> stringResource(R.string.planning_time_unknown)
}

@Composable
private fun Etiquette(
    texte: String,
    fond: androidx.compose.ui.graphics.Color,
    couleurTexte: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(fond)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = texte,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = couleurTexte,
            maxLines = 1
        )
    }
}

/** Affiche 2:3, comme partout ailleurs dans l'application. */
private const val RATIO_AFFICHE = 2f / 3f

/**
 * Ecart entre une affiche et son titre.
 *
 * Il tient compte de l'agrandissement au focus : une affiche de 220 dp
 * grandie de 12 % deborde d'une quinzaine de points vers le bas.
 */
private const val ESPACE_SOUS_AFFICHE = 16
