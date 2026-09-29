package com.allanime.tv.ui.planning

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.allanime.tv.R
import com.allanime.tv.domain.model.JourPlanning
import com.allanime.tv.domain.model.SortiePlanning
import com.allanime.tv.ui.components.CarteSortie
import com.allanime.tv.ui.components.ContenuSelonEtat
import com.allanime.tv.ui.components.TitreSection
import com.allanime.tv.ui.theme.Coral
import com.allanime.tv.ui.theme.InkBackground
import com.allanime.tv.ui.theme.LocalGabarit
import com.allanime.tv.ui.theme.MistGrey

/**
 * Planning complet des sorties de la semaine.
 *
 * Le site le presente en sept colonnes cote a cote ; sur un televiseur, sept rangees
 * empilees se parcourent mieux a la telecommande : on descend de jour en jour et on
 * se deplace a l'horizontale dans la journee. Le jour en cours est mis en evidence,
 * comme sur le site.
 */
@Composable
fun PlanningScreen(
    margeDebut: Dp,
    onOuvrirCatalogue: (String) -> Unit,
    viewModel: PlanningViewModel = viewModel(factory = PlanningViewModel.factory())
) {
    val gabarit = LocalGabarit.current
    val etat by viewModel.etat.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(InkBackground)) {
        ContenuSelonEtat(
            etat = etat,
            modifier = Modifier.fillMaxSize(),
            surReessayer = viewModel::charger
        ) { planning ->
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = gabarit.margeDebut,
                    bottom = gabarit.margeFin
                ),
                verticalArrangement = Arrangement.spacedBy(gabarit.espacementCarte)
            ) {
                item(key = "titre") {
                    Text(
                        text = stringResource(R.string.planning_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black,
                        color = Coral,
                        modifier = Modifier.padding(start = margeDebut + gabarit.margeDebut, bottom = 4.dp)
                    )
                }

                items(planning.jours, key = { it.jour.name }) { jour ->
                    Journee(
                        jour = jour,
                        estAujourdhui = jour.jour == viewModel.jourCourant,
                        margeDebut = margeDebut,
                        onOuvrirCatalogue = onOuvrirCatalogue
                    )
                }

                if (planning.sansJourFixe.isNotEmpty()) {
                    item(key = "sans-jour-fixe") {
                        Rangee(
                            titre = stringResource(R.string.planning_no_fixed_day),
                            sorties = planning.sansJourFixe,
                            margeDebut = margeDebut,
                            onOuvrirCatalogue = onOuvrirCatalogue
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Journee(
    jour: JourPlanning,
    estAujourdhui: Boolean,
    margeDebut: Dp,
    onOuvrirCatalogue: (String) -> Unit
) {
    val gabarit = LocalGabarit.current

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = margeDebut + gabarit.margeDebut, bottom = 10.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = jour.jour.libelle,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                // Le jour en cours ressort, comme sur le site.
                color = if (estAujourdhui) Coral else MistGrey
            )
            if (jour.date.isNotBlank()) {
                Text(
                    text = jour.date,
                    style = MaterialTheme.typography.titleMedium,
                    color = MistGrey
                )
            }
        }

        if (jour.sorties.isEmpty()) {
            Text(
                text = stringResource(R.string.empty_day_releases),
                style = MaterialTheme.typography.bodyMedium,
                color = MistGrey,
                modifier = Modifier.padding(start = margeDebut + gabarit.margeDebut, bottom = 8.dp)
            )
            return@Column
        }

        Cartes(
            sorties = jour.sorties,
            margeDebut = margeDebut,
            onOuvrirCatalogue = onOuvrirCatalogue
        )
    }
}

@Composable
private fun Rangee(
    titre: String,
    sorties: List<SortiePlanning>,
    margeDebut: Dp,
    onOuvrirCatalogue: (String) -> Unit
) {
    val gabarit = LocalGabarit.current

    Column(modifier = Modifier.fillMaxWidth()) {
        TitreSection(
            texte = titre,
            modifier = Modifier.padding(start = margeDebut + gabarit.margeDebut, bottom = 10.dp)
        )
        Cartes(
            sorties = sorties,
            margeDebut = margeDebut,
            onOuvrirCatalogue = onOuvrirCatalogue
        )
    }
}

@Composable
private fun Cartes(
    sorties: List<SortiePlanning>,
    margeDebut: Dp,
    onOuvrirCatalogue: (String) -> Unit
) {
    val gabarit = LocalGabarit.current

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(gabarit.espacementCarte),
        contentPadding = PaddingValues(
            start = margeDebut + gabarit.margeDebut,
            end = gabarit.margeFin,
            top = (10 * gabarit.facteur).dp,
            bottom = (12 * gabarit.facteur).dp
        )
    ) {
        items(sorties, key = { it.cle }) { sortie ->
            CarteSortie(
                sortie = sortie,
                onClick = { onOuvrirCatalogue(sortie.slug) },
                largeur = gabarit.largeurAffiche
            )
        }
    }
}

