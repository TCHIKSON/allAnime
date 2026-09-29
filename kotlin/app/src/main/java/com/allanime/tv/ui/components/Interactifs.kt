package com.allanime.tv.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.allanime.tv.domain.model.Anime
import com.allanime.tv.domain.model.ProgressionAnime
import com.allanime.tv.ui.theme.Coral
import com.allanime.tv.ui.theme.Focus
import com.allanime.tv.ui.theme.FocusHalo
import com.allanime.tv.ui.theme.InkBackground
import com.allanime.tv.ui.theme.InkElevated
import com.allanime.tv.ui.theme.InkSurface
import com.allanime.tv.ui.theme.MistGrey
import com.allanime.tv.ui.theme.PureWhite
import com.allanime.tv.ui.theme.haloFocus

/**
 * Bouton principal de l'application.
 *
 * Le focus est toujours visible (bordure et remplissage corail) : c'est la condition
 * pour rester utilisable a la telecommande (section 2).
 */
@Composable
fun BoutonTv(
    texte: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    actif: Boolean = true,
    principal: Boolean = true
) {
    Button(
        onClick = onClick,
        enabled = actif,
        modifier = modifier,
        colors = if (principal) {
            ButtonDefaults.colors(
                containerColor = InkElevated,
                contentColor = PureWhite,
                focusedContainerColor = Coral,
                focusedContentColor = PureWhite
            )
        } else {
            ButtonDefaults.colors(
                containerColor = InkSurface,
                contentColor = MistGrey,
                focusedContainerColor = Coral,
                focusedContentColor = PureWhite
            )
        }
    ) {
        Text(
            text = texte,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Surface cliquable generique : toutes les cartes de l'application en heritent, ce
 * qui garantit un comportement de focus identique partout.
 */
@Composable
fun CarteFocalisable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    surFocus: (Boolean) -> Unit = {},
    coins: Int = 12,
    echelle: Float = Focus.ECHELLE_CARTE,
    contenu: @Composable () -> Unit
) {
    val forme = RoundedCornerShape(coins.dp)
    var focalisee by remember { mutableStateOf(false) }

    Surface(
        onClick = onClick,
        modifier = modifier
            .haloFocus(focalisee, forme)
            .onFocusChanged {
                focalisee = it.isFocused
                surFocus(it.isFocused)
            },
        shape = ClickableSurfaceDefaults.shape(forme),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = InkSurface,
            contentColor = PureWhite,
            focusedContainerColor = InkElevated,
            focusedContentColor = PureWhite
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = echelle),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(Focus.BORDURE, FocusHalo),
                shape = forme
            )
        )
    ) {
        contenu()
    }
}

/**
 * Affiche d'anime : jaquette au format portrait et titre sous la carte.
 * Le titre n'apparait en entier que lorsque la carte a le focus.
 */
@Composable
fun AfficheAnime(
    anime: Anime,
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
            modifier = Modifier.fillMaxWidth().aspectRatio(POSTER_RATIO),
            echelle = Focus.ECHELLE_AFFICHE,
            surFocus = {
                focalisee = it
                surFocus(it)
            }
        ) {
            ImageDistante(
                url = anime.bestCover,
                contentDescription = anime.displayTitle,
                modifier = Modifier.fillMaxSize()
            )
        }

        Text(
            text = anime.displayTitle,
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
 * Carte de la rangee « Reprendre votre visionnage » : jaquette, barre d'avancement
 * et reperage saison / episode.
 */
@Composable
fun CarteReprise(
    progression: ProgressionAnime,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    largeur: Dp = 150.dp,
    surFocus: (Boolean) -> Unit = {}
) {
    Column(modifier = modifier.width(largeur)) {
        CarteFocalisable(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(POSTER_RATIO),
            echelle = Focus.ECHELLE_AFFICHE,
            surFocus = surFocus
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                ImageDistante(
                    url = progression.image,
                    contentDescription = progression.titre,
                    modifier = Modifier.fillMaxSize()
                )

                // Barre d'avancement posee au bas de la jaquette.
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(5.dp)
                        .background(InkBackground.copy(alpha = 0.75f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(progression.avancement.coerceAtLeast(0.02f))
                            .background(Coral)
                    )
                }
            }
        }

        Text(
            text = progression.titre,
            style = MaterialTheme.typography.bodyMedium,
            color = PureWhite,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            // Ecarte du bas de l'affiche : l'agrandissement au focus est
            // graphique et deborde de la place reservee par la mise en page.
            modifier = Modifier.padding(top = ESPACE_SOUS_AFFICHE.dp)
        )
        Text(
            text = progression.reperage,
            style = MaterialTheme.typography.labelMedium,
            color = MistGrey,
            maxLines = 1
        )
    }
}

