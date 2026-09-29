package com.allanime.tv.ui.recherche

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import com.allanime.tv.R
import com.allanime.tv.core.UiState
import com.allanime.tv.domain.model.Anime
import com.allanime.tv.domain.model.SortiePlanning
import com.allanime.tv.ui.components.AfficheAnime
import com.allanime.tv.ui.components.CarteSortie
import com.allanime.tv.ui.components.ContenuSelonEtat
import com.allanime.tv.ui.components.IndicateurChargement
import com.allanime.tv.ui.components.TitreSection
import com.allanime.tv.ui.theme.Coral
import com.allanime.tv.ui.theme.Focus
import com.allanime.tv.ui.theme.FocusHalo
import com.allanime.tv.ui.theme.InkBackground
import com.allanime.tv.ui.theme.InkElevated
import com.allanime.tv.ui.theme.InkSurface
import com.allanime.tv.ui.theme.LocalGabarit
import com.allanime.tv.ui.theme.MistGrey
import com.allanime.tv.ui.theme.PureWhite
import com.allanime.tv.ui.theme.haloFocus

/**
 * Recherche d'animes (section 7).
 *
 * Deux colonnes : la saisie a gauche, ce qu'elle produit a droite.
 *
 * ```
 *  ┌────────────────┬─────────────────────────────┐
 *  │  champ         │                             │
 *  ├────────────────┤  resultats                  │
 *  │  clavier       │  (grille d'affiches)        │
 *  │  (grille)      │                             │
 *  └────────────────┴─────────────────────────────┘
 * ```
 *
 * Le champ est pose **juste au-dessus** de la grille : c'est la que le regard revient
 * apres chaque touche, et l'y placer evite l'aller-retour vers le haut de l'ecran.
 * La fleche **droite** sort du clavier vers les resultats, la fleche **gauche** y
 * ramene — ces deux regles ne sont pas codees, elles decoulent de la geometrie,
 * Compose cherchant le voisin de focus dans la direction demandee.
 *
 * Tant que rien n'est saisi, la colonne de droite montre ce que le planning annonce
 * plutot qu'un ecran vide.
 *
 * @param margeDebut place laissee au rail de la barre laterale.
 */
@Composable
fun RechercheScreen(
    margeDebut: Dp,
    onOuvrirFiche: (Int) -> Unit,
    onOuvrirCatalogue: (String) -> Unit,
    viewModel: RechercheViewModel = viewModel(factory = RechercheViewModel.factory())
) {
    val requete by viewModel.requete.collectAsState()
    val etat by viewModel.etat.collectAsState()
    val suggestions by viewModel.suggestions.collectAsState()

    val gabarit = LocalGabarit.current
    val premiereTouche = remember { FocusRequester() }

    // Colonnes comptees sur la largeur reellement disponible, une fois le rail, le
    // clavier et les marges deduits.
    val largeurResultats = gabarit.largeurEcran - margeDebut - gabarit.margeDebut -
        gabarit.largeurClavier - gabarit.espacementSection - gabarit.margeFin
    val colonnes = gabarit.colonnes(largeurResultats, gabarit.largeurAfficheGrille * 0.85f)

    LaunchedEffect(Unit) {
        // Le clavier prend le focus a l'ouverture : on peut saisir immediatement,
        // sans avoir a chercher ou se trouve le curseur.
        runCatching { premiereTouche.requestFocus() }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(InkBackground)
            .padding(
                start = margeDebut + gabarit.margeDebut,
                end = gabarit.margeFin,
                top = gabarit.margeDebut
            ),
        horizontalArrangement = Arrangement.spacedBy(gabarit.espacementSection)
    ) {
        Column(
            modifier = Modifier.width(gabarit.largeurClavier),
            verticalArrangement = Arrangement.spacedBy(gabarit.espacementCarte * 0.9f)
        ) {
            ChampSaisie(
                requete = requete,
                onEffacerTout = viewModel::toutEffacer,
                modifier = Modifier.fillMaxWidth()
            )

            ClavierVirtuel(
                onCaractere = viewModel::ajouter,
                onEspace = { viewModel.ajouter(" ") },
                onEffacer = viewModel::effacer,
                premiereTouche = premiereTouche
            )
        }

        Box(modifier = Modifier.fillMaxSize()) {
            if (requete.isBlank()) {
                Suggestions(etat = suggestions, onOuvrirCatalogue = onOuvrirCatalogue)
            } else {
                Resultats(
                    etat = etat,
                    colonnes = colonnes,
                    onOuvrirFiche = onOuvrirFiche,
                    surReessayer = viewModel::relancer
                )
            }
        }
    }
}

/**
 * Champ de saisie : il **affiche** ce qui a ete tape, il ne le saisit pas.
 *
 * Ce n'est volontairement pas un `TextField` : un champ de texte focalise ouvre le
 * clavier du systeme, celui-la meme que [ClavierVirtuel] remplace. Il reste toutefois
 * focalisable — c'est lui que la fleche haut atteint depuis la premiere rangee de
 * touches — et la touche OK y efface la saisie, l'action la plus utile a cet endroit.
 *
 * Le curseur clignote meme quand le champ n'a pas le focus : il ne signale pas « vous
 * etes ici », il signale « ce que vous tapez arrive la ».
 */
