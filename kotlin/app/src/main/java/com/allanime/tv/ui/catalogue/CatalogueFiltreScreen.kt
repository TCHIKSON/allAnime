package com.allanime.tv.ui.catalogue

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.allanime.tv.R
import com.allanime.tv.core.dataOrNull
import com.allanime.tv.ui.components.AfficheCatalogue
import com.allanime.tv.ui.components.ContenuSelonEtat
import com.allanime.tv.ui.theme.InkBackground
import com.allanime.tv.ui.theme.LocalGabarit
import com.allanime.tv.ui.theme.MistGrey
import com.allanime.tv.ui.theme.PureWhite

/**
 * Catalogue restreint aux series ou aux films, en grille d'affiches.
 *
 * Une grille et non des rangees thematiques : l'utilisateur qui ouvre « Films » sait
 * deja ce qu'il cherche, il veut en balayer le plus possible d'un coup d'oeil. Les
 * rangees, elles, restent a l'accueil, ou la question est « que regarder ».
 *
 * @param margeDebut place laissee au rail de la barre laterale.
 */
@Composable
fun CatalogueFiltreScreen(
    filtre: FiltreCatalogue,
    margeDebut: Dp,
    onOuvrirCatalogue: (String) -> Unit,
    viewModel: CatalogueFiltreViewModel = viewModel(
        key = "catalogue-${filtre.name}",
        factory = CatalogueFiltreViewModel.factory(filtre)
    )
) {
    val gabarit = LocalGabarit.current
    val etat by viewModel.etat.collectAsState()
    val nombre = etat.dataOrNull?.size ?: 0

    // Le nombre de colonnes est **compte**, pas fige : il depend de ce qui reste
    // apres le rail et les marges, et une dalle au rapport inhabituel n'a pas la
    // largeur que le facteur d'echelle suppose.
    val largeurUtile = gabarit.largeurEcran - margeDebut - gabarit.margeDebut - gabarit.margeFin
    val colonnes = gabarit.colonnes(largeurUtile, gabarit.largeurAfficheGrille)

    Column(modifier = Modifier.fillMaxSize().background(InkBackground)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = margeDebut + gabarit.margeDebut,
                    end = gabarit.margeFin,
                    top = gabarit.margeDebut,
                    bottom = gabarit.espacementCarte
                ),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(gabarit.espacementCarte * 0.7f)
        ) {
            Text(
                text = stringResource(filtre.titre),
                style = MaterialTheme.typography.headlineLarge,
                color = PureWhite
            )

            if (nombre > 0) {
                Text(
                    text = pluralStringResource(R.plurals.label_catalogue_count, nombre, nombre),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MistGrey,
                    modifier = Modifier.padding(bottom = (4 * gabarit.facteur).dp)
                )
            }
        }

        ContenuSelonEtat(
            etat = etat,
            modifier = Modifier.fillMaxSize(),
            surReessayer = { viewModel.charger(rafraichir = true) }
        ) { animes ->
            val lignes = animes.chunked(colonnes)

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = margeDebut + gabarit.margeDebut,
                    end = gabarit.margeFin,
                    bottom = gabarit.margeFin
                ),
                verticalArrangement = Arrangement.spacedBy(gabarit.espacementSection)
            ) {
                items(lignes, key = { ligne -> ligne.first().slug }) { ligne ->
                    Row(horizontalArrangement = Arrangement.spacedBy(gabarit.espacementCarte)) {
                        ligne.forEach { anime ->
                            AfficheCatalogue(
                                anime = anime,
                                onClick = { onOuvrirCatalogue(anime.slug) },
                                largeur = gabarit.largeurAfficheGrille,
                                lignesTitre = 2
                            )
                        }
                    }
                }
            }
        }
    }
}