/**
 * Vignette generique (personnage, staff, recommandation) : image carree ou
 * portrait, legende en dessous.
 */
@Composable
fun VignetteLegendee(
    titre: String,
    sousTitre: String?,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    largeur: Dp = 140.dp,
    ratio: Float = POSTER_RATIO
) {
    Column(modifier = modifier.width(largeur)) {
        CarteFocalisable(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().aspectRatio(ratio)
        ) {
            ImageDistante(
                url = imageUrl,
                contentDescription = titre,
                modifier = Modifier.fillMaxSize()
            )
        }
        Text(
            text = titre,
            style = MaterialTheme.typography.bodySmall,
            color = PureWhite,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
        )
        if (!sousTitre.isNullOrBlank()) {
            Text(
                text = sousTitre,
                style = MaterialTheme.typography.labelSmall,
                color = MistGrey,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Repli sans vignette : le numero occupe la carte, avec un liseré de marque. */
@Composable
private fun PastilleNumero(libelle: String, focalisee: Boolean) {
    Box(
        modifier = Modifier.fillMaxSize().background(if (focalisee) InkElevated else InkSurface),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(4.dp)
                .background(Coral)
        )
        Text(
            text = libelle,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = if (focalisee) PureWhite else MistGrey,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 14.dp)
        )
    }
}

/**
 * Bouton de selection : saison, langue, plage d'episodes, lecteur.
 *
 * **Selection et focus ne doivent pas se confondre.** Le corail dit « c'est ce qui
 * est choisi », la bordure blanche dit « c'est ce que je survole ». Remplir de
 * corail l'element survole — ce que faisait la version precedente — donnait deux
 * pastilles identiques des qu'on en survolait une autre que la sienne : impossible
 * de savoir laquelle etait active.
 */
@Composable
fun PuceSelection(
    texte: String,
    selectionnee: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val forme = RoundedCornerShape(50)
    val fond = if (selectionnee) Coral else InkSurface
    val encre = if (selectionnee) PureWhite else MistGrey

    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(forme),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = fond,
            contentColor = encre,
            // Le survol eclaircit la pastille non selectionnee sans lui donner la
            // couleur de la selection.
            focusedContainerColor = if (selectionnee) Coral else InkElevated,
            focusedContentColor = PureWhite
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(Focus.BORDURE, FocusHalo),
                shape = forme
            )
        )
    ) {
        Box(modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)) {
            Text(
                text = texte,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Rapport largeur/hauteur des jaquettes AniList. */
private const val POSTER_RATIO = 2f / 3f

/** Largeur de la pastille quand l'episode n'est pas illustre : la ligne s'abaisse. */

/** Rapport des vignettes d'episodes : le 16:9 des captures fournies par AniList. */
private const val RATIO_VIGNETTE = 16f / 9f

/**
 * Ecart entre une affiche et son titre.
 *
 * Il tient compte de l'agrandissement au focus : une affiche de 220 dp
 * grandie de 12 % deborde d'une quinzaine de points vers le bas.
 */
private const val ESPACE_SOUS_AFFICHE = 16