@Composable
private fun ChampSaisie(
    requete: String,
    onEffacerTout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gabarit = LocalGabarit.current
    var focalise by remember { mutableStateOf(false) }
    val forme = RoundedCornerShape((12 * gabarit.facteur).dp)
    val vide = requete.isEmpty()

    val battement = rememberInfiniteTransition(label = "curseur")
    val opaciteCurseur by battement.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 520),
            repeatMode = RepeatMode.Reverse
        ),
        label = "curseur-opacite"
    )

    Surface(
        onClick = onEffacerTout,
        modifier = modifier
            .height(gabarit.hauteurChampSaisie)
            .haloFocus(focalise, forme)
            .onFocusChanged { focalise = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(forme),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = InkSurface,
            contentColor = PureWhite,
            focusedContainerColor = InkElevated,
            focusedContentColor = PureWhite
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(1.dp, MistGrey.copy(alpha = 0.35f)),
                shape = forme
            ),
            focusedBorder = Border(
                border = BorderStroke(Focus.BORDURE, FocusHalo),
                shape = forme
            )
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = (18 * gabarit.facteur).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                // Texte indicatif court : le champ est etroit, et une phrase
                // entiere y serait tronquee tout en repoussant le curseur hors
                // du cadre.
                text = requete.ifEmpty { stringResource(R.string.search_field_hint) },
                style = MaterialTheme.typography.titleMedium,
                color = if (vide) MistGrey else PureWhite,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )

            // Curseur : un trait fin colle a la fin de la saisie.
            Box(
                modifier = Modifier
                    .padding(start = (3 * gabarit.facteur).dp)
                    .alpha(opaciteCurseur)
                    .width((2 * gabarit.facteur).dp)
                    .height((24 * gabarit.facteur).dp)
                    .clip(RoundedCornerShape((1 * gabarit.facteur).dp))
                    .background(Coral)
            )
        }
    }
}

/** Grille des resultats, en affiches portrait. */
@Composable
private fun Resultats(
    etat: UiState<List<Anime>>,
    colonnes: Int,
    onOuvrirFiche: (Int) -> Unit,
    surReessayer: () -> Unit
) {
    val gabarit = LocalGabarit.current

    ContenuSelonEtat(
        etat = etat,
        modifier = Modifier.fillMaxSize(),
        surReessayer = surReessayer
    ) { resultats ->
        val lignes = resultats.chunked(colonnes)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = gabarit.margeFin),
            verticalArrangement = Arrangement.spacedBy(gabarit.espacementCarte * 0.9f)
        ) {
            items(lignes, key = { ligne -> ligne.first().id }) { ligne ->
                Row(
                    horizontalArrangement =
                        Arrangement.spacedBy(gabarit.espacementCarte * 0.9f)
                ) {
                    ligne.forEach { anime ->
                        AfficheAnime(
                            anime = anime,
                            onClick = { onOuvrirFiche(anime.id) },
                            largeur = gabarit.largeurAfficheGrille * 0.85f,
                            lignesTitre = 2
                        )
                    }
                }
            }
        }
    }
}

/**
 * Ce que l'ecran propose avant toute saisie.
 *
 * Deux rangees seulement, et aucune invention : ce que le planning annonce
 * aujourd'hui, puis ce qu'il annoncait les jours precedents. Quand le planning n'a
 * jamais pu etre lu, la zone renvoie simplement a la saisie — mieux vaut une consigne
 * qu'une rangee vide, qui ressemblerait a une panne.
 */
@Composable
private fun Suggestions(
    etat: EtatSuggestions,
    onOuvrirCatalogue: (String) -> Unit
) {
    val gabarit = LocalGabarit.current

    if (etat.enChargement) {
        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IndicateurChargement(taille = 22.dp)
            Text(
                text = stringResource(R.string.loading_planning),
                style = MaterialTheme.typography.bodyMedium,
                color = MistGrey,
                modifier = Modifier.padding(start = 12.dp)
            )
        }
        return
    }

    if (etat.estVide) {
        Text(
            text = stringResource(R.string.empty_search_hint),
            style = MaterialTheme.typography.bodyLarge,
            color = MistGrey,
            modifier = Modifier.padding(top = 8.dp)
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = gabarit.margeFin),
        verticalArrangement = Arrangement.spacedBy(gabarit.espacementSection)
    ) {
        if (etat.sortiesDuJour.isNotEmpty()) {
            item(key = "sorties-du-jour") {
                RangeeSuggestion(
                    titre = stringResource(
                        R.string.section_released_today,
                        etat.jour.libelle.lowercase()
                    ),
                    sorties = etat.sortiesDuJour,
                    onOuvrirCatalogue = onOuvrirCatalogue
                )
            }
        }

        if (etat.ajoutsRecents.isNotEmpty()) {
            item(key = "ajouts-recents") {
                RangeeSuggestion(
                    titre = stringResource(R.string.section_recent_additions),
                    sorties = etat.ajoutsRecents,
                    onOuvrirCatalogue = onOuvrirCatalogue
                )
            }
        }
    }
}

@Composable
private fun RangeeSuggestion(
    titre: String,
    sorties: List<SortiePlanning>,
    onOuvrirCatalogue: (String) -> Unit
) {
    val gabarit = LocalGabarit.current

    Column {
        TitreSection(texte = titre, modifier = Modifier.padding(bottom = 12.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(gabarit.espacementCarte * 0.9f),
            contentPadding = PaddingValues(
                top = (8 * gabarit.facteur).dp,
                bottom = (10 * gabarit.facteur).dp,
                end = gabarit.margeDebut
            )
        ) {
            items(sorties, key = { it.cle }) { sortie ->
                CarteSortie(
                    sortie = sortie,
                    onClick = { onOuvrirCatalogue(sortie.slug) },
                    largeur = gabarit.largeurAfficheGrille * 0.85f
                )
            }
        }
    }
}

