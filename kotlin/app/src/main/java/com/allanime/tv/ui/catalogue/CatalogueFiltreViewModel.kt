package com.allanime.tv.ui.catalogue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.allanime.tv.R
import com.allanime.tv.core.UiState
import com.allanime.tv.core.runCatchingCancellable
import com.allanime.tv.core.toAppError
import com.allanime.tv.data.repository.CatalogueListeRepository
import com.allanime.tv.di.ServiceLocator
import com.allanime.tv.di.viewModelFactoryFor
import com.allanime.tv.domain.model.AnimeCatalogue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Ce que la barre laterale sait distinguer dans le catalogue.
 *
 * Le site ne classe ses entrees que d'une seule facon qui soit fiable : le type
 * « Film ». Tout ce qui n'en est pas un est traite comme une serie — c'est
 * exactement ce que dit la page du catalogue, et rien n'est deduit au-dela.
 */
enum class FiltreCatalogue(val titre: Int) {
    SERIES(R.string.title_series),
    FILMS(R.string.title_films);

    fun retient(anime: AnimeCatalogue): Boolean =
        if (this == FILMS) anime.estFilm else !anime.estFilm
}

/**
 * Catalogue restreint aux films ou aux series.
 *
 * Aucune requete propre : la liste du catalogue est deja en memoire, partagee avec
 * l'accueil par [CatalogueListeRepository]. Ouvrir « Films » ne coute donc rien de
 * plus que le tri.
 */
class CatalogueFiltreViewModel(
    private val filtre: FiltreCatalogue,
    private val repository: CatalogueListeRepository
) : ViewModel() {

    private val _etat = MutableStateFlow<UiState<List<AnimeCatalogue>>>(
        UiState.Loading(R.string.loading_catalogue)
    )
    val etat: StateFlow<UiState<List<AnimeCatalogue>>> = _etat.asStateFlow()

    init {
        charger()
    }

    fun charger(rafraichir: Boolean = false) {
        viewModelScope.launch {
            _etat.value = UiState.Loading(R.string.loading_catalogue)

            runCatchingCancellable { repository.animes(rafraichir) }
                .onSuccess { animes ->
                    val retenus = animes.filter(filtre::retient).sortedBy { it.titre }
                    _etat.value = if (retenus.isEmpty()) {
                        UiState.Empty(R.string.empty_catalogue)
                    } else {
                        UiState.Success(retenus)
                    }
                }
                .onFailure { _etat.value = UiState.Failure(it.toAppError()) }
        }
    }

    companion object {
        fun factory(filtre: FiltreCatalogue) = viewModelFactoryFor {
            CatalogueFiltreViewModel(filtre, ServiceLocator.catalogueListeRepository)
        }
    }
}
