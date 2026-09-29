package com.allanime.tv.ui.lecteur

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.allanime.tv.R
import com.allanime.tv.domain.model.InventaireQualites
import com.allanime.tv.domain.model.Lecteur
import com.allanime.tv.domain.model.QualiteVideo
import com.allanime.tv.ui.theme.Coral
import com.allanime.tv.ui.theme.InkBackground
import com.allanime.tv.ui.theme.InkElevated
import com.allanime.tv.ui.theme.MistGrey
import com.allanime.tv.ui.theme.PureWhite

/**
 * Panneau des reglages du lecteur, ouvert par l'engrenage de la barre de lecture.
 *
 * Il regroupe les deux seuls choix qui concernent la lecture en cours — la source et
 * la resolution — dans un panneau lateral, ce qui degage la barre de commandes. Les
 * lecteurs y sont designes par leur rang dans le catalogue et le nom de leur
 * plateforme ; l'URL n'apparait jamais.
 */
@Composable
fun MenuReglages(
    lecteurs: List<Lecteur>,
    indexLecteur: Int,
    qualites: InventaireQualites,
    surChoisirLecteur: (Int) -> Unit,
    surChoisirQualite: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val premiereLigne = remember { FocusRequester() }
    // Propriete calculee : on la fige pour la composition, sinon chaque lecture
    // reconstruirait la liste et l'identite des entrees changerait.
    val entreesQualite = remember(qualites) { qualites.entreesTriees }

    LaunchedEffect(Unit) {
        runCatching { premiereLigne.requestFocus() }
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Voile : l'image reste visible, mais le panneau prend clairement le dessus.
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    listOf(Color.Transparent, InkBackground.copy(alpha = 0.92f))
                )
            )
        )

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(LARGEUR_PANNEAU.dp)
                .fillMaxHeight()
                .background(InkBackground.copy(alpha = 0.97f))
                .padding(horizontal = 28.dp, vertical = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Black,
                color = PureWhite
            )

            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(top = 18.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item { SousTitre(stringResource(R.string.settings_quality)) }

                if (qualites.estFigee) {
                    item {
                        Information(
                            texte = stringResource(R.string.quality_locked),
                            detail = qualites.hauteurCourante
                                .takeIf { it > 0 }
                                ?.let { stringResource(R.string.quality_displayed, it) }
                        )
                    }
                } else {
                    itemsIndexed(entreesQualite, key = { _, qualite -> qualite.id }) { index, qualite ->
                        LigneReglage(
                            titre = libelleQualite(qualite),
                            detail = detailQualite(qualite, qualites),
                            selectionnee = qualite.id == qualites.actif,
                            onClick = { surChoisirQualite(qualite.id) },
                            modifier = if (index == 0) {
                                Modifier.focusRequester(premiereLigne)
                            } else {
                                Modifier
                            }
                        )
                    }
                }

                item {
                    SousTitre(
                        texte = stringResource(R.string.settings_players),
                        modifier = Modifier.padding(top = 14.dp)
                    )
                }

                itemsIndexed(lecteurs, key = { _, lecteur -> lecteur.url }) { index, lecteur ->
                    LigneReglage(
                        titre = libelleLecteur(lecteur),
                        detail = null,
                        selectionnee = index == indexLecteur,
                        onClick = { surChoisirLecteur(index) },
                        modifier = if (qualites.estFigee && index == 0) {
                            Modifier.focusRequester(premiereLigne)
                        } else {
                            Modifier
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SousTitre(texte: String, modifier: Modifier = Modifier) {
    Text(
        text = texte.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = Coral,
        modifier = modifier.padding(bottom = 4.dp)
    )
}

/** Ligne selectionnable du panneau : libelle a gauche, coche a droite si active. */
@Composable
private fun LigneReglage(
    titre: String,
    detail: String?,
    selectionnee: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val forme = RoundedCornerShape(10.dp)

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = ClickableSurfaceDefaults.shape(forme),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selectionnee) InkElevated else Color.Transparent,
            contentColor = if (selectionnee) PureWhite else MistGrey,
            focusedContainerColor = Coral,
            focusedContentColor = PureWhite
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(border = BorderStroke(2.dp, PureWhite), shape = forme)
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = titre,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (!detail.isNullOrBlank()) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (selectionnee) {
                IconeCoche(couleur = PureWhite, modifier = Modifier.padding(start = 10.dp))
            }
        }
    }
}

/** Message d'information, pour les lecteurs qui n'exposent aucune resolution. */
@Composable
private fun Information(texte: String, detail: String?) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(InkElevated, RoundedCornerShape(10.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = texte,
            style = MaterialTheme.typography.bodySmall,
            color = MistGrey
        )
        if (!detail.isNullOrBlank()) {
            Text(
                text = detail,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = PureWhite
            )
        }
    }
}

@Composable
private fun libelleLecteur(lecteur: Lecteur): String = if (lecteur.hostLabel.isBlank()) {
    stringResource(R.string.player_index, lecteur.index)
} else {
    stringResource(R.string.player_index_with_host, lecteur.index, lecteur.hostLabel)
}

@Composable
private fun libelleQualite(qualite: QualiteVideo): String = when {
    qualite.estAutomatique -> stringResource(R.string.quality_auto)
    qualite.hauteur > 0 -> stringResource(R.string.quality_height, qualite.hauteur)
    else -> qualite.libelle ?: stringResource(R.string.quality_auto)
}

/**
 * L'entree automatique porte la resolution reellement affichee : c'est la seule
 * facon de savoir ce que l'on regarde quand le lecteur decide lui-meme.
 *
 * Le detail disparait des qu'une resolution est choisie a la main : la hauteur
 * mesuree ne se rafraichit qu'au prochain evenement de la video, et l'afficher sur
 * une entree inactive laisserait croire a autre chose que ce qui est diffuse.
 */
@Composable
private fun detailQualite(qualite: QualiteVideo, inventaire: InventaireQualites): String? {
    if (!qualite.estAutomatique || inventaire.actif != qualite.id) return null
    val hauteur = inventaire.hauteurCourante.takeIf { it > 0 } ?: return null
    return stringResource(R.string.quality_displayed, hauteur)
}

/** Largeur du panneau : assez pour les libelles, assez peu pour laisser voir l'image. */
private const val LARGEUR_PANNEAU = 400
