package com.allanime.tv.ui.planning

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allanime.tv.R
import com.allanime.tv.core.AppError
import com.allanime.tv.core.UiState
import com.allanime.tv.core.runCatchingCancellable
import com.allanime.tv.core.toAppError
import com.allanime.tv.data.planning.PlanningRepository
import com.allanime.tv.di.ServiceLocator
import com.allanime.tv.di.viewModelFactoryFor
import com.allanime.tv.domain.model.JourSemaine
import com.allanime.tv.domain.model.Planning
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Planning complet de la semaine.
 *
 * L'ecran lit la copie conservee sur le televiseur et declenche une verification a
 * l'ouverture, comme l'accueil : le planning s'affiche donc instantanement, puis se
 * corrige tout seul si le site a publie la semaine suivante.
 */
class PlanningViewModel(
    private val repository: PlanningRepository
) : ViewModel() {

    private val verification = MutableStateFlow(true)
    private val echec = MutableStateFlow<AppError?>(null)

    /** Jour a mettre en evidence dans la liste. */
    val jourCourant: JourSemaine = JourSemaine.courant()

    val etat: StateFlow<UiState<Planning>> =
        combine(repository.planning, verification, echec) { brut, enCours, erreur ->
            // Les scans partagent le planning du site ; l'application ne les lit pas.
            val planning = brut?.animesSeulement()

            when {
                // Une copie locale, meme d'avant la derniere publication, vaut mieux
                // qu'un ecran vide : elle est juste remplacee des que le site repond.
                planning != null && !planning.estVide -> UiState.Success(planning)
                enCours -> UiState.Loading(R.string.loading_planning)
                erreur != null -> UiState.Failure(erreur)
                else -> UiState.Empty(R.string.empty_planning)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState.Loading(R.string.loading_planning)
        )

    init {
        charger()
    }

    fun charger() {
        viewModelScope.launch {
            verification.value = true
            echec.value = null

            runCatchingCancellable { repository.synchroniser() }
                .onFailure { echec.value = it.toAppError() }

            verification.value = false
        }
    }

    companion object {
        fun factory() = viewModelFactoryFor {
            PlanningViewModel(ServiceLocator.planningRepository)
        }
    }
}
